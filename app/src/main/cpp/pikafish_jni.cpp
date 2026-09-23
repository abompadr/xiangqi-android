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
        {
            std::lock_guard<std::mutex> lk(mtx);
            for (char c : line) buf.push_back(c);
            buf.push_back('\n');
        }
        cv.notify_one();
    }

    void close() {
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
    std::string             partial;

protected:
    int overflow(int c) override {
        if (c == traits_type::eof()) return c;
        if (c == '\n') {
            std::lock_guard<std::mutex> lk(mtx);
            lines.push_back(std::move(partial));
            partial.clear();
            cv.notify_one();
        } else {
            partial += static_cast<char>(c);
        }
        return c;
    }

    std::streamsize xsputn(const char* s, std::streamsize n) override {
        for (std::streamsize i = 0; i < n; ++i) overflow(s[i]);
        return n;
    }

public:
    std::string readLine() {
        std::unique_lock<std::mutex> lk(mtx);
        cv.wait(lk, [this]{ return !lines.empty(); });
        std::string line = std::move(lines.front());
        lines.pop_front();
        return line;
    }
};

// ---------------------------------------------------------------------------
// Global engine state
// ---------------------------------------------------------------------------

static InputBuf*  g_inBuf  = nullptr;
static OutputBuf* g_outBuf = nullptr;
static std::streambuf* g_origCin  = nullptr;
static std::streambuf* g_origCout = nullptr;
static std::thread g_engineThread;

// ---------------------------------------------------------------------------
// JNI functions
// ---------------------------------------------------------------------------

extern "C" {

JNIEXPORT void JNICALL
Java_com_xiangqi_app_engine_PikafishEngine_nativeStart(JNIEnv*, jobject) {
    if (g_inBuf) return; // already running

    g_inBuf  = new InputBuf();
    g_outBuf = new OutputBuf();

    // Redirect cin/cout
    g_origCin  = std::cin.rdbuf(g_inBuf);
    g_origCout = std::cout.rdbuf(g_outBuf);

    g_engineThread = std::thread([]() {
        LOGI("Engine thread starting");
        try {
            // Construct a minimal CommandLine with no arguments
            char  progName[] = "pikafish";
            char* argv[]     = {progName};
            Stockfish::CommandLine cli(1, argv);
            Stockfish::UCIEngine uci(std::move(cli));
            uci.loop();
        } catch (const std::exception& e) {
            LOGE("Engine exception: %s", e.what());
        } catch (...) {
            LOGE("Engine unknown exception");
        }
        LOGI("Engine thread exiting");
    });
}

JNIEXPORT void JNICALL
Java_com_xiangqi_app_engine_PikafishEngine_nativeSend(JNIEnv* env, jobject, jstring cmd) {
    if (!g_inBuf) return;
    const char* s = env->GetStringUTFChars(cmd, nullptr);
    g_inBuf->push(s);
    env->ReleaseStringUTFChars(cmd, s);
}

JNIEXPORT jstring JNICALL
Java_com_xiangqi_app_engine_PikafishEngine_nativeReadLine(JNIEnv* env, jobject) {
    if (!g_outBuf) return env->NewStringUTF("");
    std::string line = g_outBuf->readLine();
    return env->NewStringUTF(line.c_str());
}

JNIEXPORT void JNICALL
Java_com_xiangqi_app_engine_PikafishEngine_nativeStop(JNIEnv*, jobject) {
    if (!g_inBuf) return;
    g_inBuf->push("quit");
    g_inBuf->close();
    if (g_engineThread.joinable()) g_engineThread.join();

    // Restore cin/cout
    if (g_origCin)  std::cin.rdbuf(g_origCin);
    if (g_origCout) std::cout.rdbuf(g_origCout);

    delete g_inBuf;  g_inBuf  = nullptr;
    delete g_outBuf; g_outBuf = nullptr;
    g_origCin = g_origCout = nullptr;
}

} // extern "C"
