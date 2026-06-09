package io.github.ganyuke.peoplehunt.paper.command

import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.ports.inbound.MatchPort
import io.github.ganyuke.peoplehunt.core.ports.inbound.ReportPort
import io.github.ganyuke.peoplehunt.core.ports.outbound.LoggerPort
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchState
import io.github.ganyuke.peoplehunt.core.utils.PEOPLEHUNT_NAMESPACE
import io.github.ganyuke.peoplehunt.paper.items.HunterCompass
import io.github.ganyuke.peoplehunt.paper.utils.MatchStatusFormatter
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player

object PhCommand {
    // i'm sure there is a better way to do this so I'm not tossing this in this class
    // but this is quick and easy so I'm leaving it here
    private fun giveHunterCompass(source: CommandSourceStack): Int {
        val player = source.sender as? Player
            ?: return CommandErrorRouter.fail(source, CommandErrorRouter.CommandFailure.MustBePlayer)

        player.give(HunterCompass.create())
        player.sendMessage(Component.text("Gave self a Hunter Compass."))
        return 1
    }

    fun handleLastStatus(source: CommandSourceStack, matchPort: MatchPort, reportPort: ReportPort): Int {
        val lastResult =
            matchPort.lastMatchResult ?: return CommandErrorRouter.fail(source, CommandErrorRouter.CommandFailure.NoLastMatch)
        val stats = reportPort.participantStats
        source.sender.sendMessage(MatchStatusFormatter.format(lastResult, stats))
        return 1
    }

    fun handleStatus(source: CommandSourceStack, matchPort: MatchPort, reportPort: ReportPort): Int {
        val status = matchPort.currentStatus
        val stats = if (status is MatchState.Finished) reportPort.participantStats else null
        source.sender.sendMessage(MatchStatusFormatter.format(matchPort.currentStatus, stats))
        return 1
    }

    private fun getOnlinePlayers() = Bukkit.getOnlinePlayers().map { it.toMatchPlayer() }

    private fun guardSession(port: ReportPort, source: CommandSourceStack): Int =
        CommandErrorRouter.handle(source, port.blockReason)

    /**
     * Registers the declarative command tree with Brigadier.
     */
    fun buildMatchCommand(
        matchPort: MatchPort,
        reportPort: ReportPort,
        logger: LoggerPort,
        outbound: MatchEventBus,
    ): LiteralCommandNode<CommandSourceStack> {
        val rootNode = Commands.literal("ph")
        val frontmanPerm = "${PEOPLEHUNT_NAMESPACE}.frontman"

        fun <T : ArgumentBuilder<CommandSourceStack, T>> T.requiresFrontman(): T {
            return this.requires { it.sender.hasPermission(frontmanPerm) }
        }

        rootNode.apply {
            // user-level commands
            then(Commands.literal("compass").executes { ctx -> giveHunterCompass(ctx.source) })
            then(
                Commands.literal("status").executes { ctx -> handleStatus(ctx.source, matchPort, reportPort) }
                .then(
                    Commands.literal("last").executes { ctx ->
                        handleLastStatus(ctx.source, matchPort, reportPort)
                    }
                ))

            // match administration commands
            then(
                Commands.literal("prime").requiresFrontman()
                    .executes { ctx ->
                        guardSession(reportPort, ctx.source).let { resultCode ->
                            if (resultCode != 1) return@executes resultCode
                        }

                        CommandErrorRouter.handle(ctx.source, matchPort.prime(getOnlinePlayers()))
                    })
            then(
                Commands.literal("start").requiresFrontman()
                    .executes { ctx ->
                        guardSession(reportPort, ctx.source).let { resultCode ->
                            if (resultCode != 1) return@executes resultCode
                        }
                        CommandErrorRouter.handle(ctx.source, matchPort.forceStart(getOnlinePlayers()))
                    })
            then(
                Commands.literal("end").requiresFrontman()
                    .executes { ctx -> CommandErrorRouter.handle(ctx.source, matchPort.forceEnd()) })
            then(
                ParticipantSubtree.build(matchPort, ParticipantSubtree.GroupNames.RUNNER)
                    .requiresFrontman()
            )
            then(
                ParticipantSubtree.build(matchPort, ParticipantSubtree.GroupNames.HUNTER)
                    .requiresFrontman()
            )
            then(ReportSubtree.build(reportPort, logger, outbound).requiresFrontman())
        }

        return rootNode.build()
    }


}