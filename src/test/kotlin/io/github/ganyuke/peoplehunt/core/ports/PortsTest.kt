package io.github.ganyuke.peoplehunt.core.ports

import io.github.ganyuke.peoplehunt.core.utils.PhConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class PortsTest {
    @Test
    fun phConfig_exposesFields() {
        val config = PhConfig(globalCompass = false, matchMinutesInterval = 5.minutes, compassTickInterval = 40, flushMinutesInterval = 5.minutes)
        assertEquals(false, config.globalCompass)
        assertEquals(5.minutes, config.matchMinutesInterval)
        assertEquals(40, config.compassTickInterval)
    }
}
