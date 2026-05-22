package dev.guildroyale.api.domain;

import java.util.Arrays;
import java.util.Objects;

/**
 * A serializable representation of a Minecraft ItemStack that retains full NBT data.
 *
 * <p>The {@code rawData} field holds the bytes produced by Paper's
 * {@code ItemStack.serializeAsBytes()} — this preserves enchantments, custom model
 * data, display names, lore, and all other NBT. Conversion to/from a live
 * {@code org.bukkit.inventory.ItemStack} is handled by
 * {@code dev.guildroyale.core.adapter.ItemStackAdapter} in the core module.
 *
 * <p>The {@code material} field is a human-readable fallback string (e.g.
 * {@code "DIAMOND_SWORD"}) used only when {@code rawData} is unavailable.
 */
public final class SerializableItemStack {

    /** Sentinel for a missing/unset icon. */
    public static final SerializableItemStack EMPTY = new SerializableItemStack(null, null);

    private final String material;
    private final byte[] rawData;

    public SerializableItemStack(String material, byte[] rawData) {
        this.material = material;
        this.rawData = rawData != null ? Arrays.copyOf(rawData, rawData.length) : null;
    }

    public String getMaterial() { return material; }

    public byte[] getRawData() { return rawData != null ? Arrays.copyOf(rawData, rawData.length) : null; }

    public boolean isEmpty() { return rawData == null || rawData.length == 0; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SerializableItemStack s)) return false;
        return Objects.equals(material, s.material) && Arrays.equals(rawData, s.rawData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(material, Arrays.hashCode(rawData));
    }

    @Override
    public String toString() {
        return "SerializableItemStack{material='" + material + "', hasData=" + (rawData != null && rawData.length > 0) + '}';
    }
}
