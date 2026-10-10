package com.xiangqi.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_games")
data class SavedGame(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,           // e.g. "Alice · 2026-10-10 14:32"
    val profileName: String,
    val skillLevel: Int,
    val timeControlMinutes: Int,
    val playerIsRed: Boolean,
    val moveHistory: String,     // space-separated UCI moves, e.g. "h2e2 h9g7 ..."
    val isCompleted: Boolean,
    val savedAt: Long            // System.currentTimeMillis()
)
