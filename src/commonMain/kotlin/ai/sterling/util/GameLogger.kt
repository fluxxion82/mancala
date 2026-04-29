package ai.sterling.util

import ai.sterling.model.Game.GameStatus

/**
 * Records human-vs-AI games for later use as training data.
 *
 * Each completed game ends up as a single JSON line:
 *   {"moves":[..],"human_player":0|1,"winner":-1|0|1,"ai_model":"...","timestamp":"..."}
 *
 * Where the result is persisted is platform-specific (file on JVM; not yet decided on web).
 */
expect class GameLogger() {
    fun startGame(humanIsPlayerOne: Boolean)
    fun recordMove(absolutePocket: Int, wasPlayerOne: Boolean)
    fun endGame(status: GameStatus)
}
