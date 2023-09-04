package ai.sterling

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

abstract class BaseViewModel(parentJob: Job? = null): CoroutineScope {
    protected val job = SupervisorJob(parentJob)

    override val coroutineContext: CoroutineContext
        get() = job

    open fun onCleared() {
        job.cancel()
    }
}
