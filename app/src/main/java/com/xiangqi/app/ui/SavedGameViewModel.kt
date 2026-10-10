package com.xiangqi.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiangqi.app.data.AppDatabase
import com.xiangqi.app.data.SavedGame
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SavedGameViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.getInstance(app).savedGameDao()

    val savedGames = dao.getAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun delete(game: SavedGame) = viewModelScope.launch { dao.delete(game) }
}
