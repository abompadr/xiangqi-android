package com.xiangqi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val ACCENT = Color(0xFFE8B84B)
private val DIM    = Color(0xFFAAAAAA)
private val WHITE  = Color.White
private val BG     = Color(0xFF2A2A4A)

@Composable
fun RulesDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BG,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Rules of Xiangqi", color = ACCENT, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RulesSection("Objective") {
                        RulesBody("Capture the opponent's General (將/帥). A player who cannot escape check loses.")
                    }
                    RulesSection("The Board") {
                        RulesBody("10 rows × 9 columns. Pieces sit on intersections, not inside squares.")
                        RulesBody("The River (楚河/漢界) runs between rows 5 and 6. Some pieces cannot cross it.")
                        RulesBody("Each side has a Palace — a 3×3 area in the center of the back three rows. The General and Advisors must stay inside it.")
                    }
                    RulesSection("Pieces") {
                        PieceRule("General  將/帥", "Moves one step at a time, horizontally or vertically. Must stay inside the Palace. The two Generals may never face each other along an open file with no pieces between them.")
                        PieceRule("Advisor  士/仕", "Moves one step diagonally. Must stay inside the Palace.")
                        PieceRule("Elephant  象/相", "Moves exactly two steps diagonally (an \"X\" shape). Cannot cross the River. Blocked if a piece sits on the midpoint of the move.")
                        PieceRule("Horse  馬", "Moves one step orthogonally, then one step diagonally outward (like a chess knight but not identical). Blocked if a piece sits on the first orthogonal step.")
                        PieceRule("Chariot  車", "Slides any number of steps horizontally or vertically (like a chess rook). The most powerful piece.")
                        PieceRule("Cannon  砲", "Moves like a Chariot when not capturing. To capture, it must jump over exactly one piece (of either color) along the same rank or file.")
                        PieceRule("Soldier  兵/卒", "Before crossing the River: moves one step forward only. After crossing: may also move one step sideways. Cannot move backward.")
                    }
                    RulesSection("Check & Checkmate") {
                        RulesBody("If your General is under attack, you are in check and must resolve it immediately.")
                        RulesBody("If you cannot escape check, you lose by checkmate.")
                        RulesBody("Perpetual check (repeating the same check sequence) is forbidden; the attacker must vary their moves.")
                    }
                    RulesSection("Tips for Beginners") {
                        RulesBody("• Protect your General early — keep Advisors and Elephants nearby.")
                        RulesBody("• Develop your Chariots quickly; they are the strongest pieces.")
                        RulesBody("• Cannons are great for early attacks but need pieces to \"mount\" for captures.")
                        RulesBody("• Passed Soldiers (across the River) become much more dangerous.")
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close", color = ACCENT)
                }
            }
        }
    }
}

@Composable
private fun RulesSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = ACCENT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun PieceRule(piece: String, description: String) {
    Column(modifier = Modifier.padding(start = 8.dp)) {
        Text(piece, color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(description, color = DIM, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun RulesBody(text: String) {
    Text(text, color = DIM, fontSize = 12.sp, lineHeight = 17.sp,
        modifier = Modifier.padding(start = 8.dp))
}
