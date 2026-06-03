package io.github.ganyuke.peoplehunt.core

import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.events.models.Pos4
import io.github.ganyuke.peoplehunt.core.testutil.player
import io.github.ganyuke.peoplehunt.core.utils.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class UtilsTest {
    @Test
    fun formatElapsed_formatsHoursMinutesSeconds() {
        assertEquals("00:00:00", formatElapsed(0))
        assertEquals("00:01:05", formatElapsed(65))
        assertEquals("01:02:03", formatElapsed(3723))
        assertEquals("02:30:45", formatElapsed(9045))
    }

    @Test
    fun isReally_comparesByUuid() {
        val uuid = Uuid.random()
        val a = MatchPlayer(uuid, "a")
        val b = MatchPlayer(uuid, "b")
        val other = player("c")

        assertTrue(a isReally b)
        assertFalse(a isReally other)
        assertTrue(null isReally null)
        assertFalse(a isReally null)
    }

    @Test
    fun isNotReally_isNegationOfIsReally() {
        val a = player("a")
        val b = player("b")
        assertTrue(a isNotReally b)
        assertFalse(a isNotReally a)
    }

    @Test
    fun pos4_supportsDataClassSemantics() {
        val world = Uuid.random()
        val a = Pos4(1, 2, 3, world)
        val b = Pos4(1, 2, 3, world)
        assertEquals(a, b)
        assertEquals(a, a.copy())
        assertEquals(world, a.w)
        a.hashCode()
    }

    @Test
    fun peoplehuntNamespace_isStable() {
        assertEquals("peoplehunt", PEOPLEHUNT_NAMESPACE)
    }

    @Test
    fun phConfig_storesValues() {
        val config = PhConfig(globalCompass = true, matchMinutesInterval = 10.minutes, compassTickInterval = 20)
        assertEquals(10.minutes, config.matchMinutesInterval)
    }

    @Test
    fun reallyContains_matchesUuidNotInstance() {
        val uuid = Uuid.random()
        val stored = MatchPlayer(uuid, "stored")
        val query = MatchPlayer(uuid, "query")
        assertTrue(setOf(stored) reallyContains query)
        assertFalse(setOf(player("other")) reallyContains query)
    }
}
