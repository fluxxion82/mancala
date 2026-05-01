package ai.sterling.util

/**
 * Single switch for the AI/UI verbose logging that's useful while iterating but
 * spammy during normal play. Flip [enabled] to true to surface the per-move sims/
 * time/TT/event prints. Errors and `printStackTrace` calls are not gated by this.
 */
internal object MancalaDebug {
    var enabled: Boolean = false

    inline fun log(message: () -> String) {
        if (enabled) println(message())
    }
}
