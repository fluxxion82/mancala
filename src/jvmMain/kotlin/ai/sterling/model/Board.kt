package ai.sterling.model

data class Board(
    val playerOne: Player,
    val playerTwo: Player,
    val pockets: MutableList<Int>,
) {
    var lastMovePlayed: Int = 0

    fun playMove(position: Int): Int {
        if (playerOne.turn && position == PLAYER_ONE_MANCALA_POSITION
            || playerTwo.turn && position == PLAYER_TWO_MANCALA_POSITION
            || position == PLAYER_TWO_MANCALA_POSITION
            ) error("Invalid position")

        val stonesFromPosition = pockets[position]
        pockets[position] = 0
        var pocket = position + 1
        for (stone in 0 until stonesFromPosition) {
            if (playerOne.turn && pocket == PLAYER_TWO_MANCALA_POSITION) {
                pocket = 0
            } else if (playerTwo.turn && pocket == PLAYER_ONE_MANCALA_POSITION) {
                pocket++
            }

            addStone(pocket)

            if (stone == stonesFromPosition - 1) {
                handleLastPocket(pocket)
            } else {
                if (pocket == PLAYER_TWO_MANCALA_POSITION) {
                    pocket = 0
                } else {
                    pocket++
                }
            }
        }

        assert(pockets.sumOf { it } == 48)
        return pocket // so we know if the play ends with a score
    }

    private fun addStone(position: Int) {
        lastMovePlayed = position
        pockets[position] = pockets[position] + 1

        if (position == PLAYER_ONE_MANCALA_POSITION) {
            playerOne.mancala++
        } else if (position == PLAYER_TWO_MANCALA_POSITION) {
            playerTwo.mancala++
        }

        assert(pockets[PLAYER_ONE_MANCALA_POSITION] == playerOne.mancala)
        assert(pockets[PLAYER_TWO_MANCALA_POSITION] == playerTwo.mancala)
    }

    private fun handleLastPocket(position: Int) {
        if (position == PLAYER_ONE_MANCALA_POSITION || position == PLAYER_TWO_MANCALA_POSITION) return

        if (playerOne.turn && position in PLAYER_TWO_POCKETS || playerTwo.turn && position in PLAYER_ONE_POCKETS) return

        val stonesInLastPit = pockets[position]
        val oppositePosition = 12 - position
        val stonesOpposite =  pockets[oppositePosition]

        if (stonesInLastPit == 1 && stonesOpposite > 0) {
            pockets[position] = 0
            pockets[oppositePosition] = 0

            if (playerOne.turn) {
                pockets[6] += stonesOpposite + 1
                playerOne.mancala += stonesOpposite + 1
                assert(pockets[PLAYER_ONE_MANCALA_POSITION] == playerOne.mancala)
            } else {
                pockets[PLAYER_TWO_MANCALA_POSITION] += stonesOpposite + 1
                playerTwo.mancala += stonesOpposite + 1
                assert(pockets[PLAYER_TWO_MANCALA_POSITION] == playerTwo.mancala)
            }
        }
    }

    fun clearRemainingPockets() {
        for (pocket in PLAYER_ONE_POCKETS) {
            val stones = pockets[pocket]
            playerOne.mancala += stones
            pockets[PLAYER_ONE_MANCALA_POSITION] += stones
            pockets[pocket] = 0
        }

        for (pocket in PLAYER_TWO_POCKETS) {
            val stones = pockets[pocket]
            playerTwo.mancala += stones
            pockets[PLAYER_TWO_MANCALA_POSITION] += stones
            pockets[pocket] = 0
        }
    }

    fun legalMoves(playerOne: Boolean): List<Int> {
        return if (playerOne) {
            pockets.mapIndexed { index, pocket ->
                if (pocket != 0 && index in PLAYER_ONE_POCKETS) index else -1
            }.filter { it != -1 }
        } else {
            pockets.mapIndexed { index, pocket ->
                if (pocket != 0 && index in PLAYER_TWO_POCKETS) index else -1
            }.filter { it != -1 }
        }.toList()
    }

    fun deepCopy() = Board(
        playerOne = playerOne.copy(),
        playerTwo = playerTwo.copy(),
        pockets = pockets.map { it }.toMutableList()
    )

    fun printBoard() {
        println(
            """
                       ${pockets[12]} | ${pockets[11]} | ${pockets[10]} | ${pockets[9]} | ${pockets[8]} | ${pockets[7]}
                    ${pockets[PLAYER_TWO_MANCALA_POSITION]}                       ${pockets[PLAYER_ONE_MANCALA_POSITION]}
                       ${pockets[0]} | ${pockets[1]} | ${pockets[2]} | ${pockets[3]} | ${pockets[4]} | ${pockets[5]}
                    
                    turn: ${if (playerOne.turn) "player One" else "player two"}
                    
                """.trimIndent()
        )
    }

    companion object {
        const val PLAYER_ONE_MANCALA_POSITION = 6
        const val PLAYER_TWO_MANCALA_POSITION = 13
        val PLAYER_ONE_POCKETS = 0..5
        val PLAYER_TWO_POCKETS = 7..12
    }

}
