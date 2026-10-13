package com.xiangqi.app.data

data class Profile(
    val id: Int = 0,
    val name: String = "",
    val skillLevel: Int = 10,
    val computerTimeMinutes: Int = 5,   // 0 = unlimited
    val playerTimeMinutes: Int = 0,     // 0 = unlimited
    val playAsRed: Boolean = true
)
