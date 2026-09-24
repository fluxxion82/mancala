package ai.sterling.loading

sealed interface WeightLoadingState {
    data object Idle : WeightLoadingState
    data object Checking : WeightLoadingState
    data class Downloading(val received: Long, val total: Long?) : WeightLoadingState
    data object Decompressing : WeightLoadingState
    data object Initializing : WeightLoadingState
    data object Ready : WeightLoadingState
    data class Error(val cause: Throwable) : WeightLoadingState
}
