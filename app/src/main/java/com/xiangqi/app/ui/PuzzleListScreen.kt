package com.xiangqi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiangqi.app.engine.Puzzle
import com.xiangqi.app.engine.PuzzleBook

private val BG     = Color(0xFF1A1A2E)
private val CARD   = Color(0xFF252540)
private val GOLD   = Color(0xFFE8B84B)

private fun categoryColor(cat: Int) = when (cat) {
    1    -> Color(0xFF4CAF50)
    2    -> Color(0xFF4A90D9)
    3    -> Color(0xFFE8A020)
    else -> Color(0xFFCC2222)
}

@Composable
fun PuzzleListScreen(
    onPuzzle: (Puzzle) -> Unit,
    onBack: () -> Unit,
) {
    val puzzles = PuzzleBook.puzzles

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = Color(0xFF8888AA)) }
            Spacer(Modifier.weight(1f))
            Text("Puzzles  棋题", color = GOLD, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(puzzles) { puzzle ->
                PuzzleCard(puzzle = puzzle, onClick = { onPuzzle(puzzle) })
            }
        }
    }
}

@Composable
private fun PuzzleCard(puzzle: Puzzle, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CARD, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(puzzle.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(2.dp))
            Text(puzzle.hint, color = Color(0xFF8888AA), fontSize = 12.sp, maxLines = 2)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .background(categoryColor(puzzle.category), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = PuzzleBook.categoryLabel(puzzle.category),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
