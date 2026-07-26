package me.usainsrht.guildroyale.core.config;

/**
 * A material + amount pair required for guild creation.
 */
public record ItemRequirement(String material, int amount) {

    public ItemRequirement {
        if (material == null || material.isBlank()) {
            throw new IllegalArgumentException("material required");
        }
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be >= 1");
        }
    }
}
