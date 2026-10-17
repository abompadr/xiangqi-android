#!/usr/bin/env python3
"""
Convert Chinese-Chess-Practical-Dataset PGN files to the app's puzzles.json format.

Dataset: https://github.com/Yvonne761/Chinese-Chess-Practical-Dataset
Encoding: UTF-8 (served as UTF-8 via GitHub raw API)
Move notation: WXF (e.g. 炮二平五, 馬８進７, 前車退三)

Output format (matches existing puzzles.json):
  { "id": "...", "title": "...", "category": 1-4, "hint": "...", "fen": "...", "solution": ["e2e5", ...] }

Usage:
  python convert_puzzles.py --input /path/to/dataset_root --output puzzles_imported.json [--max N]

The --input directory should contain the subfolders:
  Dataset/殺局_殺法_練習題/  (full professional games — long sequences, category 4)
  Dataset/中局/             (midgame positions — category 4)
  Dataset/殘局/             (endgame positions — category 4)

All three folder types contain complete move sequences (not pre-set puzzles),
so all entries get category 4. The app can later filter by folder source.

Note on the 殺局 folder: files are full games from the starting position. The
FEN header is always the standard Xiangqi starting position. These are best
treated as "game replay" content rather than tactical puzzles.
"""

import argparse
import json
import re
import sys
import io
from pathlib import Path

# Force UTF-8 output on Windows consoles
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

# ---------------------------------------------------------------------------
# WXF → UCI conversion
# ---------------------------------------------------------------------------
#
# Xiangqi board layout used in this code:
#   rank 0 = Red's back rank (bottom of screen, generals at e0)
#   rank 9 = Black's back rank (top of screen)
#   file 0 = leftmost column (a), file 8 = rightmost column (i)
#
# WXF column numbering (1–9):
#   Red  side: col 1 = rightmost from Red's perspective  → file = 9 - col
#   Black side: col 1 = rightmost from Black's perspective → file = col - 1
#
# FEN piece codes (uppercase = Red, lowercase = Black):
#   R=chariot, N=horse, B=elephant, A=advisor, K=king/general, C=cannon, P=pawn/soldier

PIECE_MAP = {
    '車': 'R', '俥': 'R',
    '馬': 'N', '傌': 'N',
    '相': 'B', '象': 'B',
    '仕': 'A', '士': 'A',
    '帥': 'K', '將': 'K',
    '炮': 'C', '砲': 'C',
    '兵': 'P', '卒': 'P',
}

# Chinese/fullwidth digit → int
NUM_MAP = {
    '一': 1, '二': 2, '三': 3, '四': 4, '五': 5,
    '六': 6, '七': 7, '八': 8, '九': 9,
    '１': 1, '２': 2, '３': 3, '４': 4, '５': 5,
    '６': 6, '７': 7, '８': 8, '９': 9,
    '1': 1, '2': 2, '3': 3, '4': 4, '5': 5,
    '6': 6, '7': 7, '8': 8, '9': 9,
}

FWD  = {'進', '进'}
BACK = {'退', '退'}
SIDE = {'平'}


def fen_to_board(fen: str):
    """Parse FEN position string into board[rank][file] = piece_char."""
    pos = fen.split()[0]
    board = [[None] * 9 for _ in range(10)]
    rank, file = 9, 0
    for ch in pos:
        if ch == '/':
            rank -= 1
            file = 0
        elif ch.isdigit():
            file += int(ch)
        else:
            board[rank][file] = ch
            file += 1
    return board


def find_pieces(board, fен_code: str, is_red: bool):
    """Return [(rank, file)] for all pieces of given FEN code belonging to side."""
    target = fен_code.upper() if is_red else fен_code.lower()
    return [(r, f) for r in range(10) for f in range(9) if board[r][f] == target]


def wxf_col_to_file(col: int, is_red: bool) -> int:
    return (9 - col) if is_red else (col - 1)


def uci(r, f):
    return chr(ord('a') + f) + str(r)


def apply_uci_move(board, move: str):
    """Return new board with move applied. Does not mutate input."""
    import copy
    b = copy.deepcopy(board)
    sf = ord(move[0]) - ord('a')
    sr = int(move[1])
    df = ord(move[2]) - ord('a')
    dr = int(move[3])
    b[dr][df] = b[sr][sf]
    b[sr][sf] = None
    return b


def wxf_to_uci(token: str, board, is_red: bool) -> str | None:
    """
    Convert one WXF move token to UCI (e.g. 'e0e2').

    WXF formats:
      [piece][col][dir][to]        e.g. 炮二平五   — normal 4-char move
      [qualifier][piece][dir][to]  e.g. 前馬退五   — 4-char with rank qualifier
      [piece][qualifier][dir][to]  e.g. 車前退二   — rare 4-char alternative
      5-char:  same but piece written as 2 chars (uncommon variant, ignored here)

    Qualifiers: 前 (advanced/front), 後/后 (rear), 中 (middle)
    Used when ≥2 pieces of same type share the same file (column).

    Returns None if conversion fails.
    """
    t = token.strip()
    if len(t) < 4:
        return None

    QUALIFIERS = ('前', '后', '後', '中')

    qualifier  = None
    piece_char = None
    second     = None   # column number char (when no qualifier)

    if t[0] in QUALIFIERS:
        # Format: [qualifier][piece][dir][to]
        qualifier  = t[0]
        piece_char = t[1]
        dir_char   = t[2]
        to_char    = t[3]
    elif len(t) >= 4 and t[1] in QUALIFIERS:
        # Format: [piece][qualifier][dir][to]
        piece_char = t[0]
        qualifier  = t[1]
        dir_char   = t[2]
        to_char    = t[3]
    else:
        # Normal format: [piece][col][dir][to]
        piece_char = t[0]
        second     = t[1]
        dir_char   = t[2]
        to_char    = t[3]

    fen_code = PIECE_MAP.get(piece_char)
    if fen_code is None:
        return None

    if dir_char in FWD:
        direction = 'fwd'
    elif dir_char in BACK:
        direction = 'back'
    elif dir_char in SIDE:
        direction = 'side'
    else:
        return None

    to_val = NUM_MAP.get(to_char)
    if to_val is None:
        return None

    pieces = find_pieces(board, fen_code, is_red)
    if not pieces:
        return None

    # --- Determine source square ---
    if qualifier:
        # Sort by rank: for Red, "front" means highest rank (closest to Black's side)
        pieces_sorted = sorted(pieces, key=lambda p: p[0], reverse=is_red)
        if qualifier == '前':
            src = pieces_sorted[0]
        elif qualifier in ('後', '后'):
            src = pieces_sorted[-1]
        elif qualifier == '中' and len(pieces_sorted) >= 3:
            src = pieces_sorted[1]
        else:
            return None
    else:
        from_col = NUM_MAP.get(second)
        if from_col is None:
            return None
        from_file = wxf_col_to_file(from_col, is_red)
        candidates = [p for p in pieces if p[1] == from_file]
        if not candidates:
            return None
        # When multiple pieces share a file, "front" is the one further advanced
        candidates.sort(key=lambda p: p[0], reverse=is_red)
        src = candidates[0]

    sr, sf = src

    # --- Compute destination ---
    if direction == 'side':
        df = wxf_col_to_file(to_val, is_red)
        dr = sr
    elif direction in ('fwd', 'back'):
        sign = 1 if is_red else -1
        if direction == 'back':
            sign = -sign

        if fen_code in ('R', 'C', 'K', 'P'):
            dr = sr + sign * to_val
            df = sf
        elif fen_code == 'N':
            df = wxf_col_to_file(to_val, is_red)
            delta_f = abs(df - sf)
            if delta_f == 1:
                dr = sr + sign * 2
            elif delta_f == 2:
                dr = sr + sign * 1
            else:
                return None
        elif fen_code in ('B', 'A'):
            df = wxf_col_to_file(to_val, is_red)
            delta_f = abs(df - sf)
            dr = sr + sign * delta_f
        else:
            return None
    else:
        return None

    if not (0 <= dr <= 9 and 0 <= df <= 8):
        return None

    return uci(sr, sf) + uci(dr, df)


# ---------------------------------------------------------------------------
# PGN parsing
# ---------------------------------------------------------------------------

def parse_pgn(text: str) -> dict | None:
    headers = {}
    for line in text.splitlines():
        m = re.match(r'\[(\w+)\s+"(.*)"\]', line.strip())
        if m:
            headers[m.group(1)] = m.group(2)

    fen = headers.get('FEN')
    if not fen:
        return None

    # Strip header lines and comments/variations, then tokenise moves
    body = re.sub(r'\[[^\]]*\]', '', text)
    body = re.sub(r'\{[^}]*\}', '', body)
    body = re.sub(r'\([^)]*\)', '', body)

    tokens = []
    for tok in body.split():
        if re.match(r'^\d+\.$', tok):
            continue
        if tok in ('1-0', '0-1', '½-½', '1/2-1/2', '*'):
            continue
        tokens.append(tok)

    return {
        'fen':    fen,
        'moves':  tokens,
        'event':  headers.get('Event', ''),
        'red':    headers.get('Red', ''),
        'black':  headers.get('Black', ''),
        'result': headers.get('Result', '*'),
        'date':   headers.get('Date', ''),
        'site':   headers.get('Site', ''),
    }


def convert_moves(fen: str, wxf_tokens: list[str]) -> tuple[list[str], int]:
    """
    Convert WXF tokens to UCI moves.
    Returns (uci_moves, fail_index) where fail_index=-1 means full success,
    otherwise is the index of the first unconvertible token.
    """
    board = fen_to_board(fen)
    side_token = fen.split()[1] if len(fen.split()) > 1 else 'w'
    is_red = (side_token == 'w')

    uci_moves = []
    for i, tok in enumerate(wxf_tokens):
        move = wxf_to_uci(tok, board, is_red)
        if move is None:
            return uci_moves, i
        uci_moves.append(move)
        board = apply_uci_move(board, move)
        is_red = not is_red

    return uci_moves, -1


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

FOLDERS = [
    ('Dataset/殺局_殺法_練習題', 'kill', 4),
    ('Dataset/中局',             'mid',  4),
    ('Dataset/殘局',             'end',  4),
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input',  required=True, help='Dataset root directory')
    parser.add_argument('--output', default='puzzles_imported.json')
    parser.add_argument('--max',    type=int, default=0, help='Max files per folder (0=all)')
    args = parser.parse_args()

    root = Path(args.input)
    results = []
    total_errors = 0

    for rel_path, prefix, category in FOLDERS:
        folder = root / rel_path
        if not folder.exists():
            print(f'[SKIP] not found: {folder}', file=sys.stderr)
            continue

        files = sorted(folder.glob('*.pgn'))
        if args.max:
            files = files[:args.max]

        ok = errors = 0
        print(f'\n{rel_path.split("/")[-1]} ({len(files)} files)')

        for pgn_file in files:
            try:
                for enc in ('big5', 'utf-8', 'gb2312', 'gbk'):
                    try:
                        text = pgn_file.read_bytes().decode(enc)
                        break
                    except (UnicodeDecodeError, LookupError):
                        continue
                else:
                    print(f'  [FAIL] cannot decode: {pgn_file.name}', file=sys.stderr)
                    errors += 1
                    continue
                data = parse_pgn(text)
                if not data:
                    errors += 1
                    continue

                uci_moves, fail_idx = convert_moves(data['fen'], data['moves'])
                if not uci_moves:
                    print(f'  [FAIL] no moves converted: {pgn_file.name}', file=sys.stderr)
                    errors += 1
                    continue

                truncated = fail_idx >= 0
                if truncated:
                    print(f'  [TRUNC] {pgn_file.name} at move {fail_idx+1}/{len(data["moves"])}', file=sys.stderr)

                idx = pgn_file.stem
                pid = f'{prefix}-{idx}'
                event = data['event'] or f"{data['red']} vs {data['black']}"

                results.append({
                    'id':        pid,
                    'title':     event,
                    'category':  category,
                    'hint':      f"{data['red']} (Red) — {data['black']} (Black), {data['date']}",
                    'fen':       data['fen'],
                    'solution':  uci_moves,
                    'source':    rel_path.split('/')[-1],
                    'result':    data['result'],
                    'truncated': truncated,
                })
                ok += 1

            except Exception as e:
                print(f'  [ERROR] {pgn_file.name}: {e}', file=sys.stderr)
                errors += 1

        total_errors += errors
        print(f'  → {ok} ok, {errors} failed')

    Path(args.output).write_text(
        json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8'
    )
    print(f'\nTotal: {len(results)} entries → {args.output}')
    if total_errors:
        print(f'Warnings: {total_errors} files failed (see stderr)')


if __name__ == '__main__':
    main()
