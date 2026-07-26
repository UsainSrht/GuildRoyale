package me.usainsrht.guildroyale.core.config;

/**
 * A config-defined guild badge.
 *
 * @param id        unique badge key
 * @param display   MiniMessage display text
 * @param cost      economy cost to buy; {@code <= 0} means not buyable
 * @param grantable whether admins can grant this badge
 */
public record BadgeDefinition(String id, String display, double cost, boolean grantable) {

    public boolean isBuyable() { return cost > 0; }
}
