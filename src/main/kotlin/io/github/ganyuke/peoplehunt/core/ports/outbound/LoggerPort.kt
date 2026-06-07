package io.github.ganyuke.peoplehunt.core.ports.outbound

interface LoggerPort {
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String, cause: Throwable? = null)
}