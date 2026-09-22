package com.xiangqi.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiangqi.app.data.AppDatabase
import com.xiangqi.app.data.Profile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.getInstance(app).profileDao()

    val profiles = dao.getAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun save(profile: Profile) = viewModelScope.launch {
        if (profile.id == 0) dao.insert(profile) else dao.update(profile)
    }

    fun delete(profile: Profile) = viewModelScope.launch {
        dao.delete(profile)
    }
}
