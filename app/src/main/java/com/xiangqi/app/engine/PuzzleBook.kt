package com.xiangqi.app.engine

import android.content.Context
import org.json.JSONArray

data class Puzzle(
    val id: String,
    val title: String,
    val category: Int,   // 1=one-move, 2=two-move, 3=three-move, 4=multi-move
    val hint: String,
    val fen: String,
    val solution: List<String>,   // UCI moves e.g. ["d5e5"]
)

object PuzzleBook {
    private val _puzzles = mutableListOf<Puzzle>()
    val puzzles: List<Puzzle> get() = _puzzles

    fun load(ctx: Context) {
        if (_puzzles.isNotEmpty()) return
        val json = ctx.assets.open("puzzles.json").bufferedReader().readText()
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val solArr = o.getJSONArray("solution")
            val sol = (0 until solArr.length()).map { solArr.getString(it) }
            _puzzles.add(
                Puzzle(
                    id       = o.getString("id"),
                    title    = o.getString("title"),
                    category = o.getInt("category"),
                    hint     = o.getString("hint"),
                    fen      = o.getString("fen"),
                    solution = sol,
                )
            )
        }
    }

    fun categoryLabel(category: Int) = when (category) {
        1    -> "Mate in 1"
        2    -> "Mate in 2"
        3    -> "Mate in 3"
        else -> "Multi-move"
    }
}
