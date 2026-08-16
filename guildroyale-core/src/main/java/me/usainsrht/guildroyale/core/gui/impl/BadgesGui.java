package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.BadgeDefinition;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.feature.GuildFeature;
import me.usainsrht.guildroyale.core.gui.GuiItems;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import me.usainsrht.guildroyale.core.gui.StandardListGui;
import me.usainsrht.guildroyale.core.message.Text;
import me.usainsrht.guildroyale.core.service.GuildServiceImpl;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists available guild badges and allows authorized members to buy or equip them.
 */
public final class BadgesGui extends StandardListGui<BadgeDefinition> {

    private final Guild guild;
    private final GuildMember viewer;
    private final GuiManager guiManager;
    private final boolean canManage;

    public BadgesGui(Guild guild, GuildMember viewer, GuiManager guiManager) {
        this(guild, viewer, guiManager, 0);
    }

    public BadgesGui(Guild guild, GuildMember viewer, GuiManager guiManager, int page) {
        super(size(), title(page), getBadgesList(), page, "badges");
        this.guild = guild;
        this.viewer = viewer;
        this.guiManager = guiManager;
        this.canManage = viewer.getRole().getIndex() == 0
                || viewer.getRole().hasPermission(GuildPermissionKey.BADGE_MANAGE);
    }

    private static List<BadgeDefinition> getBadgesList() {
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return List.of();
        return new ArrayList<>(plugin.getConfigManager().getBadges().values());
    }

    private static int size() {
        GuiConfig gui = GuiItems.config();
        return gui != null ? gui.size("badges.size", 54) : 54;
    }

    private static Component title(int page) {
        GuiConfig gui = GuiItems.config();
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        int total = plugin != null ? plugin.getConfigManager().getBadges().size() : 0;
        int innerCount = StandardListGui.calculateInnerSlots(size() / 9).size();
        int maxPage = Math.max(1, (int) Math.ceil(Math.max(total, 1) / (double) innerCount));
        int safePage = Math.max(0, Math.min(page, maxPage - 1)) + 1;
        if (gui == null) {
            return formatStandardTitle("Guild Badges");
        }
        return gui.listTitle("badges", safePage, maxPage,
                Placeholder.unparsed("page", String.valueOf(safePage)),
                Placeholder.unparsed("max_page", String.valueOf(maxPage)));
    }

    @Override
    protected ItemStack renderItem(BadgeDefinition badge, int index) {
        ItemStack item = badge.icon().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.parse(badge.displayName()).decoration(TextDecoration.ITALIC, false));

            GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
            GuiConfig gui = GuiItems.config();

            boolean isOwned = guild.ownsBadge(badge.id());
            boolean isActive = badge.id().equals(guild.getActiveBadgeId());

            int featureUnlock = plugin != null
                    ? plugin.getConfigManager().getFeatureUnlockLevel(GuildFeature.BADGE)
                    : 1;
            int reqLevel = Math.max(badge.level(), featureUnlock);
            boolean isLevelLocked = guild.getLevel() < reqLevel;

            boolean hasMoney = true;
            String formattedCost = String.valueOf(badge.cost());
            if (plugin != null) {
                GuildServiceImpl service = (GuildServiceImpl) plugin.getGuildService();
                formattedCost = service.economy().format(badge.cost());
                if (badge.isBuyable()) {
                    hasMoney = service.economy().has(viewer.getPlayerId(), badge.cost());
                }
            }

            String templateKey;
            if (isActive) {
                templateKey = "selected";
            } else if (isOwned) {
                templateKey = "owned";
            } else if (isLevelLocked) {
                templateKey = "not-enough-level";
            } else if (!badge.isBuyable()) {
                templateKey = "not-buyable";
            } else if (!hasMoney) {
                templateKey = "no-money";
            } else {
                templateKey = "buyable";
            }

            OfflinePlayer op = Bukkit.getOfflinePlayer(viewer.getPlayerId());
            String viewerName = op.getName() != null ? op.getName() : viewer.getPlayerId().toString().substring(0, 8);

            TagResolver resolvers = TagResolver.resolver(
                    Placeholder.parsed("symbol", badge.symbol()),
                    Placeholder.parsed("display", badge.displayName()),
                    Placeholder.unparsed("player", viewerName),
                    Placeholder.parsed("preview_symbol", " " + badge.symbol()),
                    Placeholder.unparsed("cost", formattedCost),
                    Placeholder.unparsed("required_level", String.valueOf(reqLevel))
            );

            List<Component> lore = gui != null ? gui.lore("badges.lore-templates." + templateKey, resolvers) : List.of();
            if (lore.isEmpty()) {
                lore = defaultLore(templateKey, badge, viewerName, formattedCost, reqLevel);
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<Component> defaultLore(String templateKey, BadgeDefinition badge, String viewerName, String cost, int reqLevel) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Text.parse(" <gray>Symbol: </gray>" + badge.symbol()).decoration(TextDecoration.ITALIC, false));
        lore.add(Text.parse(" <gray>Preview: </gray>" + viewerName + " " + badge.symbol()).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        switch (templateKey) {
            case "selected" -> {
                lore.add(Text.parse(" <green><bold>ACTIVE</bold></green>").decoration(TextDecoration.ITALIC, false));
                if (canManage) lore.add(Text.parse(" <yellow>Click to unequip").decoration(TextDecoration.ITALIC, false));
            }
            case "owned" -> {
                lore.add(Text.parse(" <yellow><bold>OWNED</bold></yellow>").decoration(TextDecoration.ITALIC, false));
                if (canManage) lore.add(Text.parse(" <yellow>Click to equip").decoration(TextDecoration.ITALIC, false));
            }
            case "not-enough-level" -> {
                lore.add(Text.parse(" <red>Locked").decoration(TextDecoration.ITALIC, false));
                lore.add(Text.parse(" <gray>Unlocks at guild level <yellow>" + reqLevel + "</yellow>").decoration(TextDecoration.ITALIC, false));
            }
            case "no-money" -> {
                lore.add(Text.parse(" <gray>Cost: <white>" + cost + "</white>").decoration(TextDecoration.ITALIC, false));
                lore.add(Text.parse(" <red>Cannot afford").decoration(TextDecoration.ITALIC, false));
            }
            case "not-buyable" -> lore.add(Text.parse(" <dark_gray>Grant-Only Badge").decoration(TextDecoration.ITALIC, false));
            default -> { // buyable
                lore.add(Text.parse(" <gray>Cost: <white>" + cost + "</white>").decoration(TextDecoration.ITALIC, false));
                if (canManage) lore.add(Text.parse(" <yellow>Click to purchase").decoration(TextDecoration.ITALIC, false));
            }
        }
        lore.add(Component.empty());
        return lore;
    }

    @Override
    protected void onItemClick(InventoryClickEvent event, BadgeDefinition badge, int index) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return;

        if (!canManage) {
            plugin.getMessages().send(player, "no-permission");
            return;
        }

        int featureUnlock = plugin.getConfigManager().getFeatureUnlockLevel(GuildFeature.BADGE);
        int reqLevel = Math.max(badge.level(), featureUnlock);
        if (guild.getLevel() < reqLevel) {
            plugin.getMessages().send(player, "feature-locked",
                    Placeholder.unparsed("feature", "badge"),
                    Placeholder.unparsed("level", String.valueOf(reqLevel)));
            return;
        }

        boolean isOwned = guild.ownsBadge(badge.id());
        boolean isActive = badge.id().equals(guild.getActiveBadgeId());

        if (isActive) {
            // Unequip
            plugin.getScheduler().runAsync(() ->
                    plugin.getGuildService().equipBadge(guild.getId(), player.getUniqueId(), null)
                            .thenCompose(result -> plugin.getGuildService().getGuild(guild.getId())
                                    .thenAccept(opt -> plugin.getScheduler().runForEntity(player, () -> {
                                        switch (result) {
                                            case ActionResult.Success s -> {
                                                plugin.getMessages().send(player, "badge-unequipped",
                                                        Placeholder.unparsed("badge", badge.displayName()));
                                                if (opt.isPresent()) {
                                                    Guild fresh = opt.get();
                                                    GuildMember freshViewer = fresh.getMember(viewer.getPlayerId()).orElse(viewer);
                                                    new BadgesGui(fresh, freshViewer, guiManager, page)
                                                            .returnTo(this)
                                                            .open(player);
                                                }
                                            }
                                            case ActionResult.Failure f -> plugin.getMessages().send(player, f.reason());
                                        }
                                    })))
            );
        } else if (isOwned) {
            // Equip
            plugin.getScheduler().runAsync(() ->
                    plugin.getGuildService().equipBadge(guild.getId(), player.getUniqueId(), badge.id())
                            .thenCompose(result -> plugin.getGuildService().getGuild(guild.getId())
                                    .thenAccept(opt -> plugin.getScheduler().runForEntity(player, () -> {
                                        switch (result) {
                                            case ActionResult.Success s -> {
                                                plugin.getMessages().send(player, "badge-equipped",
                                                        Placeholder.unparsed("badge", badge.displayName()));
                                                if (opt.isPresent()) {
                                                    Guild fresh = opt.get();
                                                    GuildMember freshViewer = fresh.getMember(viewer.getPlayerId()).orElse(viewer);
                                                    new BadgesGui(fresh, freshViewer, guiManager, page)
                                                            .returnTo(this)
                                                            .open(player);
                                                }
                                            }
                                            case ActionResult.Failure f -> plugin.getMessages().send(player, f.reason());
                                        }
                                    })))
            );
        } else if (badge.isBuyable()) {
            // Buy
            plugin.getScheduler().runAsync(() ->
                    plugin.getGuildService().buyBadge(guild.getId(), player.getUniqueId(), badge.id())
                            .thenCompose(result -> plugin.getGuildService().getGuild(guild.getId())
                                    .thenAccept(opt -> plugin.getScheduler().runForEntity(player, () -> {
                                        switch (result) {
                                            case ActionResult.Success s -> {
                                                plugin.getMessages().send(player, "badge-bought",
                                                        Placeholder.unparsed("badge", badge.displayName()));
                                                if (opt.isPresent()) {
                                                    Guild fresh = opt.get();
                                                    GuildMember freshViewer = fresh.getMember(viewer.getPlayerId()).orElse(viewer);
                                                    new BadgesGui(fresh, freshViewer, guiManager, page)
                                                            .returnTo(this)
                                                            .open(player);
                                                }
                                            }
                                            case ActionResult.Failure f -> plugin.getMessages().send(player, f.reason());
                                        }
                                    })))
            );
        } else {
            // Grant-only badge cannot be bought
            plugin.getMessages().send(player, "badge-not-buyable");
        }
    }

    @Override
    protected void onPreviousPage(Player player) {
        new BadgesGui(guild, viewer, guiManager, page - 1).returnTo(this).open(player);
    }

    @Override
    protected void onNextPage(Player player) {
        new BadgesGui(guild, viewer, guiManager, page + 1).returnTo(this).open(player);
    }
}
