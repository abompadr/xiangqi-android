// JNI bridge: redirects std::cin/cout to in-process queues and runs
// UCIEngine::loop() on a background thread.

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <condition_variable>
#include <deque>
#include <mutex>
#include <sstream>
#include <streambuf>
#include <string>
#include <thread>

#include "uci.h"
#include "misc.h"

#define LOG_TAG "PikafishJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// ---------------------------------------------------------------------------
// Queue-backed stream buffers
// ---------------------------------------------------------------------------

// Input side: Kotlin writes lines here; UCIEngine::loop() reads via cin.
struct InputBuf : std::streambuf {
    std::deque<char>        buf;
    std::mutex              mtx;
    std::condition_variable cv;
    std::atomic<bool>       closed{false};

    void push(const std::string& line) {
        LOGD(">> %s", line.c_str());
        {
            std::lock_guard<std::mutex> lk(mtx);
            for (char c : line) buf.push_back(c);
            buf.push_back('\n');
        }
        cv.notify_one();
    }

    void close() {
        LOGI("InputBuf closing");
        closed = true;
        cv.notify_all();
    }

protected:
    int underflow() override {
        std::unique_lock<std::mutex> lk(mtx);
        cv.wait(lk, [this]{ return !buf.empty() || closed; });
        if (buf.empty()) return traits_type::eof();
        current = buf.front();
        buf.pop_front();
        setg(&current, &current, &current + 1);
        return traits_type::to_int_type(current);
    }

private:
    char current = 0;
};

// Output side: UCIEngine::loop() writes via cout; Kotlin reads lines here.
struct OutputBuf : std::streambuf {
    std::deque<std::string> lines;
    std::mutex              mtx;
    std::condition_variable cv;
    std::string             partial;   // only touched under mtx
    std::atomic<bool>       closed{false};

    void close() {
        LOGI("OutputBuf closing");
        closed = true;
        cv.notify_all();
    }

protected:
    int overflow(int c) override {
        if (c == traits_type::eof()) return c;
        std::lock_guard<std::mutex> lk(mtx);
        if (c == '\n') {
            LOGD("<< %s", partial.c_str());
            lines.push_back(std::move(partial));
            partial.clear();
            cv.notify_one();
        } else {
            partial += static_cast<char>(c);
        }
        return c;
    }

    std::streamsize xsputn(const char* s, std::streamsize n) override {
        std::lock_guard<std::mutex> lk(mtx);
        for (std::streamsize i = 0; i < n; ++i) {
            char c = s[i];
            if (c == '\n') {
                LOGD("<< %s", partial.c_str());
                lines.push_back(std::move(partial));
                partial.clear();
                cv.notify_one();
            } else {
                partial += c;
            }
        }
        return n;
    }

public:
    // Returns empty string on shutdown; never blocks forever.
    std::string readLine() {
        std::unique_lock<std::mutex> lk(mtx);
        cv.wait(lk, [this]{ return !lines.empty() || closed; });
        if (lines.empty()) return "";   // engine shut down
        std::string line = std::move(lines.front());
        lines.pop_front();
        return line;
    }
};

// ---------------------------------------------------------------------------
// Global engine state
// ---------------------------------------------------------------------------

static InputBuf*       g_inBuf       = nullptr;
static OutputBuf*      g_outBuf      = nullptr;
static std::streambuf* g_origCin     = nullptr;
static std::streambuf* g_origCout    = nullptr;
static std::thread     g_engineThread;
static std::atomic<bool> g_engineDead{false};

// ---------------------------------------------------------------------------
// JNI functions
// ---------------------------------------------------------------------------

extern "C" {

JNIEXPORT void JNICALL
Java_com_xiangqi_app_engine_PikafishEngine_nativeStart(JNIEnv*, jobject) {
    if (g_inBuf) {
        LOGI("nativeStart: already running");
        return;
    }

    LOGI("nativeStart: creating buffers");
    g_inBuf      = new InputBuf();
    g_outBuf     = new OutputBuf();
    g_engineDead = false;

    // Redirect cin/cout BEFORE the engine thread starts so it sees our buffers.
    g_origCin  = std::cin.rdbuf(g_inBuf);
    g_origCout = std::cout.rdbuf(g_outBuf);
    LOGI("nativeStart: cin/cout redirected");

    g_engineThread = std::thread([]() {
        LOGI("Engine thread: starting UCIEngine");
        try {
            char  progName[] = "pikafish";
            char* argv[]     = {progName};
            LOGI("Engine thread: constructing CommandLine");
            Stockfish::CommandLine cli(1, argv);
            LOGI("Engine thread: constructing UCIEngine");
            Stockfish::UCIEngine uci(std::move(cli));
            LOGI("Engine thread: entering loop()");
            uci.loop();
            LOGI("Engine thread: loop() returned normally");
        } catch (const std::exception& e) {
            LOGE("Engine thread: std::exception: %s", e.what());
        } catch (...) {
            LOGE("Engine thread: unknown exception");
        }
        LOGI("Engine thread: marking dead and unblocking readers");
        g_engineDead = true;
        if (g_outBuf) g_outBuf->close();
    });
}

JNIEXPORT void JNICALL
Java_com_xiangqi_app_engine_PikafishEngine_nativeSend(JNIEnv* env, jobject, jstring cmd) {
    if (!g_inBuf) {
        LOGE("nativeSend: engine not started");
        return;
    }
    if (g_engineDead) {
        LOGE("nativeSend: engine is dead, ignoring");
        return;
    }
    const char* s = env->GetStringUTFChars(cmd, nullptr);
    if (s) {
        g_inBuf->push(s);
        env->ReleaseStringUTFChars(cmd, s);
    }
}

JNIEXPORT jstring JNICALL
Java_com_xiangqi_app_engine_PikafishEngine_nativeReadLine(JNIEnv* env, jobject) {
    if (!g_outBuf) {
        LOGE("nativeReadLine: engine not started");
        return env->NewStringUTF("ERROR:engine_not_started");
    }
    std::string line = g_outBuf->readLine();
    if (line.empty() && g_engineDead) {
        LOGE("nativeReadLine: engine died, returning error sentinel");
        return env->NewStringUTF("ERROR:engine_died");
    }
    return env->NewStringUTF(line.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_xiangqi_app_engine_PikafishEngine_nativeIsAlive(JNIEnv*, jobject) {
    return g_inBuf && !g_engineDead ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_xiangqi_app_engine_PikafishEngine_nativeStop(JNIEnv*, jobject) {
    LOGI("nativeStop called");
    if (!g_inBuf) return;

    // Signal engine to quit
    if (!g_engineDead) {
        g_inBuf->push("quit");
    }
    g_inBuf->close();

    // Unblock any pending readLine
    if (g_outBuf) g_outBuf->close();

    if (g_engineThread.joinable()) {
        LOGI("nativeStop: joining engine thread");
        g_engineThread.join();
        LOGI("nativeStop: engine thread joined");
    }

    // Restore cin/cout
    if (g_origCin)  std::cin.rdbuf(g_origCin);
    if (g_origCout) std::cout.rdbuf(g_origCout);
    g_origCin = g_origCout = nullptr;

    delete g_inBuf;  g_inBuf  = nullptr;
    delete g_outBuf; g_outBuf = nullptr;
    g_engineDead = false;
    LOGI("nativeStop: done");
}

} // extern "C"
