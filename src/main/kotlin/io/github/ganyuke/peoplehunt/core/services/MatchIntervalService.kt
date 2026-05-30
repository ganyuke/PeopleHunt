package io.github.ganyuke.peoplehunt.core.services

import io.github.ganyuke.peoplehunt.core.Utils
import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.ports.SchedulerPort
import io.github.ganyuke.peoplehunt.core.ports.TaskHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class MatchIntervalService(
    private val config: Utils.PhConfig,
    private val scheduler: SchedulerPort,
    private val outbound: MatchEventBus,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(matchStartTime: () -> Instant): TaskHandle? {
        if (config.matchMinutesInterval == Duration.ZERO) return null

        val job = scope.launch {
            while (true) {
                delay(config.matchMinutesInterval)
                // attempt to work around the off-by-one (-1 second) issue with the scheduler
                val elapsedMillis = (Clock.System.now() - matchStartTime()).inWholeMilliseconds
                val minutes = (elapsedMillis / 60000.0).roundToLong()

                scheduler.runOnMainThread {
                    outbound.post(MatchEvent.IntervalElapsed(minutes))
                }
            }
        }

        return object : TaskHandle { override fun cancel() { job.cancel() } }
    }

    fun shutdown() = scope.cancel()
}