package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.GuildRole;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.ConfigManager;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.gui.GuiItems;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import me.usainsrht.guildroyale.core.gui.StandardListGui;
import me.usainsrht.guildroyale.core.message.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Lists each guild permission with a role threshold selector.
 * Left click moves the selected role up (stricter); right click moves it down (looser).
 */
public final class PermissionsGui extends StandardListGui<GuildPermissionKey> {

    private final Guild guild;
    private final GuildMember viewer;
    private final GuiManager guiManager;
    private final boolean canManage;

    public PermissionsGui(Guild guild, GuildMember viewer, GuiManager guiManager) {
        this(guild, viewer, guiManager, 0);
    }

    public PermissionsGui(Guild guild, GuildMember viewer, GuiManager guiManager, int page) {
        super(size(), title(page), Arrays.asList(GuildPermissionKey.values()), page, "permissions");
        this.guild = guild;
        this.viewer = viewer;
        this.guiManager = guiManager;
        this.canManage = viewer.getRole().getIndex() == 0
                || viewer.getRole().hasPermission(GuildPermissionKey.ROLE_MANAGEMENT);
    }

    private static int size() {
        GuiConfig gui = GuiItems.config();
        return gui != null ? gui.size("permissions.size", 54) : 54;
    }

    private static Component title(int page) {
        GuiConfig gui = GuiItems.config();
        int innerCount = StandardListGui.calculateInnerSlots(size() / 9).size();
        int total = GuildPermissionKey.values().length;
        int maxPage = Math.max(1, (int) Math.ceil(Math.max(total, 1) / (double) innerCount));
        int safePage = Math.max(0, Math.min(page, maxPage - 1)) + 1;
        if (gui == null) {
            return formatStandardTitle("Permissions");
        }
        return gui.listTitle("permissions", safePage, maxPage,
                Placeholder.unparsed("page", String.valueOf(safePage)),
                Placeholder.unparsed("max_page", String.valueOf(maxPage)));
    }

    @Override
    protected ItemStack renderItem(GuildPermissionKey key, int index) {
        int selected = currentMinRole(key);
        List<GuildRole> roles = sortedRoles();

        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        ConfigManager config = plugin != null ? plugin.getConfigManager() : null;
        GuiConfig gui = GuiItems.config();

        String nameTpl = gui != null
                ? gui.string("permissions.entry-name", "<gradient:#f4d03f:#f9e79f><bold><permission></bold></gradient>")
                : "<gradient:#f4d03f:#f9e79f><bold><permission></bold></gradient>";
        String roleLine = gui != null
                ? gui.string("permissions.role-line", " <gray><role> ")
                : " <gray><role> ";
        String roleSelected = gui != null
                ? gui.string("permissions.role-line-selected", " <green><role> <dark_gray>« ")
                : " <green><role> <dark_gray>« ";
        String hint = gui != null
                ? gui.string("permissions.click-hint", " <yellow>Left/Right click to change ")
                : " <yellow>Left/Right click to change ";

        Material mat = config != null
                ? config.getPermissionIcon(key)
                : (gui != null ? gui.material("permissions.materials.entry", Material.PAPER) : Material.PAPER);

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        String pretty = config != null ? config.getPermissionName(key) : prettyName(key);
        meta.displayName(Text.parse(nameTpl, Placeholder.parsed("permission", pretty))
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        for (GuildRole role : roles) {
            String tpl = role.getIndex() == selected ? roleSelected : roleLine;
            String line = tpl
                    .replace("<role>", role.getColor().miniMessage() + role.getName() + "<reset>");
            lore.add(Text.parse(line).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        if (canManage) {
            lore.add(Text.parse(hint).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    protected void onItemClick(InventoryClickEvent event, GuildPermissionKey key, int index) {
        if (!(event.getWhoClicked() instanceof Player player) || !canManage) return;

        int current = currentMinRole(key);
        int maxIndex = sortedRoles().stream().mapToInt(GuildRole::getIndex).max().orElse(0);
        int next;
        ClickType click = event.getClick();
        if (click.isLeftClick()) {
            next = Math.max(0, current - 1);
        } else if (click.isRightClick()) {
            next = Math.min(maxIndex, current + 1);
        } else {
            return;
        }
        if (next == current) return;

        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return;

        plugin.getScheduler().runAsync(() ->
                plugin.getRoleService().setPermissionMinRole(guild.getId(), player.getUniqueId(), key, next)
                        .thenCompose(result -> plugin.getGuildService().getGuild(guild.getId())
                                .thenAccept(opt -> plugin.getScheduler().runForEntity(player, () -> {
                                    switch (result) {
                                        case ActionResult.Success s -> {
                                            if (opt.isPresent()) {
                                                Guild fresh = opt.get();
                                                GuildMember freshViewer = fresh.getMember(viewer.getPlayerId()).orElse(viewer);
                                                new PermissionsGui(fresh, freshViewer, guiManager, page)
                                                        .returnTo(this)
                                                        .open(player);
                                            }
                                        }
                                        case ActionResult.Failure f ->
                                                plugin.getMessages().send(player, f.reason());
                                    }
                                })))
        );
    }

    /**
     * Lowest-rank (highest index) role that currently holds the permission.
     * Defaults to 0 (leader only) when nobody below leader has it.
     */
    private int currentMinRole(GuildPermissionKey key) {
        int threshold = 0;
        for (GuildRole role : sortedRoles()) {
            if (role.hasPermission(key)) {
                threshold = Math.max(threshold, role.getIndex());
            }
        }
        return threshold;
    }

    private List<GuildRole> sortedRoles() {
        return guild.getRoles().stream()
                .sorted(Comparator.comparingInt(GuildRole::getIndex))
                .toList();
    }

    private static String prettyName(GuildPermissionKey key) {
        String raw = key.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = raw.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    @Override
    protected void onPreviousPage(Player player) {
        new PermissionsGui(guild, viewer, guiManager, page - 1).returnTo(this).open(player);
    }

    @Override
    protected void onNextPage(Player player) {
        new PermissionsGui(guild, viewer, guiManager, page + 1).returnTo(this).open(player);
    }
}
