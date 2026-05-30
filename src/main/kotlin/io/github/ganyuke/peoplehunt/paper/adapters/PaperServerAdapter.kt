package io.github.ganyuke.peoplehunt.paper.adapters

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import io.github.ganyuke.peoplehunt.core.Utils.formatElapsed
import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.services.MatchEngine
import io.github.ganyuke.peoplehunt.core.services.ReportingEngine
import io.github.ganyuke.peoplehunt.paper.utils.MatchStatusFormatter
import net.kyori.adventure.text.format.NamedTextColor

class PaperServerAdapter(
    private val reportingEngine: ReportingEngine,
) {
    fun onMatchEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.MatchEnd -> {
                val stats = reportingEngine.getParticipantStats()
                Bukkit.getOnlinePlayers().forEach {
                    it.sendMessage(MatchStatusFormatter.format(event.result, stats))
                }
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
