package io.github.ganyuke.peoplehunt.core.events

import io.github.ganyuke.peoplehunt.core.testutil.player
import io.github.ganyuke.peoplehunt.core.testutil.pos
import kotlin.test.Test
import kotlin.test.assertEquals

class EventBusTest {
    @Test
    fun matchEventBus_notifiesRegisteredListeners() {
        val bus = MatchEventBus()
        val received = mutableListOf<MatchEvent>()
        val listener: MatchEventBus.MatchEventListener = { received += it }
        bus.register(listener)
        val event = MatchEvent.BroadcastNotification("hello")
        bus.post(event)
        assertEquals(1, received.size)
        assertEquals(event, received.single())
        bus.unregister(listener)
        bus.post(MatchEvent.CompassTick)
        assertEquals(1, received.size)
    }

    @Test
    fun reportableEventBus_notifiesRegisteredListeners() {
        val bus = ReportableEventBus()
        val received = mutableListOf<ReportableEvent>()
        val listener: ReportableEventBus.ReportableEventListener = { received += it }
        bus.register(listener)
        val runner = player("runner")
        // todo: update PlayerMoved to new snapshot format
        val event = ReportableEvent.PlayerMoved(runner, pos())
        bus.post(event)
        assertEquals(1, received.size)
        assertEquals(event, received.single())
        bus.unregister(listener)
        bus.post(event)
        assertEquals(1, received.size)
    }
}
