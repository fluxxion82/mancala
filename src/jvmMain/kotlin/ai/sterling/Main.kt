import ai.sterling.AiMode
import ai.sterling.MancalaGame
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = remember { WindowState(size = DpSize(1000.dp, 900.dp)) },
        title = "Mancala",
    ) {
        // Stage D switch: AlphaBeta search at deploy time. The shipped Stage B
        // iter-17 model is the on-policy convergence ceiling for vanilla
        // AlphaZero MCTS — direct verification showed every additional
        // training iteration regressed it. To make the engine materially
        // stronger we wrap the same value head with deeper minimax search
        // at runtime instead of training on top.
        //
        // selectMoveAdaptive iterative-deepens with a 5× safety multiplier
        // on branching, so it stops before busting the time budget. With
        // policy-ordered alpha-beta cuts on Mancala's branching ≈6 and the
        // TT cache amortizing repeated positions, depth 7-8 is comfortably
        // reachable inside the 5s desktop budget.
        //
        // evaluateBoard uses 0.7 * neuralValue + 0.3 * heuristicValue at
        // leaves (NeuralNetEngine.evaluateBoard), so the trained value head
        // still drives the search — alpha-beta is just a stronger backup
        // operator than the on-policy MCTS that produced iter-17's targets.
        MancalaGame(
            aiMode = AiMode.AlphaBeta(
                timeBudgetMs = 5000L,
                maxDepth = 8,
            ),
        )
    }
}
