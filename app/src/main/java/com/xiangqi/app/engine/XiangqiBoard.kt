package com.xiangqi.app.engine

// Piece constants: positive = Red, negative = Black, 0 = empty
object Piece {
    const val EMPTY    = 0
    const val SOLDIER  = 1  // Bing/Zu (pawn)
    const val HORSE    = 2  // Ma (knight)
    const val ELEPHANT = 3  // Xiang/Xiang (bishop)
    const val ADVISOR  = 4  // Shi (guard)
    const val CHARIOT  = 5  // Ju (rook)
    const val CANNON   = 6  // Pao
    const val GENERAL  = 7  // Jiang/Shuai (king)
}

// file 0=a..8=i, rank 0=red's back rank..9=black's back rank
data class XqSquare(val file: Int, val rank: Int) {
    override fun toString() = "${"abcdefghi"[file]}$rank"
    companion object {
        fun fromAlg(s: String) = XqSquare("abcdefghi".indexOf(s[0]), s[1].digitToInt())
    }
}

class XiangqiBoard {
    // board[rank][file], positive = Red, negative = Black
    private val board = Array(10) { IntArray(9) }
    var redToMove = true
    val moveHistory = mutableListOf<String>()

    init { reset() }

    fun reset() {
        for (r in 0..9) board[r].fill(Piece.EMPTY)
        // Red pieces (rank 0-2)
        board[0][0] =  Piece.CHARIOT;  board[0][8] =  Piece.CHARIOT
        board[0][1] =  Piece.HORSE;    board[0][7] =  Piece.HORSE
        board[0][2] =  Piece.ELEPHANT; board[0][6] =  Piece.ELEPHANT
        board[0][3] =  Piece.ADVISOR;  board[0][5] =  Piece.ADVISOR
        board[0][4] =  Piece.GENERAL
        board[2][1] =  Piece.CANNON;   board[2][7] =  Piece.CANNON
        for (f in intArrayOf(0,2,4,6,8)) board[3][f] = Piece.SOLDIER
        // Black pieces (rank 7-9)
        board[9][0] = -Piece.CHARIOT;  board[9][8] = -Piece.CHARIOT
        board[9][1] = -Piece.HORSE;    board[9][7] = -Piece.HORSE
        board[9][2] = -Piece.ELEPHANT; board[9][6] = -Piece.ELEPHANT
        board[9][3] = -Piece.ADVISOR;  board[9][5] = -Piece.ADVISOR
        board[9][4] = -Piece.GENERAL
        board[7][1] = -Piece.CANNON;   board[7][7] = -Piece.CANNON
        for (f in intArrayOf(0,2,4,6,8)) board[6][f] = -Piece.SOLDIER
        redToMove = true
        moveHistory.clear()
    }

    fun get(rank: Int, file: Int): Int = board[rank][file]
    fun get(sq: XqSquare): Int = board[sq.rank][sq.file]
    fun setInternal(rank: Int, file: Int, value: Int) { board[rank][file] = value }

    // Apply a UCI move string like "e0e1", "h9g7"
    fun applyUci(uci: String) {
        val from = XqSquare.fromAlg(uci.substring(0, 2))
        val to   = XqSquare.fromAlg(uci.substring(2, 4))
        board[to.rank][to.file] = board[from.rank][from.file]
        board[from.rank][from.file] = Piece.EMPTY
        redToMove = !redToMove
        moveHistory.add(uci)
    }

    fun toFen(): String {
        val sb = StringBuilder()
        for (r in 9 downTo 0) {
            var empty = 0
            for (f in 0..8) {
                val p = board[r][f]
                if (p == Piece.EMPTY) { empty++ } else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    val ch = "pnbarkc"[Math.abs(p) - 1]   // soldier,horse,elephant,advisor,chariot,general,cannon
                    // map: 1=p,2=n,3=b,4=a,5=r,6=?,7=k  -> use standard xiangqi FEN letters
                    sb.append(if (p > 0) fenChar(p).uppercaseChar() else fenChar(-p))
                }
            }
            if (empty > 0) sb.append(empty)
            if (r > 0) sb.append('/')
        }
        sb.append(if (redToMove) " w" else " b")
        sb.append(" - - 0 1")
        return sb.toString()
    }

    private fun fenChar(absPiece: Int) = when (absPiece) {
        Piece.SOLDIER  -> 'p'
        Piece.HORSE    -> 'n'
        Piece.ELEPHANT -> 'b'
        Piece.ADVISOR  -> 'a'
        Piece.CHARIOT  -> 'r'
        Piece.CANNON   -> 'c'
        Piece.GENERAL  -> 'k'
        else -> '?'
    }

    fun deepCopy(): XiangqiBoard {
        val copy = XiangqiBoard()
        for (r in 0..9) for (f in 0..8) copy.board[r][f] = board[r][f]
        copy.redToMove = redToMove
        copy.moveHistory.addAll(moveHistory)
        return copy
    }

    companion object {
        const val START_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
    }
}
