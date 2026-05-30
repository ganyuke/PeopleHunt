package io.github.ganyuke.peoplehunt.core.testutil

import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.events.models.Pos4
import io.github.ganyuke.peoplehunt.core.ports.LoggerPort
import io.github.ganyuke.peoplehunt.core.ports.SchedulerPort
import io.github.ganyuke.peoplehunt.core.ports.TaskHandle
import io.github.ganyuke.peoplehunt.core.utils.PhConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

fun testPhConfig(
    globalCompass: Boolean = true,
    matchMinutesInterval: Duration = Duration.ZERO,
    compassTickInterval: Long = 20L,
) = PhConfig(globalCompass, matchMinutesInterval, compassTickInterval)

fun testPhConfigWithInterval(minutes: Long = 1L) =
    testPhConfig(matchMinutesInterval = minutes.minutes)

fun player(name: String = "player", uuid: Uuid = Uuid.random()) =
    MatchPlayer(uuid, name)

fun pos(x: Int = 0, y: Int = 64, z: Int = 0, world: Uuid = Uuid.random()) =
    Pos4(x, y, z, world)

class FakeScheduler : SchedulerPort {
    data class ScheduledEvery(val interval: Long, val delay: Long, val task: () -> Unit, val handle: CancellableHandle)
    data class ScheduledAfter(val delay: Long, val task: () -> Unit, val handle: CancellableHandle)

    val everyTickTasks = mutableListOf<ScheduledEvery>()
    val afterTasks = mutableListOf<ScheduledAfter>()
    val mainThreadTasks = mutableListOf<() -> Unit>()
    var cancelledHandles = mutableListOf<CancellableHandle>()

    override fun everyTicks(interval: Long, delay: Long, task: () -> Unit): TaskHandle {
        val handle = CancellableHandle()
        everyTickTasks += ScheduledEvery(interval, delay, task, handle)
        return handle
    }

    override fun after(delay: Long, task: () -> Unit): TaskHandle {
        val handle = CancellableHandle()
        afterTasks += ScheduledAfter(delay, task, handle)
        return handle
    }

    override fun runOnMainThread(task: () -> Unit) {
        mainThreadTasks += task
        task()
    }

    fun runAllAfterTasks() {
        afterTasks.filterNot { it.handle.cancelled }.forEach { it.task() }
    }

    fun runAllEveryTickTasks() {
        everyTickTasks.filterNot { it.handle.cancelled }.forEach { it.task() }
    }

    class CancellableHandle : TaskHandle {
        var cancelled = false
        override fun cancel() {
            cancelled = true
        }
    }
}

class FakeLogger : LoggerPort {
    val infoMessages = mutableListOf<String>()
    val warnMessages = mutableListOf<String>()
    val errorMessages = mutableListOf<Pair<String, Throwable?>>()

    override fun info(message: String) {
        infoMessages += message
    }

    override fun warn(message: String) {
        warnMessages += message
    }

    override fun error(message: String, cause: Throwable?) {
        errorMessages += message to cause
    }
}
