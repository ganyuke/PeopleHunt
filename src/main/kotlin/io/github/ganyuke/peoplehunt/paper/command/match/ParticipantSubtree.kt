package io.github.ganyuke.peoplehunt.paper.command.match

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine.FailureReason
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine.MatchPlayer
import io.github.ganyuke.peoplehunt.paper.utils.Utils.toMatchPlayer
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import kotlin.collections.forEach
import kotlin.uuid.toKotlinUuid

object ParticipantSubtree {
    enum class GroupNames(val commandName: String) {
        RUNNER("runner") {
            override fun clear(engine: MatchEngine): MatchEngine.MatchResult = engine.clearRunner()

            override fun getCandidates(engine: MatchEngine): Collection<MatchPlayer> =
                listOfNotNull(engine.runner)

            override fun remove(engine: MatchEngine, player: MatchPlayer): MatchEngine.MatchResult =
                engine.removeRunner(player)

            override fun buildAddSubtree(
                builder: LiteralArgumentBuilder<CommandSourceStack>,
                engine: MatchEngine
            ): LiteralArgumentBuilder<CommandSourceStack> =
                builder.then(Commands.argument("player", ArgumentTypes.player()).executes { ctx ->
                    val targets =
                        ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java).resolve(ctx.source)

                    // only want to allow one player to be the runner, so only allow selector for one player
                    when {
                        // fail when the selector doens't match anyone
                        targets.isEmpty() -> {
                            return@executes CommandErrors.fail(
                                ctx.source,
                                CommandErrors.CommandFailure.NoEligibleTargets
                            )
                        }

                        // fail when the selctor matches more than one possible player
                        targets.size > 1 -> {
                            return@executes CommandErrors.fail(ctx.source, CommandErrors.CommandFailure.TooManyRunners)
                        }

                        targets.first().uniqueId.toKotlinUuid() in engine.hunters.map { it.uuid } -> {
                            return@executes CommandErrors.fail(ctx.source, FailureReason.PLAYER_ALREADY_HUNTER)
                        }

                        targets.first().uniqueId.toKotlinUuid() == engine.runner?.uuid -> {
                            return@executes CommandErrors.fail(ctx.source, FailureReason.PLAYER_ALREADY_RUNNER)
                        }
                    }
                    return@executes CommandErrors.handle(ctx.source, engine.setRunner(targets.first().toMatchPlayer()))
                })
        },

        HUNTER("hunter") {
            override fun clear(engine: MatchEngine): MatchEngine.MatchResult = engine.clearHunters()

            override fun getCandidates(engine: MatchEngine): Collection<MatchPlayer> =
                engine.hunters

            override fun remove(engine: MatchEngine, player: MatchPlayer): MatchEngine.MatchResult =
                engine.removeHunter(player)

            override fun buildAddSubtree(
                builder: LiteralArgumentBuilder<CommandSourceStack>,
                engine: MatchEngine
            ): LiteralArgumentBuilder<CommandSourceStack> =
                builder.then(Commands.argument("player", ArgumentTypes.players())
                    .executes { ctx ->
                        val resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java)
                        val targets = resolver.resolve(ctx.source)
                        val currentRunner = engine.runner

                        // don't let players add the runner as a hunter specifically
                        if (targets.singleOrNull()?.uniqueId?.toKotlinUuid() == currentRunner?.uuid) {
                            return@executes CommandErrors.fail(ctx.source, FailureReason.PLAYER_ALREADY_RUNNER)
                        }

                        // error out on adding an existing hunter
                        if (targets.singleOrNull()?.uniqueId?.toKotlinUuid() in engine.hunters.map { it.uuid }) {
                            return@executes CommandErrors.fail(ctx.source, FailureReason.PLAYER_ALREADY_HUNTER)
                        }

                        // after excluding runner, check if any targets are actually there
                        val eligible = targets.filterNot { it.uniqueId.toKotlinUuid() == currentRunner?.uuid }
                        if (eligible.isEmpty()) {
                            return@executes CommandErrors.fail(
                                ctx.source,
                                CommandErrors.CommandFailure.NoEligibleTargets
                            )
                        }

                        var compositeResult: MatchEngine.MatchResult = MatchEngine.MatchResult.Ok()
                        for (bukkitPlayer in eligible) {
                            val res = engine.addHunter(bukkitPlayer.toMatchPlayer())
                            if (res is MatchEngine.MatchResult.Err) compositeResult = res
                        }
                        return@executes CommandErrors.handle(ctx.source, compositeResult)
                    })
        };

        abstract fun clear(engine: MatchEngine): MatchEngine.MatchResult
        abstract fun getCandidates(engine: MatchEngine): Collection<MatchPlayer>
        abstract fun remove(engine: MatchEngine, player: MatchPlayer): MatchEngine.MatchResult
        abstract fun buildAddSubtree(
            builder: LiteralArgumentBuilder<CommandSourceStack>,
            engine: MatchEngine
        ): LiteralArgumentBuilder<CommandSourceStack>
    }

    private fun removeSubtreeBuilder(matchEngine: MatchEngine, group: GroupNames) = Commands.literal("remove")
        .then(
            Commands.argument("name", StringArgumentType.word())
                .suggests { _, builder ->
                    group.getCandidates(matchEngine).forEach { builder.suggest(it.name) }
                    builder.buildFuture()
                }
                .executes { ctx ->
                    val name = StringArgumentType.getString(ctx, "name")
                    val target = group.getCandidates(matchEngine).find { it.name.equals(name, ignoreCase = true) }
                        ?: return@executes CommandErrors.fail(
                            ctx.source,
                            FailureReason.PLAYER_NOT_IN_GROUP
                        )

                    return@executes CommandErrors.handle(ctx.source, group.remove(matchEngine, target))
                }
        )

    fun participantSubtreeBuilder(
        matchEngine: MatchEngine,
        group: GroupNames
    ): LiteralArgumentBuilder<CommandSourceStack> {
        val rootBuilder = Commands.literal(group.commandName)

        // clear subtree delegates to enum's clear function
        val clearBuilder = Commands.literal("clear").executes { ctx ->
            return@executes CommandErrors.handle(ctx.source, group.clear(matchEngine))
        }

        // add subtree delegates to enum's add builder since runner is 1:1 and hunter is many
        // so need a different tree to handle this
        val addBuilder = group.buildAddSubtree(Commands.literal("add"), matchEngine)

        val removeBuilder = removeSubtreeBuilder(matchEngine, group)

        return rootBuilder.apply {
            then(clearBuilder)
            then(addBuilder)
            then(removeBuilder)
        }
    }
}