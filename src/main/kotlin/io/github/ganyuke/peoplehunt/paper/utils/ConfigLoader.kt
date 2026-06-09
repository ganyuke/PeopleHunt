package io.github.ganyuke.peoplehunt.paper.utils

import io.github.ganyuke.peoplehunt.core.utils.PhConfig
import org.bukkit.configuration.file.FileConfiguration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object ConfigLoader {
    private const val GLOBAL_COMPASS_DEFAULT = true
    private const val MATCH_MINUTES_INTERVAL_DEFAULT = 60L
    private const val COMPASS_TICK_INTERVAL_DEFAULT = 4L
    private const val REPORT_FLUSH_INTERVAL_MINUTES_DEFAULT = 5L

    fun load(config: FileConfiguration): PhConfig {
        val globalCompass = config.getBoolean("global-compass", GLOBAL_COMPASS_DEFAULT)
        val matchMinutesInterval = config.getLong("match-minutes-interval", MATCH_MINUTES_INTERVAL_DEFAULT)
        val compassTickInterval = config.getLong("compass-tick-interval", COMPASS_TICK_INTERVAL_DEFAULT)
        val reportFlushInterval = config.getLong("report-flush-interval-minutes", REPORT_FLUSH_INTERVAL_MINUTES_DEFAULT)

        return PhConfig(
            globalCompass,
            matchMinutesInterval.toDuration(DurationUnit.MINUTES),
            compassTickInterval,
            reportFlushInterval.toDuration(DurationUnit.MINUTES),
        )
    }
}