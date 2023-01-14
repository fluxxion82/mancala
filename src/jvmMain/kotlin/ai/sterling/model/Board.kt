package ai.sterling.model

data class Board(
    val playerOne: Player,
    val playerTwo: Player,
    val pockets: MutableList<Int>,
) {
    var lastMovePlayed: Int = 0

    fun playMove(position: Int): Int {
        if (playerOne.turn && position == 6 || playerTwo.turn && position == 13 || position == 13)
            error("Invalid position")

        val stonesFromPosition = pockets[position]
        pockets[position] = 0
        var pocket = position + 1
        for (stone in 0 until stonesFromPosition) {
            if (playerOne.turn && pocket == 13) {
                pocket = 0
            } else if (playerTwo.turn && pocket == 6) {
                pocket++
            }

            addStone(pocket)

            if (stone == stonesFromPosition - 1) {
                handleLastPocket(pocket)
            } else {
                if (pocket == 13) {
                    pocket = 0
                } else {
                    pocket++
                }
            }
        }

        return pocket // so we know if the play ends with a score
    }

    private fun addStone(position: Int) {
        lastMovePlayed = position
        pockets[position] = pockets[position] + 1

        if (position == 6) {
            playerOne.mancala++
        } else if (position == 13) {
            playerTwo.mancala++
        }

        assert(pockets[6] == playerOne.mancala)
        assert(pockets[13] == playerTwo.mancala)
    }

    private fun handleLastPocket(position: Int) {
        if (position == 6 || position == 13) return

        if (playerOne.turn && position in 7..12 || playerTwo.turn && position in 0..5) return

        val stonesInLastPit = pockets[position]
        val oppositePosition = 12 - position
        val stonesOpposite =  pockets[oppositePosition]

        if (stonesInLastPit == 1 && stonesOpposite > 0) {
            pockets[position] = 0
            pockets[oppositePosition] = 0

            if (playerOne.turn) {
                pockets[6] += stonesOpposite + 1
                playerOne.mancala = playerOne.mancala + stonesOpposite + 1
                assert(pockets[6] == playerOne.mancala)
            } else {
                pockets[13] += stonesOpposite + 1
                playerTwo.mancala = playerTwo.mancala  + stonesOpposite + 1
                assert(pockets[13] == playerTwo.mancala)
            }
        }
    }

    fun clearRemainingPockets() {
        for (pocket in 0..5) {
            val stones = pockets[pocket]
            playerOne.mancala += stones
        }

        for (pocket in 7..12) {
            val stones = pockets[pocket]
            playerTwo.mancala += stones
        }
    }

    fun legalMoves(playerOne: Boolean): List<Int> {
        return if (playerOne) {
            pockets.mapIndexed { index, pocket ->
                if (pocket != 0 && index in 0..5) index else -1
            }.filter { it != -1 }
        } else {
            pockets.mapIndexed { index, pocket ->
                if (pocket != 0 && index in 7..12) index else -1
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
                    ${pockets[13]}                       ${pockets[6]}
                       ${pockets[0]} | ${pockets[1]} | ${pockets[2]} | ${pockets[3]} | ${pockets[4]} | ${pockets[5]}
                    
                    turn: ${if (playerOne.turn) "player One" else "player two"}
                    
                """.trimIndent()
        )
    }
}
