package io.github.ganyuke.peoplehunt.paper.command.report

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.suggestion.SuggestionProvider
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.ports.LoggerPort
import io.github.ganyuke.peoplehunt.core.ports.SchedulerPort
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.ReportInboundPort
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.ReportOpResult
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.fromCompactString
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.toCompactString
import io.github.ganyuke.peoplehunt.core.utils.PEOPLEHUNT_NAMESPACE
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

object ReportCommand {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun attach(
        root: LiteralArgumentBuilder<CommandSourceStack>,
        reportPort: ReportInboundPort,
        scheduler: SchedulerPort,
        logger: LoggerPort,
        outbound: MatchEventBus,
    ) {
        val frontmanPerm = "$PEOPLEHUNT_NAMESPACE.frontman"
        val matchIdSuggestions = SuggestionProvider<CommandSourceStack> { _, builder ->
            reportPort.listExportableMatchIds().forEach { id ->
                builder.suggest(id.toCompactString())
            }
            builder.buildFuture()
        }

        root.then(
            Commands.literal("report")
                .requires { it.sender.hasPermission(frontmanPerm) }
                .then(Commands.literal("flush").executes { ctx ->
                    runAsync(reportPort, scheduler, logger, outbound, ctx.source) { reportPort.manualFlush() }
                    1
                })
                .then(Commands.literal("clear").executes { ctx ->
                    val result = reportPort.clear()
                    scheduler.runOnMainThread {
                        ReportCommandFeedback.deliver(ctx.source, logger, outbound, result)
                    }
                    if (result is ReportOpResult.Ok) 1 else 0
                })
                .then(
                    Commands.literal("export")
                        .then(
                            Commands.argument("match_id", StringArgumentType.word())
                                .suggests(matchIdSuggestions)
                                .executes { ctx ->
                                    val raw = StringArgumentType.getString(ctx, "match_id")
                                    val matchId = parseMatchId(raw)
                                    if (matchId == null) {
                                        scheduler.runOnMainThread {
                                            ctx.source.sender.sendMessage(
                                                Component.text("Invalid match id: $raw", NamedTextColor.RED),
                                            )
                                        }
                                        return@executes 0
                                    }
                                    runAsync(reportPort, scheduler, logger, outbound, ctx.source) {
                                        reportPort.export(matchId)
                                    }
                                    1
                                },
                        ),
                ),
        )
    }

    fun shutdown() = scope.cancel()

    private fun runAsync(
        reportPort: ReportInboundPort,
        scheduler: SchedulerPort,
        logger: LoggerPort,
        outbound: MatchEventBus,
        source: CommandSourceStack,
        block: suspend () -> ReportOpResult,
    ) {
        scope.launch {
            val result = block()
            scheduler.runOnMainThread {
                ReportCommandFeedback.deliver(source, logger, outbound, result)
            }
        }
    }

    private fun parseMatchId(raw: String): Uuid? = runCatching {
        if (raw.contains('-')) Uuid.parse(raw) else Uuid.fromCompactString(raw)
    }.getOrNull()
}
