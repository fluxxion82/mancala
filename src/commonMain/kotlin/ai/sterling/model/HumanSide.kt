package ai.sterling.model

enum class HumanSide {
    PLAYER_ONE,
    PLAYER_TWO;

    val aiSide: HumanSide
        get() = if (this == PLAYER_ONE) PLAYER_TWO else PLAYER_ONE
}

fun Game.GameStatus.isHumansTurn(humanSide: HumanSide?): Boolean {
    if (humanSide == null) return false
    return when (this) {
        Game.GameStatus.PlayerOneTurn -> humanSide == HumanSide.PLAYER_ONE
        Game.GameStatus.PlayerTwoTurn -> humanSide == HumanSide.PLAYER_TWO
        is Game.GameStatus.Finished -> false
    }
}
