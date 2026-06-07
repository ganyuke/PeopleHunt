package io.github.ganyuke.peoplehunt.paper.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.ports.inbound.MatchPort
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchFailureReason
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchResult
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import kotlin.uuid.toKotlinUuid

object ParticipantSubtree {
    enum class GroupNames(val commandName: String) {
        RUNNER("runner") {
            override fun clear(matchPort: MatchPort): MatchResult = matchPort.clearRunner()

            override fun getCandidates(matchPort: MatchPort): Collection<MatchPlayer> =
                listOfNotNull(matchPort.currentStatus.runner)

            override fun remove(matchPort: MatchPort, player: MatchPlayer): MatchResult =
                matchPort.removeRunner(player)

            override fun buildAddSubtree(
                builder: LiteralArgumentBuilder<CommandSourceStack>,
                matchPort: MatchPort
            ): LiteralArgumentBuilder<CommandSourceStack> =
                builder.then(Commands.argument("player", ArgumentTypes.player()).executes { ctx ->
                    val targets =
                        ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java).resolve(ctx.source)

                    when {
                        targets.isEmpty() -> {
                            return@executes CommandErrorRouter.fail(
                                ctx.source,
                                CommandErrorRouter.CommandFailure.NoEligibleTargets
                            )
                        }

                        targets.size > 1 -> {
                            return@executes CommandErrorRouter.fail(ctx.source, CommandErrorRouter.CommandFailure.TooManyRunners)
                        }

                        targets.first().uniqueId.toKotlinUuid() in matchPort.currentStatus.hunters.map { it.uuid } -> {
                            return@executes CommandErrorRouter.fail(ctx.source, MatchFailureReason.PLAYER_ALREADY_HUNTER)
                        }

                        targets.first().uniqueId.toKotlinUuid() == matchPort.currentStatus.runner?.uuid -> {
                            return@executes CommandErrorRouter.fail(ctx.source, MatchFailureReason.PLAYER_ALREADY_RUNNER)
                        }
                    }
                    return@executes CommandErrorRouter.handle(ctx.source, matchPort.setRunner(targets.first().toMatchPlayer()))
                })
        },

        HUNTER("hunter") {
            override fun clear(matchPort: MatchPort): MatchResult = matchPort.clearHunters()

            override fun getCandidates(matchPort: MatchPort): Collection<MatchPlayer> =
                matchPort.currentStatus.hunters

            override fun remove(matchPort: MatchPort, player: MatchPlayer): MatchResult =
                matchPort.removeHunter(player)

            override fun buildAddSubtree(
                builder: LiteralArgumentBuilder<CommandSourceStack>,
                matchPort: MatchPort
            ): LiteralArgumentBuilder<CommandSourceStack> =
                builder.then(
                    Commands.argument("player", ArgumentTypes.players())
                        .executes { ctx ->
                            val resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java)
                            val targets = resolver.resolve(ctx.source)
                            val currentRunner = matchPort.currentStatus.runner

                            if (targets.singleOrNull()?.uniqueId?.toKotlinUuid() == currentRunner?.uuid) {
                                return@executes CommandErrorRouter.fail(ctx.source, MatchFailureReason.PLAYER_ALREADY_RUNNER)
                            }

                            if (targets.singleOrNull()?.uniqueId?.toKotlinUuid() in matchPort.currentStatus.hunters.map { it.uuid }) {
                                return@executes CommandErrorRouter.fail(ctx.source, MatchFailureReason.PLAYER_ALREADY_HUNTER)
                            }

                            val eligible = targets.filterNot { it.uniqueId.toKotlinUuid() == currentRunner?.uuid }
                            if (eligible.isEmpty()) {
                                return@executes CommandErrorRouter.fail(
                                    ctx.source,
                                    CommandErrorRouter.CommandFailure.NoEligibleTargets
                                )
                            }

                            var compositeResult: MatchResult = MatchResult.Ok()
                            for (bukkitPlayer in eligible) {
                                val res = matchPort.addHunter(bukkitPlayer.toMatchPlayer())
                                if (res is MatchResult.Err) compositeResult = res
                            }
                            return@executes CommandErrorRouter.handle(ctx.source, compositeResult)
                        })
        };

        abstract fun clear(matchPort: MatchPort): MatchResult
        abstract fun getCandidates(matchPort: MatchPort): Collection<MatchPlayer>
        abstract fun remove(matchPort: MatchPort, player: MatchPlayer): MatchResult
        abstract fun buildAddSubtree(
            builder: LiteralArgumentBuilder<CommandSourceStack>,
            matchPort: MatchPort
        ): LiteralArgumentBuilder<CommandSourceStack>
    }

    private fun removeSubtreeBuilder(matchPort: MatchPort, group: GroupNames) = Commands.literal("remove")
        .then(
            Commands.argument("name", StringArgumentType.word())
                .suggests { _, builder ->
                    group.getCandidates(matchPort).forEach { builder.suggest(it.name) }
                    builder.buildFuture()
                }
                .executes { ctx ->
                    val name = StringArgumentType.getString(ctx, "name")
                    val target = group.getCandidates(matchPort).find { it.name.equals(name, ignoreCase = true) }
                        ?: return@executes CommandErrorRouter.fail(
                            ctx.source,
                            MatchFailureReason.PLAYER_NOT_IN_GROUP
                        )

                    return@executes CommandErrorRouter.handle(ctx.source, group.remove(matchPort, target))
                }
        )

    fun build(
        matchPort: MatchPort,
        group: GroupNames
    ): LiteralArgumentBuilder<CommandSourceStack> {
        val rootBuilder = Commands.literal(group.commandName)

        val clearBuilder = Commands.literal("clear").executes { ctx ->
            return@executes CommandErrorRouter.handle(ctx.source, group.clear(matchPort))
        }

        val addBuilder = group.buildAddSubtree(Commands.literal("add"), matchPort)
        val removeBuilder = removeSubtreeBuilder(matchPort, group)

        return rootBuilder.apply {
            then(clearBuilder)
            then(addBuilder)
            then(removeBuilder)
        }
    }
}