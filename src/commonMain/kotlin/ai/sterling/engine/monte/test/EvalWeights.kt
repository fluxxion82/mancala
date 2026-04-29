package ai.sterling.engine.monte.test

data class EvalWeights(
    val scoringMove: Double = 6110.773644912023, // 5133.355397607918,
    val captureMove: Double = 1001.3035221078235, // 2394.2192738520253,
    val captureStoneValue: Double = 334.2387177411762, // 112.4413953998069,
    val blockCapture: Double = 4030.1555199060695, // 2053.9169685861925,
    val rightmostBase: Double = 2853.340471978067, // 972.7127932091412,
    val rightmostDecay: Double = 133.57048605207873, // 105.28510802292752,
)
