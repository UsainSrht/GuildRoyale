package me.usainsrht.guildroyale.api.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * One of the 16 Minecraft dye colors, used for role display coloring.
 */
public enum RoleColor {

    WHITE("<white>", "WHITE_DYE"),
    ORANGE("<gold>", "ORANGE_DYE"),
    MAGENTA("<light_purple>", "MAGENTA_DYE"),
    LIGHT_BLUE("<aqua>", "LIGHT_BLUE_DYE"),
    YELLOW("<yellow>", "YELLOW_DYE"),
    LIME("<green>", "LIME_DYE"),
    PINK("<#FF69B4>", "PINK_DYE"),
    GRAY("<gray>", "GRAY_DYE"),
    LIGHT_GRAY("<gray>", "LIGHT_GRAY_DYE"),
    CYAN("<dark_aqua>", "CYAN_DYE"),
    PURPLE("<dark_purple>", "PURPLE_DYE"),
    BLUE("<blue>", "BLUE_DYE"),
    BROWN("<#8B4513>", "BROWN_DYE"),
    GREEN("<dark_green>", "GREEN_DYE"),
    RED("<red>", "RED_DYE"),
    BLACK("<dark_gray>", "BLACK_DYE");

    private final String miniMessage;
    private final String dyeMaterial;

    RoleColor(String miniMessage, String dyeMaterial) {
        this.miniMessage = miniMessage;
        this.dyeMaterial = dyeMaterial;
    }

    /** MiniMessage color tag used to tint role names. */
    public String miniMessage() {
        return miniMessage;
    }

    /** Bukkit material name of the matching dye item. */
    public String dyeMaterial() {
        return dyeMaterial;
    }

    public static Optional<RoleColor> fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
