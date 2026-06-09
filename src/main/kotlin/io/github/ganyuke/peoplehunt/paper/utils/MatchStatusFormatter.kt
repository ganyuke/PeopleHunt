package io.github.ganyuke.peoplehunt.paper.utils

import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchOutcome
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchState
import io.github.ganyuke.peoplehunt.core.services.reporting.highlighter.CombatStatsTracker
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant

object MatchStatusFormatter {
    private val mm = MiniMessage.miniMessage()

    fun format(status: MatchState, reportedStats: List<Pair<MatchPlayer, CombatStatsTracker.PlayerStats>>?): Component = when (status) {
        is MatchState.Idle -> buildMessage("PEOPLEHUNT STATUS") {
            line("State", "<gray><b>IDLE</b></gray>")
            line("Runner", status.runner?.name ?: "none")
            line("Hunters",
                if (status.hunters.isEmpty()) "all online players" else status.hunters.joinNames()
            )
        }
        is MatchState.Primed -> buildMessage("PEOPLEHUNT STATUS") {
            line("State", "<aqua><b>PRIMED</b></aqua>")
            line("Runner", status.runner.name)
            line("Hunters", status.hunters.joinNames())
            line("Primed at", status.primedAt.format())
        }
        is MatchState.Active -> buildMessage("PEOPLEHUNT STATUS") {
            line("State", "<green><b>ACTIVE</b></green>")
            line("Runner", status.runner.name)
            line("Hunters", status.hunters.joinNames())
            line("Started", status.startedAt.format())
            line("Elapsed", status.startedAt.elapsed())
        }
        is MatchState.Finished -> buildMessage("POST-MATCH STATS") {
            line("Result", status.outcome.colored())
            line("Runner", status.runner.name)
            line("Started", status.startedAt.format())
            line("Ended", status.endedAt.format())
            line("Duration", duration(status.startedAt, status.endedAt))
            reportedStats?.forEach { stat(it.first.name, it.second) }
        }
    }

    private fun buildMessage(title: String, block: MessageBuilder.() -> Unit): Component =
        Component.text()
            .append(mm.deserialize("<gray>———— [ <gold><b>$title</b></gold> ] ————</gray>"))
            .appendNewline()
            .append(MessageBuilder().apply(block).build())
            .appendNewline()
            .append(mm.deserialize("<gray>———————————————————————</gray>"))
            .build()

    private class MessageBuilder {
        private val lines = mutableListOf<Component>()

        fun line(label: String, value: String) {
            lines += Component.text()
                .append(mm.deserialize("<yellow>$label:</yellow> "))
                .append(mm.deserialize("<white>$value</white>"))
                .build()
        }

        fun stat(name: String, stat: CombatStatsTracker.PlayerStats) {
            lines += mm.deserialize(
                " <dark_gray>»</dark_gray> <white>${name}</white>" +
                        " <dark_gray>|</dark_gray> <red>☠ ${stat.deaths}</red>" +
                        " <aqua>⚔ ${stat.kills}</aqua>"
            )
        }

        fun build(): Component = Component.text()
            .also { builder ->
                lines.forEachIndexed { i, line ->
                    builder.append(line)
                    if (i < lines.size - 1) builder.appendNewline()
                }
            }
            .build()
    }

    private fun Set<MatchPlayer>.joinNames(): String =
        if (isEmpty()) "none" else joinToString(", ") { it.name }

    private fun Instant.format(): String =
        this.toJavaInstant()
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm:ss"))

    private fun Instant.elapsed(): String = duration(this, Clock.System.now())

    private fun duration(from: Instant, to: Instant): String {
        val seconds = (to - from).inWholeSeconds
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun MatchOutcome.colored(): String = when (this) {
        MatchOutcome.HUNTER_VICTORY -> "<red><b>Hunter Victory</b></red>"
        MatchOutcome.RUNNER_VICTORY -> "<green><b>Runner Victory</b></green>"
        MatchOutcome.INCONCLUSIVE   -> "<yellow><b>Inconclusive</b></yellow>"
    }
}