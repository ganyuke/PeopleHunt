package io.github.ganyuke.peoplehunt.paper.items

import io.github.ganyuke.peoplehunt.core.utils.PEOPLEHUNT_NAMESPACE
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType

object HunterCompass {
    private const val COMPASS_NAME = "<dark_purple>Hunter Compass</dark_purple>"

    private const val ITEM_KEY = "hunter_compass"

    private val miniMessage = MiniMessage.miniMessage()
    private val key = NamespacedKey(PEOPLEHUNT_NAMESPACE, ITEM_KEY)

    fun isHunterCompass(pdc: PersistentDataContainer): Boolean =
        pdc.has(key, PersistentDataType.BOOLEAN)

    fun create(): ItemStack {
        val item = ItemStack(Material.COMPASS)
        val meta = item.itemMeta ?: return item

        meta.displayName(miniMessage.deserialize(COMPASS_NAME))
        meta.persistentDataContainer.set(
            key,
            PersistentDataType.BOOLEAN,
            true
        )

        item.itemMeta = meta
        return item
    }
}