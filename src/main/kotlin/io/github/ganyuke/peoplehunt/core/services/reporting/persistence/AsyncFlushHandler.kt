package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

import io.github.ganyuke.peoplehunt.core.utils.PhConfig
import io.github.ganyuke.peoplehunt.core.ports.outbound.SchedulerPort
import io.github.ganyuke.peoplehunt.core.ports.outbound.TaskHandle
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.FinalizedMetadata
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.ReportSession
import kotlinx.coroutines.*
import kotlin.time.Duration

class AsyncFlushHandler(private val scheduler: SchedulerPort, private val config: PhConfig) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(session: ReportSession, metadata: FinalizedMetadata?, callback: ((ReportOpResult) -> Unit)?): TaskHandle? {
        if (config.flushMinutesInterval == Duration.ZERO) return null

        val job = scope.launch {
            while (true) {
                delay(config.flushMinutesInterval)

            }
        }

        return object : TaskHandle { override fun cancel() { job.cancel() } }
    }

    fun shutdown() = scope.cancel()
}