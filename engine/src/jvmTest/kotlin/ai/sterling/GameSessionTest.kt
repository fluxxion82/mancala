package ai.sterling

import ai.sterling.engine.AiBackend
import ai.sterling.model.Game
import ai.sterling.model.HumanSide
import ai.sterling.session.GameSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameSessionTest {

    /** Deterministic stand-in for the NN: plays the lowest legal pocket. */
    private class FirstLegalBackend : AiBackend {
        var calls = 0
        override suspend fun selectMove(game: Game, mode: AiMode): Int {
            calls++
            val p1 = game.status == Game.GameStatus.PlayerOneTurn
            return game.board.legalMoves(p1).first()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val backend = FirstLegalBackend()
    private val session = GameSession(backend, AiMode.AlphaBeta(), scope, gameLogger = null)

    @AfterTest
    fun tearDown() = scope.cancel()

    @Test
    fun nothingHappensBeforeRestart() {
        assertNull(session.humanSide.value)
        assertFalse(session.isLegalMove(0))
        assertFalse(session.playHumanMove(0))
        assertEquals(0, backend.calls)
    }

    @Test
    fun humanFirst_rejectsIllegalMoves_andAiRepliesAutomatically() = runBlocking {
        session.restart(HumanSide.PLAYER_ONE)
        assertFalse(session.playHumanMove(6), "own mancala is not playable")
        assertFalse(session.playHumanMove(8), "opponent pit is not playable")

        // Pocket 0 (4 stones) ends in pocket 4 → turn passes to the AI.
        assertTrue(session.playHumanMove(0))
        withTimeout(5_000) {
            session.game.first { it.status == Game.GameStatus.PlayerOneTurn }
        }
        assertEquals(1, backend.calls)
        assertFalse(session.isAiThinking.value)
        // AI (P2) played its lowest legal pocket, 7.
        assertEquals(0, session.game.value.board.pockets[7])
    }

    @Test
    fun humanSecond_aiOpensTheGame() = runBlocking {
        session.restart(HumanSide.PLAYER_TWO)
        withTimeout(5_000) {
            session.game.first { it.status == Game.GameStatus.PlayerTwoTurn }
        }
        assertTrue(backend.calls >= 1)
        assertTrue(session.isLegalMove(7) || session.isLegalMove(8))
        assertFalse(session.isLegalMove(0))
    }
}
