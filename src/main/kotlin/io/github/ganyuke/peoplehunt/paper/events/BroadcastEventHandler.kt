package io.github.ganyuke.peoplehunt.paper.events

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.services.reporting.highlighter.EventHighlighter
import io.github.ganyuke.peoplehunt.paper.utils.MatchStatusFormatter
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit

class BroadcastEventHandler(
    private val eventHighlighter: EventHighlighter,
) {
    fun onMatchEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.MatchEnd -> {
                val stats = eventHighlighter.participantStats
                Bukkit.broadcast(MatchStatusFormatter.format(event.result, stats))
            }

            is MatchEvent.BroadcastNotification ->
                Bukkit.broadcast(Component.text(event.message, NamedTextColor.GREEN))

            is MatchEvent.OperatorNotification ->
                Bukkit.getOnlinePlayers()
                    .filter { it.isOp }
                    .forEach { it.sendMessage(Component.text(event.message, NamedTextColor.YELLOW)) }

            is MatchEvent.IntervalElapsed ->
                Bukkit.broadcast(Component.text("Manhunt time elapsed: ${event.minutes} minute(s)", NamedTextColor.YELLOW))

            else -> {}
        }
    }
}
