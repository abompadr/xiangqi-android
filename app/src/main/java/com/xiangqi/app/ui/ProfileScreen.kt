package com.xiangqi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiangqi.app.data.GamePrefs
import com.xiangqi.app.data.Profile

private val BG     = Color(0xFF1A1A2E)
private val ACCENT = Color(0xFFE8B84B)

@Composable
fun GameSetupScreen(
    onPlay: (Profile) -> Unit,
    onPuzzles: () -> Unit = {},
    onGames: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val saved = remember { GamePrefs.load(ctx) }

    var skill         by remember { mutableIntStateOf(saved.skillLevel) }
    var computerTime  by remember { mutableIntStateOf(saved.computerTimeMinutes) }
    var playerTime    by remember { mutableIntStateOf(saved.playerTimeMinutes) }
    var playAsRed     by remember { mutableStateOf(saved.playAsRed) }
    var westernPieces by remember { mutableStateOf(saved.useWesternPieces) }
    var showRules     by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "象棋  Xiangqi",
                color = ACCENT,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { showRules = true }) {
                Text("? Rules", color = Color(0xFF8888AA))
            }
        }

        Spacer(Modifier.height(16.dp))

        SetupSection(label = "Difficulty") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ChipRow(
                    options = listOf("Beginner" to 0, "Easy" to 5, "Medium" to 10),
                    selected = skill,
                    onSelect = { skill = it }
                )
                ChipRow(
                    options = listOf("Hard" to 15, "Expert" to 20),
                    selected = skill,
                    onSelect = { skill = it }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        SetupSection(label = "Computer time") {
            ChipRow(
                options = listOf("1 min" to 1, "5 min" to 5, "10 min" to 10, "30 min" to 30, "∞" to 0),
                selected = computerTime,
                onSelect = { computerTime = it }
            )
        }

        Spacer(Modifier.height(16.dp))

        SetupSection(label = "Your time") {
            ChipRow(
                options = listOf("1 min" to 1, "5 min" to 5, "10 min" to 10, "30 min" to 30, "∞" to 0),
                selected = playerTime,
                onSelect = { playerTime = it }
            )
        }

        Spacer(Modifier.height(16.dp))

        SetupSection(label = "Play as") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = playAsRed,
                    onClick = { playAsRed = true },
                    label = { Text("Red (first)") }
                )
                FilterChip(
                    selected = !playAsRed,
                    onClick = { playAsRed = false },
                    label = { Text("Black (second)") }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        SetupSection(label = "Piece style") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !westernPieces,
                    onClick = { westernPieces = false },
                    label = { Text("Chinese 兵馬") }
                )
                FilterChip(
                    selected = westernPieces,
                    onClick = { westernPieces = true },
                    label = { Text("Western PNBRCK") }
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                val profile = Profile(
                    skillLevel          = skill,
                    computerTimeMinutes = computerTime,
                    playerTimeMinutes   = playerTime,
                    playAsRed           = playAsRed,
                    useWesternPieces    = westernPieces
                )
                GamePrefs.save(ctx, profile)
                onPlay(profile)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ACCENT)
        ) {
            Text("Play!", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onPuzzles,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ACCENT)
        ) {
            Text("Puzzles  棋题", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onGames,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF9C27B0))
        ) {
            Text("Full Games", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showRules) {
        RulesDialog(onDismiss = { showRules = false })
    }
}

@Composable
private fun SetupSection(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Color(0xFFAAAAAA), fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

@Composable
private fun ChipRow(options: List<Pair<String, Int>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (label, value) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label, fontSize = 12.sp) }
            )
        }
    }
}
