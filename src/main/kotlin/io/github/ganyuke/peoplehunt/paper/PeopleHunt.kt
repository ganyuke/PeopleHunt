package io.github.ganyuke.peoplehunt.paper

import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.services.core.CompassService
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.services.reporting.MilestoneRouter
import io.github.ganyuke.peoplehunt.core.services.reporting.ReportingEngine
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.JsonStorage
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.ReportStenographer
import io.github.ganyuke.peoplehunt.paper.adapters.PaperLoggerAdapter
import io.github.ganyuke.peoplehunt.paper.adapters.PaperSchedulerAdapter
import io.github.ganyuke.peoplehunt.paper.command.match.MatchCommand
import io.github.ganyuke.peoplehunt.paper.events.BroadcastEventHandler
import io.github.ganyuke.peoplehunt.paper.events.CompassEventHandler
import io.github.ganyuke.peoplehunt.paper.listeners.*
import io.github.ganyuke.peoplehunt.paper.utils.ConfigLoader
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

class PeopleHunt : JavaPlugin() {
    private lateinit var matchEngine: MatchEngine
    private lateinit var reportStenographer: ReportStenographer
    private val inbound = ReportableEventBus()
    private val outbound = MatchEventBus()

    private fun registerListeners(listeners: List<Listener>) {
        listeners.forEach { server.pluginManager.registerEvents(it, this) }
    }

    private fun registerInbound(listeners: List<ReportableEventBus.ReportableEventListener>) {
        listeners.forEach(inbound::register)
    }

    private fun registerOutbound(listeners: List<MatchEventBus.MatchEventListener>) {
        listeners.forEach(outbound::register)
    }

    override fun onEnable() {
        saveDefaultConfig()

        val phConfig = ConfigLoader.load(this.logger, this.config)
        val loggerAdapter = PaperLoggerAdapter(this)

        val reportOutputFolder = dataFolder.resolve("reports").also {
            runCatching { it.mkdirs() }
                .onFailure {
                    loggerAdapter.error("Failed to create replays directory: $it")
                    server.pluginManager.disablePlugin(this)
                    return
                }
        }

        val compassEventHandler = CompassEventHandler(phConfig)
        val schedulerAdapter = PaperSchedulerAdapter(this)

        matchEngine = MatchEngine(schedulerAdapter, outbound, phConfig)
        val reportingEngine = ReportingEngine(loggerAdapter)

        val milestoneRouter = MilestoneRouter(inbound, loggerAdapter)

        val broadcastEventHandler = BroadcastEventHandler(reportingEngine)

        val compassService = CompassService(outbound)
        val playerSnapshotPoller = PlayerSnapshotPoller(this, inbound)
        val inventoryListener = InventoryListener(this, inbound)
        val projectileTracker = ProjectileTracker(this, inbound)
        val endFightTracker = EndFightTracker(this, inbound)
        val craftingListener = CraftingListener(inbound)
        val foodTracker = FoodTracker(inbound)
        val mobTracker = MobTracker(this, inbound)
        val landmarkTracker = LandmarkTracker(inbound)
        reportStenographer =
            ReportStenographer(outbound, schedulerAdapter, loggerAdapter, JsonStorage(reportOutputFolder.toPath()))

        // register listeners on bus that match and compass react to
        registerInbound(
            listOf(
                matchEngine::onEvent,
                compassService::onReportableEvent,
                endFightTracker::onReportableEvent,
                reportingEngine::onReportableEvent,
                milestoneRouter::onReportableEvent,
                reportStenographer::onReportableEvent
            )
        )

        // register listeners on bus that game must react to
        registerOutbound(
            listOf(
                compassService::onMatchEvent,
                compassEventHandler::onMatchEvent,
                broadcastEventHandler::onMatchEvent,
                playerSnapshotPoller::onMatchEvent,
                inventoryListener::onMatchEvent,
                projectileTracker::onMatchEvent,
                endFightTracker::onMatchEvent,
                mobTracker::onMatchEvent,
                landmarkTracker::onMatchEvent,
                reportingEngine::onMatchEvent,
                milestoneRouter::onMatchEvent,
                reportStenographer::onMatchEvent
            )
        )

        // register Bukkit listeners
        registerListeners(
            listOf(
                CoreListener(inbound),
                MilestoneListener(inbound),
                CombatStatsListener(inbound),
                EndPortalListener(inbound),
                HealthRegainListener(inbound),
                PotionEffectListener(inbound),
                StructureListener(inbound),
                TeleportListener(inbound),
                GameModeListener(inbound),
                FluidListener(this, inbound),
                EnvironmentDamageListener(inbound),
                craftingListener,
                foodTracker,
                projectileTracker,
                playerSnapshotPoller,
                inventoryListener,
                endFightTracker,
                mobTracker,
                landmarkTracker
            )
        )

        val manager = this.lifecycleManager

        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val dispatcher = event.registrar()
            dispatcher.register(MatchCommand.buildMatchCommand(matchEngine, reportingEngine))
        }
    }

    override fun onDisable() {
        // tasks cancelled by MatchEngine.endMatch or server shutdown
        reportStenographer.shutdown()
        matchEngine.shutdown()
    }
}
