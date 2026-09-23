package com.xiangqi.app.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PikafishEngine(@Suppress("UNUSED_PARAMETER") context: Context) {

    companion object {
        init {
            System.loadLibrary("pikafish")
        }
    }

    private external fun nativeStart()
    private external fun nativeSend(cmd: String)
    private external fun nativeReadLine(): String
    private external fun nativeStop()
    private external fun nativeIsAlive(): Boolean

    suspend fun start() = withContext(Dispatchers.IO) {
        nativeStart()
        send("uci")
        waitFor("uciok")
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
            val line = nativeReadLine()
            if (line.startsWith("ERROR:")) throw RuntimeException(line)
            if (line.startsWith("bestmove")) {
                val parts = line.split(" ")
                best = if (parts.size >= 2) parts[1] else ""
                break
            }
        }
        best
    }

    fun stop() {
        try { nativeStop() } catch (_: Exception) {}
    }

    private fun send(cmd: String) {
        if (!nativeIsAlive()) throw RuntimeException("engine_not_alive")
        nativeSend(cmd)
    }

    private fun waitFor(token: String) {
        while (true) {
            val line = nativeReadLine()
            if (line.startsWith("ERROR:")) throw RuntimeException(line)
            if (line.contains(token)) break
        }
    }
}
