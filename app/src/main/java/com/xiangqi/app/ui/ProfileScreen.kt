package com.xiangqi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiangqi.app.data.Profile

private val BG      = Color(0xFF1A1A2E)
private val CARD_BG = Color(0xFF2A2A4A)
private val ACCENT  = Color(0xFFE8B84B)   // gold, fitting for xiangqi

@Composable
fun ProfileScreen(onPlay: (Profile) -> Unit, vm: ProfileViewModel = viewModel()) {
    val profiles by vm.profiles.collectAsState()
    var editTarget by remember { mutableStateOf<Profile?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Text(
            "象棋  Xiangqi",
            color = ACCENT,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(24.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(profiles, key = { it.id }) { p ->
                ProfileCard(
                    profile = p,
                    onPlay = { onPlay(p) },
                    onEdit = { editTarget = p; showDialog = true },
                    onDelete = { vm.delete(p) }
                )
            }
        }

        Button(
            onClick = { editTarget = null; showDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ACCENT)
        ) {
            Text("New Profile", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }

    if (showDialog) {
        ProfileDialog(
            initial = editTarget,
            onConfirm = { vm.save(it); showDialog = false },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CARD_BG)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(profile.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            val diffLabel = difficultyLabel(profile.skillLevel)
            val timeLabel = if (profile.timeControlMinutes == 0) "Unlimited" else "${profile.timeControlMinutes} min"
            Text("$diffLabel  ·  $timeLabel",
                color = Color(0xFFAAAAAA), fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = ACCENT),
                    modifier = Modifier.weight(1f)
                ) { Text("Play", color = Color.Black, fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Text("Edit", color = Color.White)
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE74C3C))
                ) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun ProfileDialog(
    initial: Profile?,
    onConfirm: (Profile) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var skill by remember { mutableIntStateOf(initial?.skillLevel ?: 10) }
    var time by remember { mutableIntStateOf(initial?.timeControlMinutes ?: 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Profile" else "Edit Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Difficulty", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Beginner" to 0, "Easy" to 5, "Medium" to 10, "Hard" to 15, "Expert" to 20)
                        .forEach { (label, value) ->
                            FilterChip(
                                selected = skill == value,
                                onClick = { skill = value },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                }
                Text("Time Control", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("1 min" to 1, "5 min" to 5, "10 min" to 10, "30 min" to 30, "∞" to 0)
                        .forEach { (label, value) ->
                            FilterChip(
                                selected = time == value,
                                onClick = { time = value },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(Profile(id = initial?.id ?: 0, name = name.trim(),
                            skillLevel = skill, timeControlMinutes = time))
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun difficultyLabel(skill: Int) = when {
    skill <= 3  -> "Beginner"
    skill <= 7  -> "Easy"
    skill <= 12 -> "Medium"
    skill <= 17 -> "Hard"
    else        -> "Expert"
}
