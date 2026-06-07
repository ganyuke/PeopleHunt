package io.github.ganyuke.peoplehunt.paper.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.ports.outbound.LoggerPort
import io.github.ganyuke.peoplehunt.core.ports.inbound.ReportPort
import io.github.ganyuke.peoplehunt.paper.command.CommandErrorRouter.handle
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import kotlin.uuid.Uuid

object ReportSubtree {
    private class CommandContextWrapper(
        val reportPort: ReportPort,
        val logger: LoggerPort,
        val outbound: MatchEventBus,
    )

    fun build(
        reportPort: ReportPort,
        logger: LoggerPort,
        outbound: MatchEventBus,
    ): LiteralArgumentBuilder<CommandSourceStack> {
        val ctxWrapper = CommandContextWrapper(reportPort, logger, outbound)

        val matchIdSuggestions = SuggestionProvider<CommandSourceStack> { _, builder ->
            reportPort.fetchMatchIdList().forEach {
                builder.suggest(it.toString())
            }
            builder.buildFuture()
        }

        return Commands.literal("report")
            .then(Commands.literal("flush").executes { executeFlush(it, ctxWrapper) })
            .then(Commands.literal("clear").executes { executeClear(it, ctxWrapper) })
            .then(
                Commands.literal("export").then(
                    Commands.argument("match_id", StringArgumentType.word())
                        .suggests(matchIdSuggestions)
                        .executes { executeExport(it, ctxWrapper) }
                )
            )
    }

    private fun executeFlush(ctx: CommandContext<CommandSourceStack>, env: CommandContextWrapper): Int {
        env.reportPort.manualFlush {
            handle(ctx.source, it, env.logger, env.outbound)
        }
        return 1
    }

    private fun executeClear(ctx: CommandContext<CommandSourceStack>, env: CommandContextWrapper): Int {
        val result = env.reportPort.clear()
        return handle(ctx.source, result)
    }

    private fun executeExport(ctx: CommandContext<CommandSourceStack>, env: CommandContextWrapper): Int {
        val raw = StringArgumentType.getString(ctx, "match_id")
        val matchId = Uuid.parseOrNull(raw)

        if (matchId == null) {
            ctx.source.sender.sendMessage(
                Component.text("Invalid match id: $raw", NamedTextColor.RED)
            )
            return 0
        }

        env.reportPort.export(matchId) {
            handle(ctx.source, it, env.logger, env.outbound)
        }
        return 1
    }
}