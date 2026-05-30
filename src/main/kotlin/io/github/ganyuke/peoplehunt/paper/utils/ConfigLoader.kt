package io.github.ganyuke.peoplehunt.paper.utils

import io.github.ganyuke.peoplehunt.core.utils.PhConfig
import org.bukkit.configuration.file.FileConfiguration
import java.util.logging.Logger
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object ConfigLoader {
    private const val GLOBAL_COMPASS_DEFAULT = true
    private const val MATCH_MINUTES_INTERVAL_DEFAULT = 60L
    private const val COMPASS_TICK_INTERVAL_DEFAULT = 4L

    fun load(logger: Logger, config: FileConfiguration): PhConfig {
        val globalCompass = config.getBoolean("global-compass", GLOBAL_COMPASS_DEFAULT)
        val matchMinutesInterval = config.getLong("match-minutes-interval", MATCH_MINUTES_INTERVAL_DEFAULT)
        val compassTickInterval = config.getLong("compass-tick-interval", COMPASS_TICK_INTERVAL_DEFAULT)

        return PhConfig(globalCompass, matchMinutesInterval.toDuration(DurationUnit.MINUTES), compassTickInterval)
    }
}