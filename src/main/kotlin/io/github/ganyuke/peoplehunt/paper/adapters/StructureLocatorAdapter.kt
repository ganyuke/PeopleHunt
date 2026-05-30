package io.github.ganyuke.peoplehunt.paper.adapters

import io.github.ganyuke.peoplehunt.core.Utils
import io.github.ganyuke.peoplehunt.core.ports.StructureLocatorPort
import io.github.ganyuke.peoplehunt.paper.utils.Utils.toLocation
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import org.bukkit.Location
import org.bukkit.generator.structure.Structure

class StructureLocatorAdapter : StructureLocatorPort {
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

    // can't really do anything about a bad world so just return null if it's bad
    override fun getStructureAt(pos: Utils.Pos4): String? = pos.toLocation()?.let(::getCurrentStructure)
}