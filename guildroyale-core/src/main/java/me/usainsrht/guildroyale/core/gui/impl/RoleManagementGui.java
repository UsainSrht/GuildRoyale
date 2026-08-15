package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.GuildRole;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.adapter.ItemStackAdapter;
import me.usainsrht.guildroyale.core.config.ConfigManager;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.gui.GuiItems;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import me.usainsrht.guildroyale.core.gui.StandardListGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Lists all roles in the guild. Clicking a role opens {@link RoleEditorGui}.
 * Top action opens {@link PermissionsGui}.
 */
public final class RoleManagementGui extends StandardListGui<GuildRole> {

    private final Guild guild;
    private final GuildMember viewer;
    private final GuiManager guiManager;
    private final boolean canManage;

    public RoleManagementGui(Guild guild, GuildMember viewer, GuiManager guiManager) {
        this(guild, viewer, guiManager, 0);
    }

    public RoleManagementGui(Guild guild, GuildMember viewer, GuiManager guiManager, int page) {
        super(size(), title(guild, page), getSortedRoles(guild), page, "roles");
        this.guild = guild;
        this.viewer = viewer;
        this.guiManager = guiManager;
        this.canManage = viewer.getRole().getIndex() == 0
                || viewer.getRole().hasPermission(GuildPermissionKey.ROLE_MANAGEMENT);
    }

    private static List<GuildRole> getSortedRoles(Guild guild) {
        return guild.getRoles().stream()
                .sorted((a, b) -> Integer.compare(a.getIndex(), b.getIndex()))
                .toList();
    }

    private static int size() {
        GuiConfig gui = GuiItems.config();
        return gui != null ? gui.size("roles.size", 54) : 54;
    }

    private static Component title(Guild guild, int page) {
        GuiConfig gui = GuiItems.config();
        List<GuildRole> roles = getSortedRoles(guild);
        int innerCount = StandardListGui.calculateInnerSlots(size() / 9).size();
        int maxPage = Math.max(1, (int) Math.ceil(Math.max(roles.size(), 1) / (double) innerCount));
        int safePage = Math.max(0, Math.min(page, maxPage - 1)) + 1;
        if (gui == null) {
            return formatStandardTitle("Roles");
        }
        return gui.listTitle("roles", safePage, maxPage,
                Placeholder.unparsed("guild", guild.getName()),
                Placeholder.unparsed("page", String.valueOf(safePage)),
                Placeholder.unparsed("max_page", String.valueOf(maxPage)));
    }

    @Override
    protected ItemStack renderItem(GuildRole role, int index) {
        ItemStack icon = ItemStackAdapter.fromSerializable(role.getIcon());
        long members = guild.getMembers().stream()
                .filter(m -> m.getRole().getIndex() == role.getIndex())
                .count();
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        ConfigManager configManager = plugin != null ? plugin.getConfigManager() : null;
        String colorName = configManager != null ? configManager.getRoleColorName(role.getColor()) : role.getColor().name();

        TagResolver roleResolver = plugin != null
                ? plugin.getTagService().roleResolver(role, guild, null)
                : Placeholder.unparsed("role", role.getName());

        ItemStack template = GuiItems.get("roles-entry",
                roleResolver,
                Placeholder.unparsed("index", String.valueOf(role.getIndex())),
                Placeholder.unparsed("members", String.valueOf(members)),
                Placeholder.parsed("color", colorName));
        if (icon.getType().isAir()) {
            Material dye = Material.matchMaterial(role.getColor().dyeMaterial());
            if (dye != null) {
                icon = new ItemStack(dye);
            } else {
                icon = template;
            }
        }
        ItemMeta meta = template.getItemMeta();
        if (meta != null && icon != template) {
            icon.setItemMeta(meta);
        }
        return icon == template ? template : icon;
    }

    @Override
    protected void onItemClick(InventoryClickEvent event, GuildRole role, int index) {
        if (event.getWhoClicked() instanceof Player player) {
            new RoleEditorGui(guild, role, viewer, guiManager)
                    .returnTo(p -> new RoleManagementGui(guild, viewer, guiManager, page)
                            .returnTo(this)
                            .open(p))
                    .open(player);
        }
    }

    @Override
    protected @Nullable ItemStack getAction1Item() {
        return canManage ? GuiItems.get("roles-create") : null;
    }

    @Override
    protected @Nullable ItemStack getTopActionItem() {
        return GuiItems.get("roles-permissions");
    }

    @Override
    protected void onAction1(Player player) {
        if (!canManage) return;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return;

        player.closeInventory();
        plugin.getDialogManager().openRoleNameDialog(player, "Enter a name for the new role", name ->
                plugin.getScheduler().runAsync(() ->
                        plugin.getRoleService().createRole(guild.getId(), player.getUniqueId(), name)
                                .thenCompose(result -> plugin.getGuildService().getGuild(guild.getId())
                                        .thenAccept(opt -> plugin.getScheduler().runForEntity(player, () -> {
                                            switch (result) {
                                                case ActionResult.Success s -> {
                                                    plugin.getMessages().send(player, "role-created",
                                                            Placeholder.unparsed("role", name));
                                                    if (opt.isPresent()) {
                                                        Guild fresh = opt.get();
                                                        GuildMember freshViewer = fresh.getMember(viewer.getPlayerId()).orElse(viewer);
                                                        new RoleManagementGui(fresh, freshViewer, guiManager, page)
                                                                .returnTo(this)
                                                                .open(player);
                                                    }
                                                }
                                                case ActionResult.Failure f ->
                                                        plugin.getMessages().send(player, f.reason());
                                            }
                                        })))
                )
        );
    }

    @Override
    protected void onTopAction(Player player) {
        new PermissionsGui(guild, viewer, guiManager)
                .returnTo(p -> new RoleManagementGui(guild, viewer, guiManager, page)
                        .returnTo(this)
                        .open(p))
                .open(player);
    }

    @Override
    protected void onPreviousPage(Player player) {
        new RoleManagementGui(guild, viewer, guiManager, page - 1).returnTo(this).open(player);
    }

    @Override
    protected void onNextPage(Player player) {
        new RoleManagementGui(guild, viewer, guiManager, page + 1).returnTo(this).open(player);
    }
}
