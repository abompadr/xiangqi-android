package com.xiangqi.app.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

class PikafishEngine(private val context: Context) {

    private var process: Process? = null
    private var output: java.io.BufferedReader? = null
    private var input: java.io.PrintWriter? = null

    suspend fun start() = withContext(Dispatchers.IO) {
        val binary = extractBinary()
        // Copy NNUE net from assets to filesDir so the engine can read it
        val nnueFile = extractNnue()
        process = ProcessBuilder(binary.absolutePath)
            .redirectErrorStream(true)
            .start()
        output = process!!.inputStream.bufferedReader()
        input  = java.io.PrintWriter(process!!.outputStream, true)
        send("uci")
        waitFor("uciok")
        // Point engine to the NNUE net file
        if (nnueFile.exists()) {
            send("setoption name EvalFile value ${nnueFile.absolutePath}")
        }
        send("isready")
        waitFor("readyok")
    }

    fun setSkillLevel(level: Int) {
        send("setoption name Skill Level value $level")
    }

    fun setPosition(fen: String, moves: List<String> = emptyList()) {
        val movePart = if (moves.isEmpty()) "" else " moves ${moves.joinToString(" ")}"
        send("position fen $fen$movePart")
    }

    suspend fun getBestMove(timeLimitMs: Int = 2000): String = withContext(Dispatchers.IO) {
        send("go movetime $timeLimitMs")
        var best = ""
        while (true) {
            val line = output?.readLine() ?: break
            if (line.startsWith("bestmove")) {
                val parts = line.split(" ")
                best = if (parts.size >= 2) parts[1] else ""
                break
            }
        }
        best
    }

    fun stop() {
        try { send("quit") } catch (_: Exception) {}
        process?.destroy()
        process = null
    }

    private fun send(cmd: String) {
        input?.println(cmd)
    }

    private fun waitFor(token: String) {
        while (true) {
            val line = output?.readLine() ?: break
            if (line.contains(token)) break
        }
    }

    private fun extractBinary(): File {
        val dest = File(context.codeCacheDir, "pikafish")
        if (!dest.exists() || dest.length() == 0L) {
            dest.delete()
            val src = File(context.applicationInfo.nativeLibraryDir, "libpikafish.so")
            if (src.exists()) {
                src.copyTo(dest, overwrite = true)
            } else {
                context.assets.open("pikafish").use { input: InputStream ->
                    dest.outputStream().use { input.copyTo(it) }
                }
            }
            dest.setExecutable(true)
        }
        if (!dest.canExecute()) dest.setExecutable(true)
        return dest
    }

    private fun extractNnue(): File {
        val dest = File(context.filesDir, "pikafish.nnue")
        if (!dest.exists() || dest.length() == 0L) {
            try {
                context.assets.open("pikafish.nnue").use { input: InputStream ->
                    dest.outputStream().use { input.copyTo(it) }
                }
            } catch (_: Exception) { /* NNUE not bundled — engine will use built-in or no eval */ }
        }
        return dest
    }
}
