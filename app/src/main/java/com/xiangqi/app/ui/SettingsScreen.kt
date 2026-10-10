package com.xiangqi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

private val BG = Color(0xFF1A1A2E)

@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val savedKey by vm.geminiApiKey.collectAsState()
    var draft by remember(savedKey) { mutableStateOf(savedKey) }
    var showKey by remember { mutableStateOf(false) }
    val isDirty = draft.trim() != savedKey

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back", color = Color(0xFF8888AA)) }
            Spacer(Modifier.width(8.dp))
            Text("Settings", color = Color.White, fontSize = 20.sp)
        }

        Spacer(Modifier.height(24.dp))

        Text("Gemini API Key", color = Color(0xFFDDDDDD), fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Used to explain the computer's moves. Get a free key at aistudio.google.com.",
            color = Color(0xFF8888AA),
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API Key") },
            placeholder = { Text("AIza...") },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showKey = !showKey }) {
                    Text(if (showKey) "Hide" else "Show", color = Color(0xFF8888AA), fontSize = 12.sp)
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (isDirty) vm.saveApiKey(draft)
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFE8B84B),
                unfocusedBorderColor = Color(0xFF444466),
                focusedLabelColor = Color(0xFFE8B84B),
                unfocusedLabelColor = Color(0xFF8888AA),
                cursorColor = Color(0xFFE8B84B),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
            )
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { vm.saveApiKey(draft) },
            enabled = isDirty,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8B84B))
        ) {
            Text("Save", color = Color.Black)
        }
    }
}
