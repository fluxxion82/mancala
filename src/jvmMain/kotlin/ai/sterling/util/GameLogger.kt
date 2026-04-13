package ai.sterling.util

import ai.sterling.model.Game.GameStatus
import java.io.File
import java.time.Instant

/**
 * Logs human-vs-AI games to a JSONL file for use as training data.
 *
 * Each completed game is written as a single JSON line containing the move
 * sequence (relative to whoever moved), which side the human played, and the
 * winner. The Python ingest_human_games.py script consumes this format.
 */
class GameLogger(
    private val logFile: File = File(System.getProperty("user.home"), "mancala_games.jsonl"),
    private val aiModel: String = "mancala_weights.npz",
) {
    private val moves = mutableListOf<Int>()
    private var humanPlayer: Int = 0

    /** Call when a new game starts. Resets the move buffer. */
    fun startGame(humanIsPlayerOne: Boolean = true) {
        moves.clear()
        humanPlayer = if (humanIsPlayerOne) 0 else 1
    }

    /**
     * Call after each move is played. `absolutePocket` is the 0-12 pocket index
     * the player chose (the same value passed to Game.makeMove). `wasPlayerOne`
     * is whether Player 1 (P0 in the JSON format) made this move.
     */
    fun recordMove(absolutePocket: Int, wasPlayerOne: Boolean) {
        // Convert absolute pocket (0-12) to relative action (0-5)
        val relative = if (wasPlayerOne) absolutePocket else absolutePocket - 7
        moves.add(relative)
    }

    /**
     * Call when the game finishes. `status` must be a `GameStatus.Finished` variant.
     * Writes one JSON line to the log file and clears the buffer.
     */
    fun endGame(status: GameStatus) {
        val winner = when (status) {
            is GameStatus.Finished.PlayerOneWin -> 0
            is GameStatus.Finished.PlayerTwoWin -> 1
            is GameStatus.Finished.Draw -> -1
            else -> return  // game not actually finished — bail
        }
        val movesJson = moves.joinToString(",")
        val timestamp = Instant.now().toString()
        val line = """{"moves":[$movesJson],"human_player":$humanPlayer,"winner":$winner,"ai_model":"$aiModel","timestamp":"$timestamp"}"""
        try {
            logFile.parentFile?.mkdirs()
            logFile.appendText(line + "\n")
        } catch (e: Exception) {
            // Don't crash the app if logging fails — just print
            println("GameLogger: failed to write log: ${e.message}")
        }
        moves.clear()
    }
}
