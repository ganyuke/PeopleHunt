package io.github.ganyuke.peoplehunt.core.ports

import io.github.ganyuke.peoplehunt.core.Utils
import io.github.ganyuke.peoplehunt.core.testutil.pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class PortsTest {
    @Test
    fun playerSnapshot_exposesFields() {
        val world = Uuid.random()
        val location = pos(1, 2, 3, world)
        val snapshot = PlayerSnapshot(Uuid.random(), location)
        assertEquals(location, snapshot.pos)
    }

    @Test
    fun phConfig_exposesFields() {
        val config = Utils.PhConfig(globalCompass = false, matchMinutesInterval = 5.minutes, compassTickInterval = 40)
        assertEquals(false, config.globalCompass)
        assertEquals(5.minutes, config.matchMinutesInterval)
        assertEquals(40, config.compassTickInterval)
    }
}
