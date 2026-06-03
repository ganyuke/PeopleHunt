package io.github.ganyuke.peoplehunt.core.events.models

import kotlinx.serialization.Serializable

@Serializable
enum class TeleportCause {
    ENDER_PEARL, CONSUMABLE_EFFECT, COMMAND, SPECTATOR_WARP,
    END_PORTAL, NETHER_PORTAL, END_GATEWAY, UNKNOWN
}