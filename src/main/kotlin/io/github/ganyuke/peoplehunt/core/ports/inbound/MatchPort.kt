package io.github.ganyuke.peoplehunt.core.ports.inbound

import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchResult
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchState

interface MatchPort {
    // /ph status [last] commands
    val currentStatus: MatchState
    val lastMatchResult: MatchState.Finished?

    // /ph <prime|start|end> commands
    fun prime(onlinePlayers: List<MatchPlayer>): MatchResult
    fun forceStart(onlinePlayers: List<MatchPlayer>): MatchResult
    fun forceEnd(): MatchResult

    // participant subtree (/ph <hunter|runner>) commands
    fun setRunner(player: MatchPlayer): MatchResult
    fun clearRunner(): MatchResult
    fun removeRunner(player: MatchPlayer): MatchResult

    fun addHunter(player: MatchPlayer): MatchResult
    fun clearHunters(): MatchResult
    fun removeHunter(player: MatchPlayer): MatchResult
}