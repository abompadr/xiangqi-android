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

    fun hasAnyLegalMove(): Boolean {
        for (r in 0..9) for (f in 0..8) {
            val p = board[r][f]
            if (p == Piece.EMPTY) continue
            if ((p > 0) != redToMove) continue
            if (legalMovesFrom(XqSquare(f, r)).isNotEmpty()) return true
        }
        return false
    }

    fun deepCopy(): XiangqiBoard {
        val copy = XiangqiBoard()
        for (r in 0..9) for (f in 0..8) copy.board[r][f] = board[r][f]
        copy.redToMove = redToMove
        copy.moveHistory.addAll(moveHistory)
        return copy
    }

    // Returns all pseudo-legal destination squares for the piece at [from],
    // filtered to exclude moves that leave own General in check.
    fun legalMovesFrom(from: XqSquare): List<XqSquare> {
        val piece = get(from)
        if (piece == Piece.EMPTY) return emptyList()
        val red = piece > 0
        if (red != redToMove) return emptyList()
        return pseudoMovesFrom(from).filter { to ->
            val copy = deepCopy()
            copy.board[to.rank][to.file] = copy.board[from.rank][from.file]
            copy.board[from.rank][from.file] = Piece.EMPTY
            !copy.isInCheck(red)
        }
    }

    private fun isInCheck(red: Boolean): Boolean {
        // Find the General
        var gFile = -1; var gRank = -1
        outer@ for (r in 0..9) for (f in 0..8) {
            val p = board[r][f]
            if (red && p == Piece.GENERAL) { gRank = r; gFile = f; break@outer }
            if (!red && p == -Piece.GENERAL) { gRank = r; gFile = f; break@outer }
        }
        if (gRank < 0) return true  // General captured = in check
        val gSq = XqSquare(gFile, gRank)
        // Check if any enemy piece attacks the General
        for (r in 0..9) for (f in 0..8) {
            val p = board[r][f]
            if (p == Piece.EMPTY) continue
            val enemyRed = p > 0
            if (enemyRed == red) continue
            if (pseudoMovesFrom(XqSquare(f, r)).contains(gSq)) return true
        }
        return false
    }

    private fun pseudoMovesFrom(from: XqSquare): List<XqSquare> {
        val piece = get(from)
        val abs = Math.abs(piece)
        val red = piece > 0
        val moves = mutableListOf<XqSquare>()

        fun inBounds(f: Int, r: Int) = f in 0..8 && r in 0..9
        fun add(f: Int, r: Int) { if (inBounds(f, r)) { val p = board[r][f]; if (p == 0 || (p > 0) != red) moves.add(XqSquare(f, r)) } }
        fun occupied(f: Int, r: Int) = inBounds(f, r) && board[r][f] != Piece.EMPTY

        when (abs) {
            Piece.CHARIOT -> {
                for (df in intArrayOf(-1, 1)) { var f = from.file + df; while (inBounds(f, from.rank)) { add(f, from.rank); if (occupied(f, from.rank)) break; f += df } }
                for (dr in intArrayOf(-1, 1)) { var r = from.rank + dr; while (inBounds(from.file, r)) { add(from.file, r); if (occupied(from.file, r)) break; r += dr } }
            }
            Piece.HORSE -> {
                val legs = arrayOf(intArrayOf(0,1,1,2),intArrayOf(0,1,-1,2),intArrayOf(0,-1,1,-2),intArrayOf(0,-1,-1,-2),
                                   intArrayOf(1,0,2,1),intArrayOf(1,0,2,-1),intArrayOf(-1,0,-2,1),intArrayOf(-1,0,-2,-1))
                for (l in legs) { val bf = from.file+l[0]; val br = from.rank+l[1]; if (inBounds(bf,br) && !occupied(bf,br)) add(from.file+l[2], from.rank+l[3]) }
            }
            Piece.ELEPHANT -> {
                val steps = arrayOf(intArrayOf(1,1),intArrayOf(1,-1),intArrayOf(-1,1),intArrayOf(-1,-1))
                val riverMin = if (red) 0 else 5; val riverMax = if (red) 4 else 9
                for (s in steps) { val mf = from.file+s[0]; val mr = from.rank+s[1]; if (inBounds(mf,mr) && !occupied(mf,mr)) { val tf = from.file+s[0]*2; val tr = from.rank+s[1]*2; if (tr in riverMin..riverMax) add(tf,tr) } }
            }
            Piece.ADVISOR -> {
                val steps = arrayOf(intArrayOf(1,1),intArrayOf(1,-1),intArrayOf(-1,1),intArrayOf(-1,-1))
                val rMin = if (red) 0 else 7; val rMax = if (red) 2 else 9
                for (s in steps) { val tf = from.file+s[0]; val tr = from.rank+s[1]; if (tf in 3..5 && tr in rMin..rMax) add(tf,tr) }
            }
            Piece.GENERAL -> {
                val rMin = if (red) 0 else 7; val rMax = if (red) 2 else 9
                for (s in arrayOf(intArrayOf(1,0),intArrayOf(-1,0),intArrayOf(0,1),intArrayOf(0,-1))) { val tf = from.file+s[0]; val tr = from.rank+s[1]; if (tf in 3..5 && tr in rMin..rMax) add(tf,tr) }
                // Flying General rule: General faces enemy General with no pieces between
                val enemyGeneral = if (red) -Piece.GENERAL else Piece.GENERAL
                val dir = if (red) 1 else -1; var r = from.rank + dir; var blocked = false
                while (inBounds(from.file, r)) { val p = board[r][from.file]; if (p != Piece.EMPTY) { if (p == enemyGeneral && !blocked) moves.add(XqSquare(from.file, r)); break }; r += dir }
            }
            Piece.CANNON -> {
                for (df in intArrayOf(-1,1)) { var f = from.file+df; while (inBounds(f,from.rank) && !occupied(f,from.rank)) { moves.add(XqSquare(f,from.rank)); f+=df }; f+=df; while (inBounds(f,from.rank)) { val p = board[from.rank][f]; if (p!=Piece.EMPTY) { if ((p>0)!=red) moves.add(XqSquare(f,from.rank)); break }; f+=df } }
                for (dr in intArrayOf(-1,1)) { var r = from.rank+dr; while (inBounds(from.file,r) && !occupied(from.file,r)) { moves.add(XqSquare(from.file,r)); r+=dr }; r+=dr; while (inBounds(from.file,r)) { val p = board[r][from.file]; if (p!=Piece.EMPTY) { if ((p>0)!=red) moves.add(XqSquare(from.file,r)); break }; r+=dr } }
            }
            Piece.SOLDIER -> {
                val fwd = if (red) 1 else -1
                add(from.file, from.rank + fwd)
                val crossedRiver = if (red) from.rank >= 5 else from.rank <= 4
                if (crossedRiver) { add(from.file-1, from.rank); add(from.file+1, from.rank) }
            }
        }
        return moves
    }

    companion object {
        const val START_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
    }
}
