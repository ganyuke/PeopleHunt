package io.github.ganyuke.peoplehunt.paper

import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.services.core.CompassService
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.services.reporting.ReportingEngine
import io.github.ganyuke.peoplehunt.paper.adapters.PaperLoggerAdapter
import io.github.ganyuke.peoplehunt.paper.adapters.PaperSchedulerAdapter
import io.github.ganyuke.peoplehunt.paper.command.match.MatchCommand
import io.github.ganyuke.peoplehunt.paper.events.BroadcastEventHandler
import io.github.ganyuke.peoplehunt.paper.events.CompassEventHandler
import io.github.ganyuke.peoplehunt.paper.listeners.CombatStatsListener
import io.github.ganyuke.peoplehunt.paper.listeners.CoreListener
import io.github.ganyuke.peoplehunt.paper.listeners.EndPortalListener
import io.github.ganyuke.peoplehunt.paper.listeners.FluidListener
import io.github.ganyuke.peoplehunt.paper.listeners.InventoryListener
import io.github.ganyuke.peoplehunt.paper.listeners.MilestoneListener
import io.github.ganyuke.peoplehunt.paper.listeners.PlayerSnapshotPoller
import io.github.ganyuke.peoplehunt.paper.listeners.PotionEffectListener
import io.github.ganyuke.peoplehunt.paper.listeners.StructureListener
import io.github.ganyuke.peoplehunt.paper.listeners.TeleportListener
import io.github.ganyuke.peoplehunt.paper.utils.ConfigLoader
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

class PeopleHunt : JavaPlugin() {
    private lateinit var matchEngine: MatchEngine
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


        val compassEventHandler = CompassEventHandler(phConfig)
        val schedulerAdapter = PaperSchedulerAdapter(this)
        val loggerAdapter = PaperLoggerAdapter(this)

        matchEngine = MatchEngine(schedulerAdapter, outbound, phConfig)
        val reportingEngine = ReportingEngine(outbound, schedulerAdapter, loggerAdapter)

        val broadcastEventHandler = BroadcastEventHandler(reportingEngine)
        
        val compassService = CompassService(outbound)
        val playerSnapshotPoller = PlayerSnapshotPoller(this, inbound)
        val inventoryListener = InventoryListener(this, inbound)

        // register listeners on bus that match and compass react to
        registerInbound(listOf(
            matchEngine::onEvent,
            compassService::onReportableEvent,
            reportingEngine::onReportableEvent
        ))

        // register listeners on bus that game must react to
        registerOutbound(listOf(
            compassService::onMatchEvent,
            compassEventHandler::onMatchEvent,
            broadcastEventHandler::onMatchEvent,
            reportingEngine::onMatchEvent,
            playerSnapshotPoller::onMatchEvent,
            inventoryListener::onMatchEvent
        ))

        // register Bukkit listeners
        registerListeners(listOf(
            CoreListener(inbound),
            MilestoneListener(inbound),
            CombatStatsListener(inbound),
            EndPortalListener(inbound),
            PotionEffectListener(inbound),
            StructureListener(inbound),
            TeleportListener(inbound),
            FluidListener(this, inbound),
            playerSnapshotPoller,
            inventoryListener
        ))

        val manager = this.lifecycleManager

        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val dispatcher = event.registrar()
            dispatcher.register(MatchCommand.buildMatchCommand(matchEngine, reportingEngine))
        }
    }

    override fun onDisable() {
        // tasks cancelled by MatchEngine.endMatch or server shutdown
        matchEngine.shutdown()
    }
}
