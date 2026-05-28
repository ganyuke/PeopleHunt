package io.github.ganyuke.peoplehunt.paper.adapters

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import io.github.ganyuke.peoplehunt.core.Utils.formatElapsed
import io.github.ganyuke.peoplehunt.core.events.MatchEvent

class PaperServerAdapter {
    fun onMatchEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.BroadcastNotification ->
                Bukkit.broadcast(Component.text(event.message))
            is MatchEvent.OperatorNotification ->
                Bukkit.getOnlinePlayers()
                    .filter { it.isOp }
                    .forEach { it.sendMessage(Component.text(event.message)) }
            is MatchEvent.IntervalElapsed ->
                Bukkit.broadcast(Component.text("Elapsed: ${formatElapsed(event.elapsedSeconds)}"))
            else -> {}
        }
    }
}
