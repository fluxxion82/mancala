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
        // Desktop has real threads, no UI cost from longer searches. With tree
        // reuse the second move onwards inherits the previous turn's accumulated
        // statistics, so this 5s budget is closer to "thousands of effective sims
        // by mid-game" than "5s × 50 sims/s every turn".
        //
        // cPuct=1.8 broadens exploration past the policy net's top pick (slightly
        // distrustful while the value head is small).
        //
        // neuralWeight=0.5 gives the heuristic equal weight at leaves while the
        // current value head is saturated above 0 (telemetry shows rootValue
        // never goes negative across 100+ positions in lost games — calibration
        // issue). Push back toward 0.85+ once a properly-calibrated model ships.
        //
        // openingTempPlies=4 with T=1.0 samples from the root visit-count
        // distribution for the first 4 AI moves of each game so two replays
        // of the same human moves don't produce identical games. After the
        // window the AI returns to deterministic visit-count argmax. Proven
        // wins are still always taken.
        MancalaGame(
            aiMode = AiMode.Mcts(
                timeBudgetMs = 5000L,
                cPuct = 1.8f,
                neuralWeight = 0.5f,
                openingTempPlies = 4,
                openingTemperature = 1.0f,
            ),
        )
    }
}
