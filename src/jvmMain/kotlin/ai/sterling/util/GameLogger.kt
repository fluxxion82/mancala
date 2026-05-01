package ai.sterling.util

import ai.sterling.engine.MoveTelemetry
import ai.sterling.model.Game.GameStatus
import java.io.File
import java.time.Instant

/**
 * JVM/Desktop actual: appends each completed game as a JSONL line under
 * `~/mancala_games.jsonl`. The Python ingest_human_games.py script consumes this format.
 *
 * Per-AI-move search telemetry lands in `~/mancala_ai_telemetry.jsonl` — one line
 * per AI move, with a `game_id` (the start-of-game timestamp) so lines from the
 * same game can be grouped without parsing the games file.
 */
actual class GameLogger actual constructor() {

    private val logFile: File = File(System.getProperty("user.home"), "mancala_games.jsonl")
    private val telemetryFile: File = File(System.getProperty("user.home"), "mancala_ai_telemetry.jsonl")
    private val aiModel: String = "mancala_weights.bin"

    private val moves = mutableListOf<Int>()
    private var humanPlayer: Int = 0
    private var gameId: String = ""
    private var aiMoveIndex: Int = 0

    actual fun startGame(humanIsPlayerOne: Boolean) {
        moves.clear()
        humanPlayer = if (humanIsPlayerOne) 0 else 1
        gameId = Instant.now().toString()
        aiMoveIndex = 0
    }

    actual fun recordMove(absolutePocket: Int, wasPlayerOne: Boolean) {
        // Convert absolute pocket (0-12) to relative action (0-5).
        val relative = if (wasPlayerOne) absolutePocket else absolutePocket - 7
        moves.add(relative)
    }

    actual fun recordAiMove(telemetry: MoveTelemetry) {
        val priorsJson = telemetry.priors.joinToString(",") { "%.4f".format(it) }
        val visitsJson = telemetry.visits.joinToString(",")
        val timestamp = Instant.now().toString()
        val line = buildString {
            append('{')
            append("\"timestamp\":\"$timestamp\",")
            append("\"game_id\":\"$gameId\",")
            append("\"ai_move_index\":$aiMoveIndex,")
            append("\"move\":${telemetry.move},")
            append("\"sims\":${telemetry.sims},")
            append("\"timeMs\":${telemetry.timeMs},")
            append("\"rootValue\":${"%.4f".format(telemetry.rootValue)},")
            append("\"priors\":[$priorsJson],")
            append("\"visits\":[$visitsJson],")
            append("\"chosenQ\":${"%.4f".format(telemetry.chosenQ)},")
            append("\"cacheHits\":${telemetry.cacheHits},")
            append("\"cacheMisses\":${telemetry.cacheMisses},")
            append("\"reusedRootVisits\":${telemetry.reusedRootVisits}")
            append('}')
        }
        aiMoveIndex++
        try {
            telemetryFile.parentFile?.mkdirs()
            telemetryFile.appendText(line + "\n")
        } catch (e: Exception) {
            println("GameLogger: failed to write telemetry: ${e.message}")
        }
    }

    actual fun endGame(status: GameStatus) {
        val winner = when (status) {
            is GameStatus.Finished.PlayerOneWin -> 0
            is GameStatus.Finished.PlayerTwoWin -> 1
            is GameStatus.Finished.Draw -> -1
            else -> return // not actually finished — bail
        }
        val movesJson = moves.joinToString(",")
        val timestamp = Instant.now().toString()
        val line = """{"moves":[$movesJson],"human_player":$humanPlayer,"winner":$winner,"ai_model":"$aiModel","timestamp":"$timestamp","game_id":"$gameId"}"""
        try {
            logFile.parentFile?.mkdirs()
            logFile.appendText(line + "\n")
        } catch (e: Exception) {
            println("GameLogger: failed to write log: ${e.message}")
        }
        moves.clear()
    }
}
