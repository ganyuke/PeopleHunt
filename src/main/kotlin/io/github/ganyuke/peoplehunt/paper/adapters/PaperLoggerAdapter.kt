package io.github.ganyuke.peoplehunt.paper.adapters

import io.github.ganyuke.peoplehunt.core.ports.outbound.LoggerPort
import org.bukkit.plugin.java.JavaPlugin

class PaperLoggerAdapter(private val plugin: JavaPlugin) : LoggerPort {
    override fun info(message: String) = plugin.logger.info(message)
    override fun warn(message: String) = plugin.logger.warning(message)
    override fun error(message: String, cause: Throwable?) {
        plugin.logger.severe(message)
        cause?.let { plugin.logger.severe(it.stackTraceToString()) }
    }
}