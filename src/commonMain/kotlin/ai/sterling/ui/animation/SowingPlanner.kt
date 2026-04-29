package ai.sterling.ui.animation

import ai.sterling.model.Board

/**
 * Pure simulator that re-derives the per-stone trace of a Mancala move so the UI can animate
 * each stone individually. Mirrors the rules in [Board.playMove], [Board.distributeStones],
 * [Board.handleCapture] and the end-of-game sweep block. The aggregate result must equal what
 * [Board.playMove] produces — see SowingPlannerTest for the equivalence assertion.
 */
object SowingPlanner {

    fun plan(
        boardPocketsBefore: List<Int>,
        position: Int,
        isPlayerOne: Boolean,
    ): AnimationPlan {
        require(boardPocketsBefore.size == Board.TOTAL_POCKETS)
        require(position in 0 until Board.TOTAL_POCKETS)
        require(boardPocketsBefore[position] > 0) {
            "No stones to sow at position $position"
        }

        val ownMancala = if (isPlayerOne) Board.PLAYER_ONE_MANCALA else Board.PLAYER_TWO_MANCALA
        val oppMancala = if (isPlayerOne) Board.PLAYER_TWO_MANCALA else Board.PLAYER_ONE_MANCALA
        val ownPits = if (isPlayerOne) Board.PLAYER_ONE_POCKETS else Board.PLAYER_TWO_POCKETS

        val pockets = boardPocketsBefore.toMutableList()
        val moves = mutableListOf<StoneMove>()

        // Phase 1: sow.
        val stonesInHand = pockets[position]
        pockets[position] = 0
        var current = position
        var lastPosition = position
        var remaining = stonesInHand
        while (remaining > 0) {
            current = (current + 1) % Board.TOTAL_POCKETS
            if (current == oppMancala) continue
            pockets[current] = pockets[current] + 1
            moves += StoneMove(fromPit = position, toPit = current, kind = StoneMoveKind.SOW)
            lastPosition = current
            remaining--
        }

        // Phase 2: capture (mirrors Board.canCapture + Board.handleCapture).
        val canCapture =
            lastPosition != Board.PLAYER_ONE_MANCALA &&
                lastPosition != Board.PLAYER_TWO_MANCALA &&
                lastPosition in ownPits &&
                pockets[lastPosition] == 1 &&
                pockets[Board.TOTAL_POCKETS - 2 - lastPosition] > 0

        if (canCapture) {
            val oppositePosition = Board.TOTAL_POCKETS - 2 - lastPosition
            val opposingCount = pockets[oppositePosition]
            // Move each captured stone from the opposite pit into own mancala first.
            repeat(opposingCount) {
                moves += StoneMove(
                    fromPit = oppositePosition,
                    toPit = ownMancala,
                    kind = StoneMoveKind.CAPTURE_OPPOSITE,
                )
            }
            pockets[oppositePosition] = 0
            // Then move the landing stone to own mancala.
            moves += StoneMove(
                fromPit = lastPosition,
                toPit = ownMancala,
                kind = StoneMoveKind.CAPTURE_LANDING,
            )
            pockets[lastPosition] = 0
            pockets[ownMancala] = pockets[ownMancala] + opposingCount + 1
        }

        // Phase 3: end-of-game sweep (mirrors the block at Board.kt lines 30-43).
        val p1SideEmpty = Board.PLAYER_ONE_POCKETS.all { pockets[it] == 0 }
        val p2SideEmpty = Board.PLAYER_TWO_POCKETS.all { pockets[it] == 0 }
        if (p1SideEmpty || p2SideEmpty) {
            // If P2 side has stones, sweep them to P2 mancala.
            if (Board.PLAYER_TWO_POCKETS.sumOf { pockets[it] } > 0) {
                for (pit in Board.PLAYER_TWO_POCKETS) {
                    val n = pockets[pit]
                    repeat(n) {
                        moves += StoneMove(
                            fromPit = pit,
                            toPit = Board.PLAYER_TWO_MANCALA,
                            kind = StoneMoveKind.SWEEP,
                        )
                    }
                    if (n > 0) {
                        pockets[Board.PLAYER_TWO_MANCALA] = pockets[Board.PLAYER_TWO_MANCALA] + n
                        pockets[pit] = 0
                    }
                }
            }
            if (Board.PLAYER_ONE_POCKETS.sumOf { pockets[it] } > 0) {
                for (pit in Board.PLAYER_ONE_POCKETS) {
                    val n = pockets[pit]
                    repeat(n) {
                        moves += StoneMove(
                            fromPit = pit,
                            toPit = Board.PLAYER_ONE_MANCALA,
                            kind = StoneMoveKind.SWEEP,
                        )
                    }
                    if (n > 0) {
                        pockets[Board.PLAYER_ONE_MANCALA] = pockets[Board.PLAYER_ONE_MANCALA] + n
                        pockets[pit] = 0
                    }
                }
            }
        }

        return AnimationPlan(moves = moves, finalPockets = pockets.toList())
    }
}
