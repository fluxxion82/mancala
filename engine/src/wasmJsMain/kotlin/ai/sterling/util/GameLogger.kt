@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package ai.sterling.util

import ai.sterling.engine.MoveTelemetry
import ai.sterling.model.Game.GameStatus
import kotlin.random.Random

/**
 * WasmJs/browser actual: POSTs each completed game as a single JSONL line to a
 * Cloudflare Worker, which writes it to an R2 bucket. The line format matches
 * the JVM logger exactly so `ingest_human_games.py` consumes web games and
 * desktop games unchanged.
 */
actual class GameLogger actual constructor() {

    private val endpoint = "https://mancala-log-game.fluxxion.workers.dev/"
    private val aiModel = "mancala_weights.bin"

    private val moves = mutableListOf<Int>()
    private var humanPlayer = 0
    private var gameId = ""

    actual fun startGame(humanIsPlayerOne: Boolean) {
        moves.clear()
        humanPlayer = if (humanIsPlayerOne) 0 else 1
        gameId = "${nowIso()}-${Random.nextInt(0, 1_000_000)}"
    }

    actual fun recordMove(absolutePocket: Int, wasPlayerOne: Boolean) {
        // Convert absolute pocket (0-12) to relative action (0-5), matching JVM.
        val relative = if (wasPlayerOne) absolutePocket else absolutePocket - 7
        moves.add(relative)
    }

    actual fun recordAiMove(telemetry: MoveTelemetry) {
        // Web build skips per-move search telemetry — bandwidth/storage cost
        // isn't justified for current traffic. Desktop still captures it.
    }

    actual fun endGame(status: GameStatus) {
        val winner = when (status) {
            is GameStatus.Finished.PlayerOneWin -> 0
            is GameStatus.Finished.PlayerTwoWin -> 1
            is GameStatus.Finished.Draw -> -1
            else -> return
        }
        val movesJson = moves.joinToString(",")
        val timestamp = nowIso()
        val line = """{"moves":[$movesJson],"human_player":$humanPlayer,"winner":$winner,"ai_model":"$aiModel","timestamp":"$timestamp","game_id":"$gameId"}"""
        moves.clear()
        postFireAndForget(endpoint, line)
    }
}

@JsFun("() => new Date().toISOString()")
private external fun nowIso(): String

@JsFun(
    """
    (url, body) => {
        try {
            fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: body,
                keepalive: true
            }).catch(() => {});
        } catch (e) {}
    }
    """
)
private external fun postFireAndForget(url: String, body: String)
