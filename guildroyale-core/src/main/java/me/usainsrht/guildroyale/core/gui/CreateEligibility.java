package me.usainsrht.guildroyale.core.gui;

import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.config.ItemRequirement;
import me.usainsrht.guildroyale.core.message.Text;
import me.usainsrht.guildroyale.core.service.GuildServiceImpl;
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
 * Evaluates whether a player can create a guild and picks the matching GUI item key.
 */
public final class CreateEligibility {

    public enum Status {
        READY("create-ready"),
        ALREADY_IN_GUILD("create-already-in-guild"),
        NO_PERMISSION("create-no-permission"),
        INSUFFICIENT_FUNDS("create-insufficient-funds"),
        MISSING_ITEMS("create-missing-items");

        private final String itemKey;

        Status(String itemKey) {
            this.itemKey = itemKey;
        }

        public String itemKey() {
            return itemKey;
        }

        public boolean canCreate() {
            return this == READY;
        }
    }

    private CreateEligibility() {}

    public static Status evaluate(Player player, boolean alreadyInGuild) {
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) {
            return Status.NO_PERMISSION;
        }
        if (alreadyInGuild) {
            return Status.ALREADY_IN_GUILD;
        }
        var config = plugin.getConfigManager();
        if (config.isCreationPermissionEnabled()
                && !player.hasPermission(config.getCreationPermissionNode())) {
            return Status.NO_PERMISSION;
        }
        GuildServiceImpl service = (GuildServiceImpl) plugin.getGuildService();
        if (config.isCreationMoneyEnabled()) {
            double cost = config.getCreationMoneyCost();
            if (cost > 0 && !service.economy().has(player.getUniqueId(), cost)) {
                return Status.INSUFFICIENT_FUNDS;
            }
        }
        if (config.isCreationItemsEnabled() && !hasRequiredItems(player, config.getCreationItemRequirements())) {
            return Status.MISSING_ITEMS;
        }
        return Status.READY;
    }

    private static boolean hasRequiredItems(Player player, List<ItemRequirement> requirements) {
        for (ItemRequirement req : requirements) {
            Material mat = Material.matchMaterial(req.material());
            if (mat == null || mat.isAir()) {
                continue;
            }
            if (!player.getInventory().containsAtLeast(new ItemStack(mat), req.amount())) {
                return false;
            }
        }
        return true;
    }

    /** Builds MiniMessage lore lines describing creation requirements (with ItemText sprites). */
    public static List<Component> requirementLore(Player player) {
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        List<Component> lines = new ArrayList<>();
        if (plugin == null) {
            return lines;
        }
        GuiConfig gui = plugin.getGuiConfig();
        var config = plugin.getConfigManager();
        GuildServiceImpl service = (GuildServiceImpl) plugin.getGuildService();

        try (Text.Scope ignored = Text.push(player)) {
            lines.add(line(gui, player, "hub.requirements.separator", " <dark_gray>───────────────── "));
            lines.add(line(gui, player, "hub.requirements.header", " <yellow>Requirements: "));

            if (config.isCreationPermissionEnabled()) {
                boolean ok = player.hasPermission(config.getCreationPermissionNode());
                String path = ok ? "hub.requirements.permission-ok" : "hub.requirements.permission-fail";
                String def = ok
                        ? " <green>✔ <gray>Permission "
                        : " <red>✘ <gray>Permission <dark_gray>(<node>) ";
                lines.add(line(gui, player, path, def,
                        Placeholder.unparsed("node", config.getCreationPermissionNode())));
            }

            if (config.isCreationMoneyEnabled()) {
                double cost = config.getCreationMoneyCost();
                boolean ok = cost <= 0 || service.economy().has(player.getUniqueId(), cost);
                String formatted = service.economy().format(cost);
                String path = ok ? "hub.requirements.cost-ok" : "hub.requirements.cost-fail";
                String def = ok
                        ? " <green>✔ <gray>Cost: <white><cost> "
                        : " <red>✘ <gray>Cost: <white><cost> ";
                lines.add(line(gui, player, path, def, Placeholder.unparsed("cost", formatted)));
            }

            if (config.isCreationItemsEnabled()) {
                String okPrefix = string(gui, "hub.requirements.item-ok-prefix", " <green>✔ ");
                String failPrefix = string(gui, "hub.requirements.item-fail-prefix", " <red>✘ ");
                for (ItemRequirement req : config.getCreationItemRequirements()) {
                    Material mat = Material.matchMaterial(req.material());
                    if (mat == null || mat.isAir()) {
                        continue;
                    }
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

            if (!config.isCreationPermissionEnabled()
                    && !config.isCreationMoneyEnabled()
                    && !config.isCreationItemsEnabled()) {
                lines.add(line(gui, player, "hub.requirements.none", " <gray>None: free to create! "));
            }
        }

        return lines;
    }

    private static String string(GuiConfig gui, String path, String def) {
        return gui != null ? gui.string(path, def) : def;
    }

    private static Component line(GuiConfig gui, Player player, String path, String def,
                                  TagResolver... resolvers) {
        String raw = string(gui, path, def);
        return Text.parse(raw, player, resolvers);
    }

    public static String costPlaceholder(Player player) {
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) {
            return "0";
        }
        double cost = plugin.getConfigManager().isCreationMoneyEnabled()
                ? plugin.getConfigManager().getCreationMoneyCost()
                : 0;
        return ((GuildServiceImpl) plugin.getGuildService()).economy().format(cost);
    }

    public static TagResolver costResolver(Player player) {
        return Placeholder.unparsed("cost", costPlaceholder(player));
    }
}
