package ai.sterling.model

data class Board(
    val pockets: List<Int>,
    val playerOne: Player,
    val playerTwo: Player
) {
    init {
        require(pockets.size == TOTAL_POCKETS) { "Board must have exactly $TOTAL_POCKETS pockets" }
        val nonMancalaSum = pockets.filterIndexed { index, _ ->
            index != PLAYER_ONE_MANCALA && index != PLAYER_TWO_MANCALA
        }.sum()
        require(nonMancalaSum + playerOne.mancala + playerTwo.mancala == TOTAL_STONES) {
            "Total stones must equal $TOTAL_STONES"
        }
        require(pockets[PLAYER_ONE_MANCALA] == playerOne.mancala) { "Player One mancala count mismatch" }
        require(pockets[PLAYER_TWO_MANCALA] == playerTwo.mancala) { "Player Two mancala count mismatch" }
    }

    fun playMove(position: Int, isPlayerOneTurn: Boolean): MoveResult {
        require(isValidMove(position, isPlayerOneTurn)) { "Invalid move: $position" }

        val (newPockets, lastPosition, stonesCollected) = distributeStones(position, isPlayerOneTurn)
        val (updatedPockets, capturedStones) = handleCapture(newPockets, lastPosition, isPlayerOneTurn)

        val (newPlayerOne, newPlayerTwo) = when {
            isPlayerOneTurn -> Pair(
                playerOne.addToMancala(stonesCollected + capturedStones),
                playerTwo
            )
            else -> Pair(
                playerOne,
                playerTwo.addToMancala(stonesCollected + capturedStones)
            )
        }

        return MoveResult(
            board = copy(
                pockets = updatedPockets,
                playerOne = newPlayerOne,
                playerTwo = newPlayerTwo
            ),
            endsInMancala = lastPosition == if (isPlayerOneTurn) PLAYER_ONE_MANCALA else PLAYER_TWO_MANCALA
        )
    }

    private fun distributeStones(
        startPosition: Int,
        isPlayerOneTurn: Boolean
    ): Triple<List<Int>, Int, Int> {
        val stones = pockets[startPosition]
        var currentPockets = pockets.toMutableList()
        currentPockets[startPosition] = 0

        var stonesCollected = 0
        var currentPosition = startPosition
        var remainingStones = stones

        while (remainingStones > 0) {
            currentPosition = (currentPosition + 1) % TOTAL_POCKETS

            if ((isPlayerOneTurn && currentPosition == PLAYER_TWO_MANCALA) ||
                (!isPlayerOneTurn && currentPosition == PLAYER_ONE_MANCALA)) {
                continue
            }

            if ((isPlayerOneTurn && currentPosition == PLAYER_ONE_MANCALA) ||
                (!isPlayerOneTurn && currentPosition == PLAYER_TWO_MANCALA)) {
                stonesCollected++
            }

            currentPockets[currentPosition]++
            remainingStones--
        }

        return Triple(currentPockets, currentPosition, stonesCollected)
    }

    private fun handleCapture(
        pockets: List<Int>,
        lastPosition: Int,
        isPlayerOneTurn: Boolean
    ): Pair<List<Int>, Int> {
        if (!canCapture(pockets, lastPosition, isPlayerOneTurn)) {
            return Pair(pockets, 0)
        }

        val oppositePosition = TOTAL_POCKETS - 2 - lastPosition
        val capturedStones = pockets[oppositePosition] + 1
        val newPockets = pockets.toMutableList().apply {
            this[lastPosition] = 0
            this[oppositePosition] = 0
            if (isPlayerOneTurn) {
                this[PLAYER_ONE_MANCALA] += capturedStones
            } else {
                this[PLAYER_TWO_MANCALA] += capturedStones
            }
        }

        return Pair(newPockets, capturedStones)
    }

    private fun isValidMove(position: Int, isPlayerOneTurn: Boolean): Boolean =
        when {
            position < 0 || position >= TOTAL_POCKETS -> false
            position == PLAYER_ONE_MANCALA || position == PLAYER_TWO_MANCALA -> false
            isPlayerOneTurn && position !in PLAYER_ONE_POCKETS -> false
            !isPlayerOneTurn && position !in PLAYER_TWO_POCKETS -> false
            pockets[position] == 0 -> false
            else -> true
        }

    private fun canCapture(
        pockets: List<Int>,
        lastPosition: Int,
        isPlayerOneTurn: Boolean
    ): Boolean {
        if (lastPosition == PLAYER_ONE_MANCALA || lastPosition == PLAYER_TWO_MANCALA) {
            return false
        }

        val isInPlayerTerritory = if (isPlayerOneTurn) {
            lastPosition in PLAYER_ONE_POCKETS
        } else {
            lastPosition in PLAYER_TWO_POCKETS
        }

        return isInPlayerTerritory &&
                pockets[lastPosition] == 1 &&
                pockets[TOTAL_POCKETS - 2 - lastPosition] > 0
    }

    fun isGameOver(): Boolean =
        pockets.slice(PLAYER_ONE_POCKETS).all { it == 0 } ||
                pockets.slice(PLAYER_TWO_POCKETS).all { it == 0 }

    fun legalMoves(isPlayerOneTurn: Boolean): List<Int> =
        if (isPlayerOneTurn) {
            PLAYER_ONE_POCKETS.filter { pockets[it] > 0 }
        } else {
            PLAYER_TWO_POCKETS.filter { pockets[it] > 0 }
        }

    fun printBoard() {
        println(
            """
                       ${pockets[12]} | ${pockets[11]} | ${pockets[10]} | ${pockets[9]} | ${pockets[8]} | ${pockets[7]}
                    ${pockets[PLAYER_TWO_MANCALA]}                       ${pockets[PLAYER_ONE_MANCALA]}
                       ${pockets[0]} | ${pockets[1]} | ${pockets[2]} | ${pockets[3]} | ${pockets[4]} | ${pockets[5]}

                """.trimIndent()
        )
    }

    companion object {
        const val TOTAL_POCKETS = 14
        const val TOTAL_STONES = 48
        const val PLAYER_ONE_MANCALA = 6
        const val PLAYER_TWO_MANCALA = 13
        val PLAYER_ONE_POCKETS = 0..5
        val PLAYER_TWO_POCKETS = 7..12

        fun new(): Board = Board(
            pockets = List(TOTAL_POCKETS) { if (it == PLAYER_ONE_MANCALA || it == PLAYER_TWO_MANCALA) 0 else 4 },
            playerOne = Player(0),
            playerTwo = Player(0)
        )
    }
}

data class MoveResult(
    val board: Board,
    val endsInMancala: Boolean
)
