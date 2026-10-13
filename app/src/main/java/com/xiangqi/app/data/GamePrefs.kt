package com.xiangqi.app.data

import android.content.Context

object GamePrefs {
    private const val PREFS = "game_settings"
    private const val KEY_SKILL = "skill"
    private const val KEY_COMPUTER_TIME = "computer_time"
    private const val KEY_PLAYER_TIME = "player_time"
    private const val KEY_PLAY_AS_RED = "play_as_red"

    fun load(ctx: Context): Profile {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Profile(
            skillLevel          = sp.getInt(KEY_SKILL, 10),
            computerTimeMinutes = sp.getInt(KEY_COMPUTER_TIME, 5),
            playerTimeMinutes   = sp.getInt(KEY_PLAYER_TIME, 0),
            playAsRed           = sp.getBoolean(KEY_PLAY_AS_RED, true)
        )
    }

    fun save(ctx: Context, p: Profile) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SKILL, p.skillLevel)
            .putInt(KEY_COMPUTER_TIME, p.computerTimeMinutes)
            .putInt(KEY_PLAYER_TIME, p.playerTimeMinutes)
            .putBoolean(KEY_PLAY_AS_RED, p.playAsRed)
            .apply()
    }
}
