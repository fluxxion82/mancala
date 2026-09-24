package ai.sterling.model

class Game (
    val board: Board,
    var status: GameStatus = GameStatus.PlayerOneTurn,
) {
    fun makeMove(position: Int): Game {
        require(status !is GameStatus.Finished) { "Game is already finished" }

        val isPlayerOneTurn = status == GameStatus.PlayerOneTurn
        val result = board.playMove(position, isPlayerOneTurn)

        return if (result.board.isGameOver()) {
            copy(board = result.board, status = determineWinner(result.board))
        } else {
            copy(
                board = result.board,
                status = if (result.endsInMancala) status else toggleTurn()
            )
        }
    }

    private fun determineWinner(board: Board): GameStatus.Finished = when {
        board.playerOne.mancala > board.playerTwo.mancala -> GameStatus.Finished.PlayerOneWin
        board.playerTwo.mancala > board.playerOne.mancala -> GameStatus.Finished.PlayerTwoWin
        else -> GameStatus.Finished.Draw
    }

    private fun toggleTurn(): GameStatus = when (status) {
        is GameStatus.PlayerOneTurn -> GameStatus.PlayerTwoTurn
        is GameStatus.PlayerTwoTurn -> GameStatus.PlayerOneTurn
        is GameStatus.Finished -> status
    }

    fun copy(
        board: Board = this.board,
        status: GameStatus = this.status
    ): Game = Game(board, status)

    companion object {
        fun new(): Game = Game(
            board = Board.new(),
            status = GameStatus.PlayerOneTurn
        )

        fun newGameWithPosition(pockets: List<Int>, isPlayerOneTurn: Boolean): Game {
            val board = Board(
                pockets = pockets,
                playerOne = Player(pockets[Board.PLAYER_ONE_MANCALA]),
                playerTwo = Player(pockets[Board.PLAYER_TWO_MANCALA])
            )

            return Game(
                board = board,
                status = if (isPlayerOneTurn) GameStatus.PlayerOneTurn else GameStatus.PlayerTwoTurn
            )
        }
    }

    sealed class GameStatus {
        data object PlayerOneTurn : GameStatus()
        data object PlayerTwoTurn : GameStatus()

        sealed class Finished : GameStatus() {
            data object PlayerOneWin : Finished()
            data object PlayerTwoWin : Finished()
            data object Draw : Finished()
        }
    }
}
