package ai.sterling.engine.ml

import ai.sterling.model.Board
import ai.sterling.model.Game
import ai.sterling.model.Game.GameStatus
import ai.sterling.util.MancalaDebug
import kotlin.math.sqrt
import kotlin.time.TimeSource
import kotlinx.coroutines.yield

/**
 * AlphaZero-style PUCT Monte Carlo Tree Search.
 *
 * Uses the policy network as priors and the value head at leaves, with no random
 * rollouts. Search is guided by:
 *   PUCT(a) = Q(a) + c_puct * P(a) * sqrt(parentVisits + 1) / (1 + N(a))
 *
 * Mancala-specific wrinkle: when a move ends in the player's own mancala the same
 * player keeps the turn. The parent and child nodes share their mover's POV, so
 * the value does NOT get negated on backup across that edge. We track this per-edge
 * via the flip bitmap [pathFlip] and only negate when [PuctNode.isPlayerOne] differs
 * between the two endpoints.
 *
 * Inference-time defaults: no Dirichlet noise, no temperature — pick the action with
 * the highest visit count at the root. The noise/temperature knobs are training-time
 * concerns; at inference we want the deterministic strongest move.
 */
internal class PuctMcts(
    private val policyValue: (Board, Boolean) -> Pair<FloatArray, Float>,
    private val cPuct: Float = 1.5f,
    private val maxDepth: Int = 64,
) {

    /** Number of simulations completed by the most recent [search] call. */
    var lastSims: Int = 0
        private set

    /** Wall-clock duration of the most recent [search] call. */
    var lastTimeMs: Long = 0L
        private set

    /**
     * One-shot search: build a fresh root, run [search]. Convenience wrapper for
     * tests and any caller that doesn't want to manage tree reuse.
     */
    suspend fun selectMove(
        game: Game,
        simulations: Int = 0,
        timeBudgetMs: Long = 0L,
    ): Int = search(freshRoot(game), simulations, timeBudgetMs)

    /**
     * Build a brand-new root node for [game] — populate the legal-move mask and run
     * one expansion (policy + value lookup) so the root is ready to descend from.
     */
    fun freshRoot(game: Game): PuctNode {
        val isP1 = when (game.status) {
            GameStatus.PlayerOneTurn -> true
            GameStatus.PlayerTwoTurn -> false
            else -> error("freshRoot called on a finished game (status=${game.status})")
        }
        val root = PuctNode(game = game, isPlayerOne = isP1, terminalValue = null)
        populateLegalMask(root)
        expand(root) // discard root value — it never gets backed up to a "parent"
        return root
    }

    /**
     * Walk the cached subtree under [prevRoot] looking for a node whose `(board,
     * mover)` matches [game]. Used for tree reuse across moves: after the AI plays
     * and the opponent responds, the new root is one or two plies below the old
     * one — usually a direct child. Returns `null` if no match within [MAX_REUSE_DEPTH].
     */
    fun locateOrAdvance(prevRoot: PuctNode, game: Game): PuctNode? {
        val targetIsP1 = when (game.status) {
            GameStatus.PlayerOneTurn -> true
            GameStatus.PlayerTwoTurn -> false
            else -> return null // game over; nothing to search
        }
        if (matches(prevRoot, game.board, targetIsP1)) return prevRoot

        // BFS bounded by MAX_REUSE_DEPTH. Mancala chains can stack a few same-player
        // plies; depth 6 is loose enough to cover anything realistic.
        var frontier: List<PuctNode> = listOf(prevRoot)
        repeat(MAX_REUSE_DEPTH) {
            val next = mutableListOf<PuctNode>()
            for (node in frontier) {
                for (child in node.children) {
                    if (child == null) continue
                    if (matches(child, game.board, targetIsP1)) return child
                    next.add(child)
                }
            }
            if (next.isEmpty()) return null
            frontier = next
        }
        return null
    }

    /**
     * Run the simulation loop on [root] until either [simulations] sims have been
     * completed or [timeBudgetMs] has elapsed (whichever fires first). When both
     * are 0, defaults to 200 sims. Returns the absolute pocket index of the chosen
     * move.
     */
    suspend fun search(
        root: PuctNode,
        simulations: Int = 0,
        timeBudgetMs: Long = 0L,
    ): Int {
        // Pre-allocated workspace for the descent path. Reused across simulations.
        val pathNodes = arrayOfNulls<PuctNode>(maxDepth + 1)
        val pathActions = IntArray(maxDepth + 1)
        val pathFlip = BooleanArray(maxDepth + 1)

        val effectiveSims = when {
            simulations > 0 -> simulations
            timeBudgetMs > 0L -> Int.MAX_VALUE
            else -> 200
        }
        val mark = TimeSource.Monotonic.markNow()
        var simsRun = 0

        while (simsRun < effectiveSims) {
            if (timeBudgetMs > 0L && mark.elapsedNow().inWholeMilliseconds >= timeBudgetMs) break
            // If the root itself is proven, no amount of searching can change the answer.
            if (root.provenValue != null) break

            var pathLen = 0
            var node = root
            var leafValue = 0f

            descent@ while (true) {
                val a = pickAction(node)
                if (a < 0) {
                    leafValue = 0f
                    break
                }
                val child = node.children[a]
                    ?: createChild(node, a).also { node.children[a] = it }

                pathNodes[pathLen] = node
                pathActions[pathLen] = a
                pathFlip[pathLen] = (node.isPlayerOne != child.isPlayerOne)
                pathLen++

                val terminal = child.terminalValue
                if (terminal != null) {
                    leafValue = terminal
                    break
                }
                if (!child.isExpanded) {
                    leafValue = expand(child)
                    break
                }
                if (pathLen >= maxDepth) {
                    // Safety cap — extremely deep mancala-chain in self-play. Treat as 0.
                    leafValue = 0f
                    break
                }
                node = child
            }

            // Backup: walk up the recorded path, flipping value when crossing player
            // boundaries. Same-player edges (mancala chains) leave the value untouched.
            var v = leafValue
            for (i in pathLen - 1 downTo 0) {
                if (pathFlip[i]) v = -v
                val parent = pathNodes[i]!!
                val a = pathActions[i]
                parent.n[a] += 1
                parent.w[a] += v
                parent.visitCount += 1
            }

            // MCTS-Solver: walk up again, marking parents proven when their outcomes
            // are determined by their children's proofs. Stops at the first parent
            // that can't yet be proven — ancestors above must wait for further sims.
            for (i in pathLen - 1 downTo 0) {
                val parent = pathNodes[i]!!
                val a = pathActions[i]
                val child = parent.children[a] ?: break
                if (child.provenValue == null) break
                tryProveParent(parent)
                if (parent.provenValue == null) break
            }

            simsRun++
            // Cooperative yield every 16 simulations so the browser event loop can
            // run scroll handlers, animation frames, and pointer events while we think.
            if ((simsRun and 0xF) == 0) yield()
        }

        val elapsedMs = mark.elapsedNow().inWholeMilliseconds
        lastSims = simsRun
        lastTimeMs = elapsedMs
        MancalaDebug.log { "[mancala] PUCT MCTS sims=$simsRun time=${elapsedMs}ms" }

        // Final move selection. If there's a proven-win child, take it immediately —
        // we already know it forces a win. Otherwise, fall back to visit-count argmax.
        var bestRel = pickProvenWinChild(root)
        if (bestRel < 0) {
            var bestVisits = -1
            for (rel in 0 until 6) {
                if (!root.legalRel[rel]) continue
                if (root.n[rel] > bestVisits) {
                    bestVisits = root.n[rel]
                    bestRel = rel
                }
            }
        }
        if (bestRel < 0) {
            // No move was visited (e.g. simulations cap of 0). Fall back to argmax prior.
            var bestPrior = -1f
            for (rel in 0 until 6) {
                if (root.legalRel[rel] && root.prior[rel] > bestPrior) {
                    bestPrior = root.prior[rel]
                    bestRel = rel
                }
            }
        }
        if (bestRel < 0) bestRel = 0 // ultimate fallback; should be unreachable

        return if (root.isPlayerOne) bestRel else bestRel + 7
    }

    /** Returns the relative action of any proven-win child of [node], or -1 if none. */
    private fun pickProvenWinChild(node: PuctNode): Int {
        for (rel in 0 until 6) {
            if (!node.legalRel[rel]) continue
            val child = node.children[rel] ?: continue
            val cv = child.provenValue ?: continue
            val asMover = if (node.isPlayerOne != child.isPlayerOne) -cv else cv
            if (asMover >= 1f - 1e-6f) return rel
        }
        return -1
    }

    /**
     * If every legal child of [parent] has a proven value, mark [parent] as proven
     * with the best-for-parent outcome. Otherwise: if any proven child IS a forced
     * win for parent's mover, mark parent proven-win even without exhaustive search
     * (parent's mover would just play that move).
     */
    private fun tryProveParent(parent: PuctNode) {
        // Quick win check.
        var anyUnproven = false
        var bestForParent = -2f // worse than -1; gets overwritten on first child
        for (rel in 0 until 6) {
            if (!parent.legalRel[rel]) continue
            val child = parent.children[rel]
            if (child == null) {
                anyUnproven = true
                continue
            }
            val cv = child.provenValue
            if (cv == null) {
                anyUnproven = true
                continue
            }
            val asParent = if (parent.isPlayerOne != child.isPlayerOne) -cv else cv
            if (asParent >= 1f - 1e-6f) {
                parent.provenValue = 1f
                return
            }
            if (asParent > bestForParent) bestForParent = asParent
        }
        // Only proven if every legal child was proven AND none was a win (handled above).
        if (!anyUnproven && bestForParent > -2f) {
            parent.provenValue = bestForParent
        }
    }

    private fun matches(node: PuctNode, board: Board, isPlayerOne: Boolean): Boolean {
        if (node.isPlayerOne != isPlayerOne) return false
        val nodePockets = node.game.board.pockets
        val targetPockets = board.pockets
        for (i in 0 until 14) {
            if (nodePockets[i] != targetPockets[i]) return false
        }
        return true
    }

    companion object {
        /** Cap on `locateOrAdvance` BFS depth. Mancala chains rarely stack >3 plies. */
        private const val MAX_REUSE_DEPTH = 6
    }

    private fun populateLegalMask(node: PuctNode) {
        val legal = node.game.board.legalMoves(node.isPlayerOne)
        for (m in legal) {
            val rel = if (node.isPlayerOne) m else m - 7
            if (rel in 0..5) node.legalRel[rel] = true
        }
    }

    private fun pickAction(node: PuctNode): Int {
        // Fast path: any proven-win child for our mover wins the search. No reason
        // to keep exploring a position that's already solved.
        val provenWin = pickProvenWinChild(node)
        if (provenWin >= 0) return provenWin

        val sqrtParentN = sqrt((node.visitCount + 1).toFloat())
        var bestRel = -1
        var bestScore = Float.NEGATIVE_INFINITY
        for (rel in 0 until 6) {
            if (!node.legalRel[rel]) continue
            // Skip proven-loss children — sunk cost; spending sims on them is waste.
            val child = node.children[rel]
            if (child?.provenValue != null) {
                val cv = child.provenValue!!
                val asMover = if (node.isPlayerOne != child.isPlayerOne) -cv else cv
                if (asMover <= -1f + 1e-6f) continue
            }
            val nVisits = node.n[rel]
            val q = if (nVisits == 0) 0f else node.w[rel] / nVisits
            val u = cPuct * node.prior[rel] * sqrtParentN / (1 + nVisits)
            val score = q + u
            if (score > bestScore) {
                bestScore = score
                bestRel = rel
            }
        }
        return bestRel
    }

    private fun expand(node: PuctNode): Float {
        val (priors, value) = policyValue(node.game.board, node.isPlayerOne)
        for (i in 0 until 6) node.prior[i] = priors[i]
        node.isExpanded = true
        return value
    }

    private fun createChild(parent: PuctNode, relAction: Int): PuctNode {
        val absMove = if (parent.isPlayerOne) relAction else relAction + 7
        val result = parent.game.board.playMove(absMove, parent.isPlayerOne)
        val gameOver = result.board.isGameOver()
        val childIsP1 = if (result.endsInMancala) parent.isPlayerOne else !parent.isPlayerOne

        val childStatus: GameStatus = when {
            gameOver -> when {
                result.board.playerOne.mancala > result.board.playerTwo.mancala -> GameStatus.Finished.PlayerOneWin
                result.board.playerOne.mancala < result.board.playerTwo.mancala -> GameStatus.Finished.PlayerTwoWin
                else -> GameStatus.Finished.Draw
            }
            childIsP1 -> GameStatus.PlayerOneTurn
            else -> GameStatus.PlayerTwoTurn
        }

        val terminalValue: Float? = if (gameOver) {
            // Outcome from the (notional) child mover's POV.
            val signFromP1 = when {
                result.board.playerOne.mancala > result.board.playerTwo.mancala -> 1f
                result.board.playerOne.mancala < result.board.playerTwo.mancala -> -1f
                else -> 0f
            }
            if (childIsP1) signFromP1 else -signFromP1
        } else {
            null
        }

        val childGame = Game(board = result.board, status = childStatus)
        val child = PuctNode(game = childGame, isPlayerOne = childIsP1, terminalValue = terminalValue)
        if (terminalValue == null) {
            populateLegalMask(child)
        } else {
            // Terminal nodes are immediately proven — their value is the game's outcome
            // and never changes with further search.
            child.provenValue = terminalValue
        }
        return child
    }
}

internal class PuctNode(
    val game: Game,
    val isPlayerOne: Boolean,
    /** Outcome value from this node's mover POV when the game is over here, else null. */
    val terminalValue: Float?,
) {
    val prior = FloatArray(6)
    val n = IntArray(6)
    val w = FloatArray(6)
    val children = arrayOfNulls<PuctNode>(6)
    val legalRel = BooleanArray(6)
    var isExpanded = false
    var visitCount = 0

    /**
     * Once known, the exact value of this node from its mover's POV: -1 = forced
     * loss, 0 = forced draw, +1 = forced win. `null` until the MCTS-Solver pass
     * proves the node — terminal nodes are proven at construction (see [createChild]
     * in PuctMcts), and internal nodes become proven once enough of their children
     * are proven that the outcome is determined regardless of further search.
     *
     * Selection uses this to short-circuit search: a proven-win child is taken
     * unconditionally, and proven-loss children are skipped so we don't waste sims
     * on sunk-cost branches.
     */
    var provenValue: Float? = null
}
