package io.github.ganyuke.peoplehunt.paper

import org.bukkit.plugin.java.JavaPlugin
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.services.CompassService
import io.github.ganyuke.peoplehunt.core.services.MatchEngine
import io.github.ganyuke.peoplehunt.core.services.ReportingEngine
import io.github.ganyuke.peoplehunt.paper.adapters.PaperCompassAdapter
import io.github.ganyuke.peoplehunt.paper.adapters.PaperSchedulerAdapter
import io.github.ganyuke.peoplehunt.paper.adapters.PaperServerAdapter
import io.github.ganyuke.peoplehunt.paper.command.match.MatchCommand
import io.github.ganyuke.peoplehunt.paper.listeners.PaperListener
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents

class PeopleHunt : JavaPlugin() {
    override fun onEnable() {
        val inbound = ReportableEventBus()
        val outbound = MatchEventBus()

        val scheduler = PaperSchedulerAdapter(this)
        val matchEngine = MatchEngine(scheduler, outbound)
        val reportingEngine = ReportingEngine()

        val compass = CompassService(outbound)
        val paperCompass = PaperCompassAdapter()
        val paperServer = PaperServerAdapter(matchEngine, reportingEngine)

        // register listeners on bus that match and compass react to
        inbound.register(matchEngine::onEvent)
        inbound.register(compass::onReportableEvent)

        // register listeners on bus that game must react to
        outbound.register(compass::onMatchEvent)
        outbound.register(paperCompass::onMatchEvent)
        outbound.register(paperServer::onMatchEvent)

        server.pluginManager.registerEvents(PaperListener(inbound), this)

        val manager = this.lifecycleManager

        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val dispatcher = event.registrar()
            dispatcher.register(MatchCommand.buildMatchCommand(matchEngine, reportingEngine))
        }
    }

    override fun onDisable() {
        // tasks cancelled by MatchEngine.endMatch or server shutdown
    }
}
