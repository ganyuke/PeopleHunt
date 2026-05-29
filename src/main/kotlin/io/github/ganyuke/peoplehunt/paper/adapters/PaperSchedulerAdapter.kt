package io.github.ganyuke.peoplehunt.paper.adapters

import org.bukkit.plugin.java.JavaPlugin
import io.github.ganyuke.peoplehunt.core.ports.SchedulerPort
import io.github.ganyuke.peoplehunt.core.ports.TaskHandle

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
}
