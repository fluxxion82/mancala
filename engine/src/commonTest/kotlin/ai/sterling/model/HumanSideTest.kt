package ai.sterling.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HumanSideTest {

    @Test
    fun aiSideIsTheOppositeSide() {
        assertEquals(HumanSide.PLAYER_TWO, HumanSide.PLAYER_ONE.aiSide)
        assertEquals(HumanSide.PLAYER_ONE, HumanSide.PLAYER_TWO.aiSide)
    }

    @Test
    fun isHumansTurn_whenHumanIsP1() {
        assertTrue(Game.GameStatus.PlayerOneTurn.isHumansTurn(HumanSide.PLAYER_ONE))
        assertFalse(Game.GameStatus.PlayerTwoTurn.isHumansTurn(HumanSide.PLAYER_ONE))
    }

    @Test
    fun isHumansTurn_whenHumanIsP2() {
        assertFalse(Game.GameStatus.PlayerOneTurn.isHumansTurn(HumanSide.PLAYER_TWO))
        assertTrue(Game.GameStatus.PlayerTwoTurn.isHumansTurn(HumanSide.PLAYER_TWO))
    }

    @Test
    fun isHumansTurn_isFalseWhenGameFinished() {
        for (side in HumanSide.entries) {
            assertFalse(Game.GameStatus.Finished.PlayerOneWin.isHumansTurn(side))
            assertFalse(Game.GameStatus.Finished.PlayerTwoWin.isHumansTurn(side))
            assertFalse(Game.GameStatus.Finished.Draw.isHumansTurn(side))
        }
    }

    @Test
    fun isHumansTurn_isFalseWhenHumanSideNotChosen() {
        assertFalse(Game.GameStatus.PlayerOneTurn.isHumansTurn(null))
        assertFalse(Game.GameStatus.PlayerTwoTurn.isHumansTurn(null))
        assertFalse(Game.GameStatus.Finished.PlayerOneWin.isHumansTurn(null))
        assertFalse(Game.GameStatus.Finished.PlayerTwoWin.isHumansTurn(null))
        assertFalse(Game.GameStatus.Finished.Draw.isHumansTurn(null))
    }
}
