package io.github.ganyuke.peoplehunt.core.events.models

data class ItemSnapshot(
    val typeIdentifier: String,
    val count: Int,
    val displayName: String?,
)

data class InventorySnapshot(
    val hotbar: List<ItemSnapshot?>,
    val mainInventory: List<ItemSnapshot?>,
    val offhand: ItemSnapshot?,
    val helmet: ItemSnapshot?,
    val chestplate: ItemSnapshot?,
    val leggings: ItemSnapshot?,
    val boots: ItemSnapshot?,
)