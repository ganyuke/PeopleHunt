package io.github.ganyuke.peoplehunt.paper

import org.bukkit.plugin.java.JavaPlugin
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.services.CompassService
import io.github.ganyuke.peoplehunt.core.services.MatchEngine
import io.github.ganyuke.peoplehunt.paper.adapters.PaperCompassAdapter
import io.github.ganyuke.peoplehunt.paper.adapters.PaperSchedulerAdapter
import io.github.ganyuke.peoplehunt.paper.adapters.PaperServerAdapter
import io.github.ganyuke.peoplehunt.paper.command.MatchCommand
import io.github.ganyuke.peoplehunt.paper.listeners.PaperListener

class PeopleHunt : JavaPlugin() {

    override fun onEnable() {
        val inbound  = ReportableEventBus()
        val outbound = MatchEventBus()

        val scheduler    = PaperSchedulerAdapter(this)
        val matchEngine  = MatchEngine(scheduler, outbound)
        val compass      = CompassService(outbound)
        val paperCompass = PaperCompassAdapter()
        val paperServer  = PaperServerAdapter()

        // inbound: Paper → core services
        inbound.register(matchEngine::onEvent)
        inbound.register(compass::onReportableEvent)

        // outbound: core services → CompassService + Paper adapters
        outbound.register(compass::onMatchEvent)
        outbound.register(paperCompass::onMatchEvent)
        outbound.register(paperServer::onMatchEvent)

        server.pluginManager.registerEvents(PaperListener(inbound), this)
        getCommand("match")?.setExecutor(MatchCommand(matchEngine))
    }

    override fun onDisable() {
        // tasks cancelled by MatchEngine.endMatch or server shutdown
    }
}
