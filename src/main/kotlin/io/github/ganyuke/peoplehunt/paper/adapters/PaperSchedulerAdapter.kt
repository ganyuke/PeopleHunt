package io.github.ganyuke.peoplehunt.paper.adapters

import io.github.ganyuke.peoplehunt.core.ports.outbound.SchedulerPort
import io.github.ganyuke.peoplehunt.core.ports.outbound.TaskHandle
import org.bukkit.plugin.java.JavaPlugin

class PaperSchedulerAdapter(private val plugin: JavaPlugin) : SchedulerPort {
    override fun everyTicks(interval: Long, delay: Long, task: () -> Unit): TaskHandle {
        val handle = plugin.server.scheduler.runTaskTimer(plugin, Runnable { task() }, delay, interval)
        return object : TaskHandle {
            override fun cancel() = handle.cancel()
        }
    }

    override fun after(delay: Long, task: () -> Unit): TaskHandle {
        val handle = plugin.server.scheduler.runTaskLater(plugin, Runnable { task() }, delay)
        return object : TaskHandle {
            override fun cancel() = handle.cancel()
        }
    }

    override fun runOnMainThread(task: () -> Unit) {
        plugin.server.scheduler.runTask(plugin, Runnable { task() })
    }
}
