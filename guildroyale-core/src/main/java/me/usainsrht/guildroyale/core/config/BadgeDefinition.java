package me.usainsrht.guildroyale.core.config;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * A config-defined guild badge.
 *
 * @param id          unique badge key
 * @param displayName MiniMessage display text
 * @param symbol      MiniMessage symbol text
 * @param icon        icon itemstack
 * @param cost        economy cost to buy; {@code <= 0} means not buyable
 * @param grantable   whether this badge is grant-only via admin (if true, cannot be bought)
 * @param level       required guild level to equip/buy
 */
public record BadgeDefinition(
        String id,
        String displayName,
        String symbol,
        ItemStack icon,
        double cost,
        boolean grantable,
        int level
) {

    public BadgeDefinition(String id, String displayName, String symbol, ItemStack icon, double cost, boolean grantable) {
        this(id, displayName, symbol, icon, cost, grantable, 1);
    }

    public BadgeDefinition {
        Objects.requireNonNull(id, "id");
        displayName = displayName != null ? displayName : id;
        symbol = symbol != null ? symbol : displayName;
        icon = icon != null ? icon.clone() : new ItemStack(Material.NETHER_STAR);
        level = Math.max(1, level);
    }

    public boolean isBuyable() { return !grantable && cost > 0; }

    /** Legacy display string alias. */
    public String display() { return displayName; }

    @Override
    public ItemStack icon() {
        return icon != null ? icon.clone() : new ItemStack(Material.NETHER_STAR);
    }
}
