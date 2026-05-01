package ai.sterling.util

import ai.sterling.engine.MoveTelemetry
import ai.sterling.model.Game.GameStatus

/**
 * WasmJs/browser actual: stubbed for now. The site has no backend yet, so we
 * are not persisting played games. Once a destination is chosen (localStorage,
 * a Firebase collection, a hosted endpoint, etc.) this is the place to wire it up.
 */
actual class GameLogger actual constructor() {
    actual fun startGame(humanIsPlayerOne: Boolean) {
        // no-op
    }

    actual fun recordMove(absolutePocket: Int, wasPlayerOne: Boolean) {
        // no-op
    }

    actual fun recordAiMove(telemetry: MoveTelemetry) {
        // no-op
    }

    actual fun endGame(status: GameStatus) {
        // no-op
    }
}
