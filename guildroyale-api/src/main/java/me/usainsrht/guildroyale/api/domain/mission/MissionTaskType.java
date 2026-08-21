package me.usainsrht.guildroyale.api.domain.mission;

/**
 * Types of tasks that can be assigned in a guild mission.
 */
public enum MissionTaskType {

    /** Break a certain amount of blocks matching criteria. */
    BLOCK_BREAK,

    /** Complete a certain number of trades with villagers. */
    VILLAGER_TRADE,

    /** Trade a specific item with villagers (bought or sold). */
    VILLAGER_TRADE_ITEM,

    /** Kill a certain number of entities matching criteria. */
    KILL_ENTITY,

    /** Craft a certain number of items. */
    CRAFT_ITEM,

    /** Custom or 3rd-party plugin task triggered via API or commands. */
    CUSTOM;

    public static MissionTaskType fromString(String name) {
        if (name == null) return CUSTOM;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CUSTOM;
        }
    }
}
