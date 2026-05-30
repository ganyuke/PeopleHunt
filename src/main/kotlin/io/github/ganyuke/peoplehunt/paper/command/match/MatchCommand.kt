package io.github.ganyuke.peoplehunt.paper.command.match

import com.mojang.brigadier.tree.LiteralCommandNode
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine.MatchState
import io.github.ganyuke.peoplehunt.core.services.reporting.ReportingEngine
import io.github.ganyuke.peoplehunt.core.utils.PEOPLEHUNT_NAMESPACE
import io.github.ganyuke.peoplehunt.paper.items.HunterCompass
import io.github.ganyuke.peoplehunt.paper.utils.MatchStatusFormatter
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player

object MatchCommand {
    // i'm sure there is a better way to do this so I'm not tossing this in this class
    // but this is quick and easy so I'm leaving it here
    private fun giveHunterCompass(source: CommandSourceStack): Int {
        val player = source.sender as? Player
            ?: return CommandErrors.fail(source, CommandErrors.CommandFailure.MustBePlayer)

        player.give(HunterCompass.create())
        player.sendMessage(Component.text("Gave self a Hunter Compass."))
        return 1
    }

    fun handleLastStatus(source: CommandSourceStack, matchEngine: MatchEngine, reportingEngine: ReportingEngine): Int {
        val lastResult = matchEngine.lastMatchResult ?: return CommandErrors.fail(source, CommandErrors.CommandFailure.NoLastMatch)
        val stats = reportingEngine.participantStats
        source.sender.sendMessage(MatchStatusFormatter.format(lastResult, stats))
        return 1
    }

    fun handleStatus(source: CommandSourceStack, matchEngine: MatchEngine, reportingEngine: ReportingEngine): Int {
        val status = matchEngine.currentStatus
        val stats = if (status is MatchState.Finished) reportingEngine.participantStats else null
        source.sender.sendMessage(MatchStatusFormatter.format(matchEngine.currentStatus, stats))
        return 1
    }

    private fun getOnlinePlayers() = Bukkit.getOnlinePlayers().map { it.toMatchPlayer() }

    /**
     * Registers the declarative command tree with Brigadier.
     */
    fun buildMatchCommand(matchEngine: MatchEngine, reportingEngine: ReportingEngine): LiteralCommandNode<CommandSourceStack> {
        val rootNode = Commands.literal("ph")
        val frontmanPerm = "$PEOPLEHUNT_NAMESPACE.frontman"

        rootNode.apply {
            // user-level commands
            then(Commands.literal("compass").executes { ctx -> giveHunterCompass(ctx.source) })
            then(Commands.literal("status").executes { ctx -> handleStatus(ctx.source, matchEngine, reportingEngine) }.then(
                Commands.literal("last").executes {
                    ctx -> handleLastStatus(ctx.source, matchEngine, reportingEngine)
                }
            ))

            // match administration commands
            then(Commands.literal("prime")
                .requires { it.sender.hasPermission(frontmanPerm) }
                .executes { ctx -> CommandErrors.handle(ctx.source, matchEngine.prime(getOnlinePlayers())) })
            then(Commands.literal("start")
                .requires { it.sender.hasPermission(frontmanPerm) }
                .executes { ctx -> CommandErrors.handle(ctx.source, matchEngine.forceStart(getOnlinePlayers())) })
            then(Commands.literal("end")
                .requires { it.sender.hasPermission(frontmanPerm) }
                .executes { ctx -> CommandErrors.handle(ctx.source, matchEngine.forceEnd()) })
            then(
                ParticipantSubtree.participantSubtreeBuilder(matchEngine, ParticipantSubtree.GroupNames.RUNNER)
                .requires { it.sender.hasPermission(frontmanPerm) }
            )
            then(
                ParticipantSubtree.participantSubtreeBuilder(matchEngine, ParticipantSubtree.GroupNames.HUNTER)
                .requires { it.sender.hasPermission(frontmanPerm) }
            )
        }

        return rootNode.build()
    }


}