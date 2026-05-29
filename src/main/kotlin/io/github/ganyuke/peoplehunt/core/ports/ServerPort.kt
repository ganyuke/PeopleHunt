package io.github.ganyuke.peoplehunt.core.ports

import kotlin.uuid.Uuid
import io.github.ganyuke.peoplehunt.core.Utils.Pos4

data class PlayerSnapshot(
    val uuid: Uuid,
    val pos: Pos4
)

interface ServerPort {
    fun getOnlinePlayers(): List<PlayerSnapshot>
}
