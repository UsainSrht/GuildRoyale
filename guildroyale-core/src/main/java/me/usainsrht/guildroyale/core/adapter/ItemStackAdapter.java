package me.usainsrht.guildroyale.core.adapter;

import me.usainsrht.guildroyale.api.domain.SerializableItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Converts between {@link SerializableItemStack} (pure-Java domain) and
 * {@link ItemStack} (Bukkit API), preserving full NBT data via
 * {@code ItemStack.serializeAsBytes()} / {@code deserializeBytes()}.
 */
public final class ItemStackAdapter {

    private ItemStackAdapter() {}

    /**
     * Converts a live {@link ItemStack} to a {@link SerializableItemStack}.
     * Returns {@link SerializableItemStack#EMPTY} if {@code item} is null or air.
     */
    public static SerializableItemStack toSerializable(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return SerializableItemStack.EMPTY;
        }
        byte[] rawData = item.serializeAsBytes();
        return new SerializableItemStack(item.getType().name(), rawData);
    }

    /**
     * Converts a {@link SerializableItemStack} back to a live {@link ItemStack}.
     * Returns a single piece of {@link Material#AIR} if the input is empty or invalid.
     */
    public static ItemStack fromSerializable(SerializableItemStack s) {
        if (s == null || s.isEmpty()) {
            return new ItemStack(Material.AIR);
        }
        try {
            byte[] rawData = s.getRawData();
            if (rawData != null && rawData.length > 0) {
                return ItemStack.deserializeBytes(rawData);
            }
            // Fallback: reconstruct from material name only
            if (s.getMaterial() != null) {
                Material material = Material.matchMaterial(s.getMaterial());
                if (material != null) return new ItemStack(material);
            }
        } catch (Exception ignored) {
            // Corrupt data — fall through to AIR
        }
        return new ItemStack(Material.AIR);
    }
}
