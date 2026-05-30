package io.github.ganyuke.peoplehunt.core.events

import kotlin.uuid.Uuid
import io.github.ganyuke.peoplehunt.core.Utils.Pos4

sealed class ReportableEvent {
    data class PlayerMoved(val player: Uuid, val pos: Pos4) : ReportableEvent()
    data class PlayerRespawned(val player: Uuid, val pos: Pos4) : ReportableEvent()
    data class EntityDied(
        val player: Uuid?,
        val entityIdentifier: String,
        val pos: Pos4,
        val playerKiller: Uuid?,
        val entityKiller: String?,
    ) : ReportableEvent()
}
