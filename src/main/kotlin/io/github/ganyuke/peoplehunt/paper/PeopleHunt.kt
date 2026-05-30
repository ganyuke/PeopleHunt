package io.github.ganyuke.peoplehunt.paper

import org.bukkit.plugin.java.JavaPlugin
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.services.core.CompassService
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.services.reporting.ReportingEngine
import io.github.ganyuke.peoplehunt.paper.adapters.PaperCompassAdapter
import io.github.ganyuke.peoplehunt.paper.adapters.PaperLoggerAdapter
import io.github.ganyuke.peoplehunt.paper.adapters.PaperSchedulerAdapter
import io.github.ganyuke.peoplehunt.paper.adapters.PaperServerAdapter
import io.github.ganyuke.peoplehunt.paper.adapters.StructureLocatorAdapter
import io.github.ganyuke.peoplehunt.paper.command.match.MatchCommand
import io.github.ganyuke.peoplehunt.paper.listeners.CombatStatsListener
import io.github.ganyuke.peoplehunt.paper.listeners.CoreListener
import io.github.ganyuke.peoplehunt.paper.listeners.EndPortalListener
import io.github.ganyuke.peoplehunt.paper.listeners.MilestoneListener
import io.github.ganyuke.peoplehunt.paper.utils.ConfigLoader
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents

class PeopleHunt : JavaPlugin() {
    private lateinit var matchEngine: MatchEngine

    override fun onEnable() {
        saveDefaultConfig()

        val phConfig = ConfigLoader.load(this.logger, this.config)

        val inbound = ReportableEventBus()
        val outbound = MatchEventBus()

        val compassAdapter = PaperCompassAdapter(phConfig)
        val schedulerAdapter = PaperSchedulerAdapter(this)
        val structureLocatorAdapter = StructureLocatorAdapter()
        val loggerAdapter = PaperLoggerAdapter(this)

        matchEngine = MatchEngine(schedulerAdapter, outbound, phConfig)
        val reportingEngine = ReportingEngine(outbound, schedulerAdapter, structureLocatorAdapter, loggerAdapter)

        val paperServerAdapter = PaperServerAdapter(reportingEngine)
        
        val compassService = CompassService(outbound)

        // register listeners on bus that match and compass react to
        inbound.register(matchEngine::onEvent)
        inbound.register(compassService::onReportableEvent)
        inbound.register(reportingEngine::onReportableEvent)

        // register listeners on bus that game must react to
        outbound.register(compassService::onMatchEvent)
        outbound.register(compassAdapter::onMatchEvent)
        outbound.register(paperServerAdapter::onMatchEvent)
        outbound.register(reportingEngine::onMatchEvent)

        server.pluginManager.registerEvents(CoreListener(inbound), this)
        server.pluginManager.registerEvents(MilestoneListener(inbound), this)
        server.pluginManager.registerEvents(CombatStatsListener(inbound), this)
        server.pluginManager.registerEvents(EndPortalListener(inbound), this)

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
