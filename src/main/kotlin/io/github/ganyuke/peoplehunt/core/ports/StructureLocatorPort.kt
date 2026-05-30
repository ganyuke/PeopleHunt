package io.github.ganyuke.peoplehunt.core.ports

import io.github.ganyuke.peoplehunt.core.Utils

interface StructureLocatorPort {
    fun getStructureAt(pos: Utils.Pos4): String?  // returns e.g. "minecraft:fortress", null if none
}