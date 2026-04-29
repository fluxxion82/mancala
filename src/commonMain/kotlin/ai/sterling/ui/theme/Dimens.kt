package ai.sterling.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Dimens {
    val PitDiameter = 96.dp
    val MancalaWidth = 104.dp
    val MancalaHeight = 240.dp
    val MarbleRadius = 12.dp
    val BoardPadding = 24.dp
    val BoardCorner = 24.dp
    val PitSpacing = 12.dp
    val ArcHeight = 30.dp

    const val FlightMs = 260
    const val StaggerMs = 80L
    const val SweepStaggerMs = 50L
    const val ThinkDelayMs = 600L
    const val CapturePulseMs = 150

    val PitCountFontSize = 13.sp
    val MancalaCountFontSize = 32.sp
    val CountLabelInset = 6.dp
}

/**
 * Multiplicative scale applied to every absolute board dimension so the layout
 * can shrink to fit a constrained host (e.g. an embed inside a 800dp-wide blog).
 * BoardLayout computes this from its BoxWithConstraints and provides it; child
 * components that aren't given pre-scaled values read it directly.
 */
val LocalBoardScale = compositionLocalOf { 1f }
