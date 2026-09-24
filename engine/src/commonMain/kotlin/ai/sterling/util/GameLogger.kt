package ai.sterling.util

import ai.sterling.engine.MoveTelemetry
import ai.sterling.model.Game.GameStatus

/**
 * Records human-vs-AI games for later use as training data, plus per-AI-move
 * search telemetry for debugging.
 *
 * Each completed game ends up as a single JSON line in the games file:
 *   {"moves":[..],"human_player":0|1,"winner":-1|0|1,"ai_model":"...","timestamp":"..."}
 *
 * Each AI move appends a line to the telemetry file (see [MoveTelemetry]):
 *   {"timestamp":"...","game_id":"...","move":N,"sims":N,"timeMs":N,"rootValue":F,
 *    "priors":[..],"visits":[..],"chosenQ":F,"cacheHits":N,"cacheMisses":N,
 *    "reusedRootVisits":N}
 *
 * Where the data is persisted is platform-specific (file on JVM; no-op on web).
 */
expect class GameLogger() {
    fun startGame(humanIsPlayerOne: Boolean)
    fun recordMove(absolutePocket: Int, wasPlayerOne: Boolean)
    fun recordAiMove(telemetry: MoveTelemetry)
    fun endGame(status: GameStatus)
}
