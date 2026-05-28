package io.github.ganyuke.peoplehunt.core.ports

interface TaskHandle {
    fun cancel()
}

interface SchedulerPort {
    fun everyTicks(interval: Long, task: () -> Unit): TaskHandle
    fun after(delay: Long, task: () -> Unit): TaskHandle
}
