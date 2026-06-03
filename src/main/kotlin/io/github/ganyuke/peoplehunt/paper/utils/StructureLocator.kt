package io.github.ganyuke.peoplehunt.paper.utils

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import org.bukkit.Location
import org.bukkit.generator.structure.Structure

data class StructureInfo(val name: String, val center: Location)

object StructureLocator {
    private val structureRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE)

    private fun getStructureKeyString(structure: Structure): String? =
        structureRegistry.getKey(structure)?.toString()

    private fun findMatchingChunkStructure(location: Location) =
        location.chunk.structures.firstOrNull { structure ->
            structure.pieces.any { piece ->
                piece.boundingBox.contains(location.x, location.y, location.z)
            }
        }

    fun getStructureAt(pos: Location): String? =
        findMatchingChunkStructure(pos)?.structure?.let(::getStructureKeyString)

    fun getStructureInfoAt(pos: Location): StructureInfo? {
        val chunkStructure = findMatchingChunkStructure(pos) ?: return null
        val name = getStructureKeyString(chunkStructure.structure) ?: return null
        val piece = chunkStructure.pieces.firstOrNull { it.boundingBox.contains(pos.x, pos.y, pos.z) } ?: return null
        return StructureInfo(name, piece.boundingBox.center.toLocation(pos.world))
    }
}