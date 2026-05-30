package io.github.ganyuke.peoplehunt.paper.utils

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import org.bukkit.Location
import org.bukkit.generator.structure.Structure

object StructureLocator {
    private val structureRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE)

    // should turn the ugly enums into stuff like `minecraft:fortress`
    private fun getStructureKeyString(structure: Structure): String? =
        structureRegistry.getKey(structure)?.toString()

    // this is a little more specific, check if the player is actually in a piece
    // of the structure instead of a bounding box
    private fun getCurrentStructure(location: Location): String? =
        location.chunk.structures.firstOrNull { structure ->
            structure.pieces.any { piece ->
                piece.boundingBox.contains(location.x, location.y, location.z)
            }
        }?.structure?.let(::getStructureKeyString)

    fun getStructureAt(pos: Location): String? = getCurrentStructure(pos)
}