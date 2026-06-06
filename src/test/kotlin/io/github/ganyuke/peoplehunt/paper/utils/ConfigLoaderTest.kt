package io.github.ganyuke.peoplehunt.paper.utils

import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import org.bukkit.configuration.file.YamlConfiguration

class ConfigLoaderTest {
  @Test
  fun load_readsReportFlushIntervalMinutes() {
    val yaml = YamlConfiguration()
    yaml.set("global-compass", false)
    yaml.set("match-minutes-interval", 30)
    yaml.set("compass-tick-interval", 8)
    yaml.set("report-flush-interval-minutes", 10)

    val config = ConfigLoader.load(Logger.getLogger("test"), yaml)
    assertEquals(false, config.globalCompass)
    assertEquals(30.minutes, config.matchMinutesInterval)
    assertEquals(8L, config.compassTickInterval)
    assertEquals(10.minutes, config.reportFlushInterval)
  }

  @Test
  fun load_usesDefaultReportFlushInterval() {
    val yaml = YamlConfiguration()
    val config = ConfigLoader.load(Logger.getLogger("test"), yaml)
    assertEquals(5.minutes, config.reportFlushInterval)
  }
}
