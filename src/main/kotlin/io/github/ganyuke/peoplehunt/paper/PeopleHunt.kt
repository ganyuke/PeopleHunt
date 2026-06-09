package io.github.ganyuke.peoplehunt.paper

import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.services.core.CompassService
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.services.reporting.MilestoneRouter
import io.github.ganyuke.peoplehunt.core.services.reporting.highlighter.EventHighlighter
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.ReportService
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.exporter.ReportExportHandler
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.exporter.WebReportSerializer
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.SqliteStorage
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.stenography.ReportStenographer
import io.github.ganyuke.peoplehunt.core.utils.InstantSerializer
import io.github.ganyuke.peoplehunt.core.utils.UuidSerializer
import io.github.ganyuke.peoplehunt.paper.adapters.PaperLoggerAdapter
import io.github.ganyuke.peoplehunt.paper.adapters.PaperSchedulerAdapter
import io.github.ganyuke.peoplehunt.paper.command.PhCommand
import io.github.ganyuke.peoplehunt.paper.events.BroadcastEventHandler
import io.github.ganyuke.peoplehunt.paper.events.CompassEventHandler
import io.github.ganyuke.peoplehunt.paper.listeners.*
import io.github.ganyuke.peoplehunt.paper.utils.ConfigLoader
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PeopleHunt : JavaPlugin() {
    private lateinit var matchEngine: MatchEngine
    private lateinit var reportStenographer: ReportStenographer
    private lateinit var reportExportHandler: ReportExportHandler
    private lateinit var reportService: ReportService
    private lateinit var webReportSerializer: WebReportSerializer

    private val inbound = ReportableEventBus()
    private val outbound = MatchEventBus()

    private val json: Json = Json {
        serializersModule = SerializersModule {
            contextual(Uuid::class, UuidSerializer)
            contextual(Instant::class, InstantSerializer)
        }
    }

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

        val phConfig = ConfigLoader.load(this.config)
        val loggerAdapter = PaperLoggerAdapter(this)

        val reportOutputFolder = dataFolder.resolve("reports").also {
            runCatching { it.mkdirs() }
                .onFailure {
                    loggerAdapter.error("Failed to create replays directory: $it")
                    server.pluginManager.disablePlugin(this)
                    return
                }
        }

        val sqliteStorage = SqliteStorage(reportOutputFolder.toPath(), json)
        try {
            sqliteStorage.verifyStorage()
        } catch (cause: Exception) {
            loggerAdapter.error("SQLite storage verification failed", cause)
            server.pluginManager.disablePlugin(this)
            return
        }

        val schedulerAdapter = PaperSchedulerAdapter(this)
        val compassEventHandler = CompassEventHandler(phConfig)

        val webSerializer = WebReportSerializer(reportOutputFolder.toPath(), sqliteStorage, json, schedulerAdapter)

        matchEngine = MatchEngine(schedulerAdapter, outbound, phConfig)
        val eventHighlighter = EventHighlighter(loggerAdapter)

        val milestoneRouter = MilestoneRouter(inbound, loggerAdapter)

        val broadcastEventHandler = BroadcastEventHandler(eventHighlighter)

        val compassService = CompassService(outbound)
        val playerSnapshotPoller = PlayerSnapshotPoller(this, inbound)
        val inventoryListener = InventoryListener(this, inbound)
        val projectileTracker = ProjectileTracker(this, inbound)
        val endFightTracker = EndFightTracker(this, inbound)
        val craftingListener = CraftingListener(inbound)
        val foodTracker = FoodTracker(inbound)
        val mobTracker = MobTracker(this, inbound)
        val landmarkTracker = LandmarkTracker(inbound)

        reportStenographer = ReportStenographer(
            loggerAdapter,
            sqliteStorage,
            schedulerAdapter,
            phConfig,
        )
        reportExportHandler = ReportExportHandler(webSerializer, loggerAdapter, outbound)
        reportService = ReportService(reportStenographer, webSerializer, reportOutputFolder.toPath(), eventHighlighter)

        // register listeners on bus that match and compass react to
        registerInbound(
            listOf(
                matchEngine::onEvent,
                compassService::onReportableEvent,
                endFightTracker::onReportableEvent,
                eventHighlighter::onReportableEvent,
                milestoneRouter::onReportableEvent,
                reportStenographer::onReportableEvent,
            ),
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
                eventHighlighter::onMatchEvent,
                milestoneRouter::onMatchEvent,
                reportStenographer::onMatchEvent,
                reportExportHandler::onMatchEvent,
            ),
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
                landmarkTracker,
            ),
        )

        val manager = this.lifecycleManager
        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val commandRoot = PhCommand.buildMatchCommand(
                matchEngine,
                reportService,
                loggerAdapter,
                outbound,
            )
            event.registrar().register(commandRoot)
        }
    }

    override fun onDisable() {
        // tasks cancelled by MatchEngine.endMatch or server shutdown
        webReportSerializer.shutdown()
        matchEngine.shutdown()
        reportStenographer.shutdown()
    }
}
