package ai.sterling.ui.animation

import ai.sterling.model.Board
import ai.sterling.model.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SowingPlannerTest {

    @Test
    fun finalPocketsMatchBoardPlayMove_freshOpening() {
        for (position in Board.PLAYER_ONE_POCKETS) {
            assertPlanMatchesBoard(
                pocketsBefore = freshPockets(),
                position = position,
                isPlayerOne = true,
            )
        }
        for (position in Board.PLAYER_TWO_POCKETS) {
            assertPlanMatchesBoard(
                pocketsBefore = freshPockets(),
                position = position,
                isPlayerOne = false,
            )
        }
    }

    @Test
    fun captureRule_landsInOwnEmptyPit_capturesOpposite() {
        // Construct: P1 will sow from pit 0 (1 stone) into pit 1, which we set to 0,
        // then capture pit 11 (opposite of pit 1) which we set to 5.
        val pockets = mutableListOf(0, 0, 4, 4, 4, 4, 0, 4, 4, 4, 4, 5, 4, 0)
        // pit 0 = 1 stone so it lands in pit 1
        pockets[0] = 1
        // total stones: P1 side = 1+0+4+4+4+4 = 17, P2 side = 4+4+4+4+5+4 = 25, mancalas 0+0
        // total = 42 — must equal 48. Adjust:
        pockets[5] = 4 + 6  // bump
        // recompute: P1 1+0+4+4+4+10=23, P2 25, total 48. Good.

        val plan = SowingPlanner.plan(pockets, position = 0, isPlayerOne = true)

        // Last few moves should be the capture.
        val captureOpp = plan.moves.filter { it.kind == StoneMoveKind.CAPTURE_OPPOSITE }
        val captureLanding = plan.moves.filter { it.kind == StoneMoveKind.CAPTURE_LANDING }
        assertEquals(5, captureOpp.size, "should capture all 5 stones in opposite pit")
        assertTrue(captureOpp.all { it.fromPit == 11 && it.toPit == Board.PLAYER_ONE_MANCALA })
        assertEquals(1, captureLanding.size)
        assertEquals(1, captureLanding[0].fromPit)
        assertEquals(Board.PLAYER_ONE_MANCALA, captureLanding[0].toPit)

        assertPlanMatchesBoard(pocketsBefore = pockets, position = 0, isPlayerOne = true)
    }

    @Test
    fun extraTurn_doesNotEncodeStatus_planEndsAtFinalPockets() {
        // P1 has 1 stone in pit 5 → lands in own mancala (pit 6) → extra turn in game logic,
        // but the plan itself just sows 1 stone; status is the ViewModel's job to track.
        val pockets = mutableListOf(4, 4, 4, 4, 4, 1, 0, 4, 4, 4, 4, 4, 4, 0)
        // total: P1 = 4+4+4+4+4+1=21, P2=4+4+4+4+4+4=24, mancalas 0+0=0; total=45. Need 48. Bump 3 more.
        pockets[10] = 4 + 3
        // Recount: P1=21, P2=4+4+4+7+4+4=27, total 48. Good.

        val plan = SowingPlanner.plan(pockets, position = 5, isPlayerOne = true)
        assertEquals(1, plan.moves.size)
        assertEquals(StoneMoveKind.SOW, plan.moves[0].kind)
        assertEquals(5, plan.moves[0].fromPit)
        assertEquals(Board.PLAYER_ONE_MANCALA, plan.moves[0].toPit)

        assertPlanMatchesBoard(pocketsBefore = pockets, position = 5, isPlayerOne = true)
    }

    @Test
    fun sweep_movesRemainingStonesToOwnMancala() {
        // P2 to move from pit 12 (1 stone) — landing in P2 mancala (13). After move,
        // P2 side becomes empty, so P1 side stones should sweep to P1 mancala.
        val pockets = mutableListOf(0, 0, 2, 1, 1, 0, 25, 0, 0, 0, 0, 0, 1, 18)
        val plan = SowingPlanner.plan(pockets, position = 12, isPlayerOne = false)

        val sweepMoves = plan.moves.filter { it.kind == StoneMoveKind.SWEEP }
        // P1 side has 0+0+2+1+1+0 = 4 leftover stones, all flying to P1 mancala (6).
        assertEquals(4, sweepMoves.size)
        assertTrue(sweepMoves.all { it.toPit == Board.PLAYER_ONE_MANCALA })

        assertPlanMatchesBoard(pocketsBefore = pockets, position = 12, isPlayerOne = false)
    }

    @Test
    fun skipsOpponentMancala_duringSow() {
        // P1 sows from pit 5 with enough stones to wrap past pit 13 (P2 mancala).
        // Position 5 with 9 stones: stones land in 6,7,8,9,10,11,12, [SKIP 13], 0, 1.
        val pockets = mutableListOf(4, 4, 4, 4, 4, 9, 0, 4, 4, 4, 4, 4, 4, 0)
        // total: 4*5+9 + 4*6 = 29+24 = 53; subtract 5 to get 48: pockets[10] -= 5
        pockets[10] = 0
        // Recount: 4+4+4+4+4+9 + 4+4+4+0+4+4 = 29+20 = 49; need 48. pockets[11] -= 1
        pockets[11] = 3
        // Recount: 29 + (4+4+4+0+4+3) = 29+19 = 48. Good.

        val plan = SowingPlanner.plan(pockets, position = 5, isPlayerOne = true)
        // 9 SOW moves, none with toPit == 13.
        val sows = plan.moves.filter { it.kind == StoneMoveKind.SOW }
        assertEquals(9, sows.size)
        assertTrue(sows.none { it.toPit == Board.PLAYER_TWO_MANCALA })
        // Last sow should be at pit 1 (after wrapping).
        assertEquals(1, sows.last().toPit)

        assertPlanMatchesBoard(pocketsBefore = pockets, position = 5, isPlayerOne = true)
    }

    // ---------- helpers ----------

    private fun freshPockets(): List<Int> = List(Board.TOTAL_POCKETS) { idx ->
        if (idx == Board.PLAYER_ONE_MANCALA || idx == Board.PLAYER_TWO_MANCALA) 0 else 4
    }

    private fun boardFrom(pockets: List<Int>): Board = Board(
        pockets = pockets,
        playerOne = Player(pockets[Board.PLAYER_ONE_MANCALA]),
        playerTwo = Player(pockets[Board.PLAYER_TWO_MANCALA]),
    )

    private fun assertPlanMatchesBoard(
        pocketsBefore: List<Int>,
        position: Int,
        isPlayerOne: Boolean,
    ) {
        val plan = SowingPlanner.plan(pocketsBefore, position, isPlayerOne)
        val board = boardFrom(pocketsBefore)
        val expected = board.playMove(position, isPlayerOne).board.pockets
        assertEquals(
            expected,
            plan.finalPockets,
            "Plan finalPockets must equal Board.playMove output for position=$position isP1=$isPlayerOne, before=$pocketsBefore",
        )
    }
}
