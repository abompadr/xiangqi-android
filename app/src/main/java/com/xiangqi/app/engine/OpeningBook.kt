package com.xiangqi.app.engine

import android.content.Context

object OpeningBook {

    // Maps FEN (position after the line's moves) → opening name.
    // Multiple lines can map to the same FEN (transpositions share the same entry).
    // When two lines reach the same FEN, the longer one's name wins (more specific).
    private var fenToName: Map<String, String> = emptyMap()

    fun load(ctx: Context) {
        if (fenToName.isNotEmpty()) return
        val map = mutableMapOf<String, Pair<Int, String>>() // fen → (depth, name)
        ctx.assets.open("openings.txt").bufferedReader().lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { raw ->
                val parts = raw.split("|")
                if (parts.size < 2) return@forEach
                val moves = parts[0].trim().split(" ").filter { it.isNotBlank() }
                val name  = parts[1].trim()
                if (moves.isEmpty() || name.isEmpty()) return@forEach
                // Replay the line on a scratch board to get the resulting FEN
                val board = XiangqiBoard()
                for (uci in moves) board.applyUci(uci)
                val fen = board.toFen()
                // Keep the deeper (more specific) name if two lines reach the same position
                val existing = map[fen]
                if (existing == null || moves.size > existing.first) {
                    map[fen] = moves.size to name
                }
            }
        fenToName = map.mapValues { it.value.second }
    }

    /** Returns the opening name for the current [board] position, or null if not in book. */
    fun lookup(board: XiangqiBoard): String? {
        if (board.moveHistory.isEmpty()) return null
        return fenToName[board.toFen()]
    }
}
