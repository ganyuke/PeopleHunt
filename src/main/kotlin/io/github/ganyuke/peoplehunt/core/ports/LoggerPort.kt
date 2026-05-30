package io.github.ganyuke.peoplehunt.core.ports

interface LoggerPort {
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String, cause: Throwable? = null)
}