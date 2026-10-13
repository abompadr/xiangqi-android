package com.xiangqi.app.engine

import android.content.Context

object OpeningBook {

    // Each entry: moves (list of UCI strings) → name
    private data class Line(val moves: List<String>, val name: String)

    private var lines: List<Line> = emptyList()

    fun load(ctx: Context) {
        if (lines.isNotEmpty()) return
        lines = ctx.assets.open("openings.txt").bufferedReader().lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { raw ->
                val parts = raw.split("|")
                if (parts.size < 2) return@mapNotNull null
                val moves = parts[0].trim().split(" ").filter { it.isNotBlank() }
                val name  = parts[1].trim()
                if (moves.isEmpty() || name.isEmpty()) null else Line(moves, name)
            }
            // Longest lines first so we always match the most specific name
            .sortedByDescending { it.moves.size }
            .toList()
    }

    /** Returns the name of the most specific opening that is a prefix of [history], or null. */
    fun lookup(history: List<String>): String? {
        if (history.isEmpty()) return null
        // Find all lines whose move list is a prefix of history (or equal to it)
        return lines.firstOrNull { line ->
            history.size >= line.moves.size &&
                history.subList(0, line.moves.size) == line.moves
        }?.name
    }
}
