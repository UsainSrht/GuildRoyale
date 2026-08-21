package me.usainsrht.guildroyale.core.gui;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.config.ItemRequirement;
import me.usainsrht.guildroyale.core.config.MissionConfig;
import me.usainsrht.guildroyale.core.message.Text;
import me.usainsrht.guildroyale.core.service.GuildServiceImpl;
import me.usainsrht.guildroyale.core.service.MissionServiceImpl;
import me.usainsrht.itemapi.itemtext.ItemText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates whether a player can start a guild mission and builds requirement lore.
 */
public final class MissionEligibility {

    public enum Status {
        READY("mission-start-ready"),
        ALREADY_ACTIVE("mission-active-indicator"),
        ON_COOLDOWN("mission-start-cooldown"),
        NO_PERMISSION("mission-start-no-permission"),
        INSUFFICIENT_FUNDS("mission-start-insufficient-funds"),
        MISSING_ITEMS("mission-start-missing-items");

        private final String itemKey;

        Status(String itemKey) {
            this.itemKey = itemKey;
        }

        public String itemKey() {
            return itemKey;
        }

        public boolean canStart() {
            return this == READY;
        }
    }

    private MissionEligibility() {}

    public static Status evaluate(Player player, Guild guild, GuildMember viewer) {
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null || guild == null || viewer == null) {
            return Status.NO_PERMISSION;
        }

        MissionServiceImpl missionService = (MissionServiceImpl) plugin.getMissionService();
        if (missionService.isMissionActive(guild.getId())) {
            return Status.ALREADY_ACTIVE;
        }

        long cd = missionService.getCooldownRemainingSeconds(guild.getId());
        if (cd > 0) {
            return Status.ON_COOLDOWN;
        }

        me.usainsrht.guildroyale.core.service.PermissionEvaluatorImpl evaluator = new me.usainsrht.guildroyale.core.service.PermissionEvaluatorImpl();
        if (!evaluator.canAct(viewer, GuildPermissionKey.MISSION_START)) {
            return Status.NO_PERMISSION;
        }

        MissionConfig config = plugin.getConfigManager().getMissionConfig();
        if (config.isPermissionEnabled() && !player.hasPermission(config.getPermissionNode())) {
            return Status.NO_PERMISSION;
        }

        GuildServiceImpl service = (GuildServiceImpl) plugin.getGuildService();
        if (config.isMoneyEnabled() && config.getMoneyCost() > 0) {
            if (config.getMoneyTakeFrom() == MissionConfig.TakeFrom.BANK) {
                if (!service.economy().has(guild.getId(), config.getMoneyCost())) {
                    return Status.INSUFFICIENT_FUNDS;
                }
            } else {
                if (!service.economy().has(player.getUniqueId(), config.getMoneyCost())) {
                    return Status.INSUFFICIENT_FUNDS;
                }
            }
        }

        if (config.isItemsEnabled() && !hasRequiredItems(player, config.getItemRequirements())) {
            return Status.MISSING_ITEMS;
        }

        return Status.READY;
    }

    private static boolean hasRequiredItems(Player player, List<ItemRequirement> requirements) {
        for (ItemRequirement req : requirements) {
            Material mat = Material.matchMaterial(req.material());
            if (mat == null || mat.isAir()) continue;
            if (!player.getInventory().containsAtLeast(new ItemStack(mat), req.amount())) {
                return false;
            }
        }
        return true;
    }

    /** Builds requirement lore lines with ItemText sprites and status indicators. */
    public static List<Component> requirementLore(Player player, Guild guild, GuildMember viewer) {
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        List<Component> lines = new ArrayList<>();
        if (plugin == null) return lines;

        GuiConfig gui = plugin.getGuiConfig();
        MissionConfig config = plugin.getConfigManager().getMissionConfig();
        GuildServiceImpl service = (GuildServiceImpl) plugin.getGuildService();
        MissionServiceImpl missionService = (MissionServiceImpl) plugin.getMissionService();

        try (Text.Scope ignored = Text.push(player)) {
            lines.add(line(gui, player, "missions.requirements.separator", " <dark_gray>───────────────── "));
            lines.add(line(gui, player, "missions.requirements.header", " <yellow>Requirements: "));

            // Guild role permission check
            me.usainsrht.guildroyale.core.service.PermissionEvaluatorImpl evaluator = new me.usainsrht.guildroyale.core.service.PermissionEvaluatorImpl();
            boolean rolePermOk = evaluator.canAct(viewer, GuildPermissionKey.MISSION_START);
            lines.add(line(gui, player, rolePermOk ? "missions.requirements.role-ok" : "missions.requirements.role-fail",
                    rolePermOk ? " <green>✔ <gray>Guild Role Permission " : " <red>✘ <gray>Guild Role Permission "));

            // Optional player permission node check
            if (config.isPermissionEnabled()) {
                boolean permOk = player.hasPermission(config.getPermissionNode());
                lines.add(line(gui, player, permOk ? "missions.requirements.permission-ok" : "missions.requirements.permission-fail",
                        permOk ? " <green>✔ <gray>Permission " : " <red>✘ <gray>Permission <dark_gray>(<node>) ",
                        Placeholder.unparsed("node", config.getPermissionNode())));
            }

            // Cooldown status
            long cdRemaining = missionService.getCooldownRemainingSeconds(guild.getId());
            if (cdRemaining > 0) {
                String formattedTime = MissionServiceImpl.formatDuration(cdRemaining);
                lines.add(line(gui, player, "missions.requirements.cooldown-fail",
                        " <red>✘ <gray>Cooldown: <yellow><cooldown> remaining ",
                        Placeholder.unparsed("cooldown", formattedTime)));
            } else {
                lines.add(line(gui, player, "missions.requirements.cooldown-ok",
                        " <green>✔ <gray>Cooldown: <white>Ready "));
            }

            // Money cost
            if (config.isMoneyEnabled() && config.getMoneyCost() > 0) {
                boolean bankSource = config.getMoneyTakeFrom() == MissionConfig.TakeFrom.BANK;
                boolean moneyOk = bankSource
                        ? service.economy().has(guild.getId(), config.getMoneyCost())
                        : service.economy().has(player.getUniqueId(), config.getMoneyCost());

                String formatted = service.economy().format(config.getMoneyCost());
                String sourceLabel = bankSource ? " (Guild Bank)" : "";
                lines.add(line(gui, player, moneyOk ? "missions.requirements.cost-ok" : "missions.requirements.cost-fail",
                        moneyOk ? " <green>✔ <gray>Cost: <white><cost><source> " : " <red>✘ <gray>Cost: <white><cost><source> ",
                        Placeholder.unparsed("cost", formatted),
                        Placeholder.unparsed("source", sourceLabel)));
            }

            // Items
            if (config.isItemsEnabled() && !config.getItemRequirements().isEmpty()) {
                String okPrefix = string(gui, "missions.requirements.item-ok-prefix", " <green>✔ ");
                String failPrefix = string(gui, "missions.requirements.item-fail-prefix", " <red>✘ ");
                for (ItemRequirement req : config.getItemRequirements()) {
                    Material mat = Material.matchMaterial(req.material());
                    if (mat == null || mat.isAir()) continue;

                    ItemStack stack = new ItemStack(mat, Math.max(1, req.amount()));
                    boolean ok = player.getInventory().containsAtLeast(new ItemStack(mat), req.amount());
                    Component formatted = ItemText.format(stack, opts -> opts
                            .pattern("<item_sprite><item_amount> <item_displayname>")
                            .showAmountWhenOne(true)
                            .hoverEnabled(false));
                    lines.add(Component.text()
                            .append(Text.parse(ok ? okPrefix : failPrefix, player))
                            .append(formatted)
                            .append(Component.text(" "))
                            .build());
                }
            }
        }
        return lines;
    }

    private static String string(GuiConfig gui, String path, String def) {
        return gui != null ? gui.string(path, def) : def;
    }

    private static Component line(GuiConfig gui, Player player, String path, String def, TagResolver... resolvers) {
        String raw = string(gui, path, def);
        return Text.parse(raw, player, resolvers);
    }
}
