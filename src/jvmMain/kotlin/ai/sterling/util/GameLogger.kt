package ai.sterling.util

import ai.sterling.model.Game.GameStatus
import java.io.File
import java.time.Instant

/**
 * JVM/Desktop actual: appends each completed game as a JSONL line under
 * `~/mancala_games.jsonl`. The Python ingest_human_games.py script consumes this format.
 */
actual class GameLogger actual constructor() {

    private val logFile: File = File(System.getProperty("user.home"), "mancala_games.jsonl")
    private val aiModel: String = "mancala_weights.bin"

    private val moves = mutableListOf<Int>()
    private var humanPlayer: Int = 0

    actual fun startGame(humanIsPlayerOne: Boolean) {
        moves.clear()
        humanPlayer = if (humanIsPlayerOne) 0 else 1
    }

    actual fun recordMove(absolutePocket: Int, wasPlayerOne: Boolean) {
        // Convert absolute pocket (0-12) to relative action (0-5).
        val relative = if (wasPlayerOne) absolutePocket else absolutePocket - 7
        moves.add(relative)
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
        val line = """{"moves":[$movesJson],"human_player":$humanPlayer,"winner":$winner,"ai_model":"$aiModel","timestamp":"$timestamp"}"""
        try {
            logFile.parentFile?.mkdirs()
            logFile.appendText(line + "\n")
        } catch (e: Exception) {
            println("GameLogger: failed to write log: ${e.message}")
        }
        moves.clear()
    }
}
