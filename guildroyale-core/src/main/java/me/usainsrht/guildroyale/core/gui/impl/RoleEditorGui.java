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
import me.usainsrht.guildroyale.core.gui.AbstractGui;
import me.usainsrht.guildroyale.core.gui.GuiItems;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import me.usainsrht.guildroyale.core.gui.StandardListGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

/**
 * Edits a single role: icon, color, name, and lists members holding the role.
 * Permissions are managed in {@link PermissionsGui}.
 */
public final class RoleEditorGui extends AbstractGui {

    private final Guild guild;
    private final GuildRole role;
    private final GuildMember viewer;
    private final GuiManager guiManager;
    private final int infoSlot;
    private final int setIconSlot;
    private final int setColorSlot;
    private final int renameSlot;
    private final int deleteSlot;
    private final int backSlot;
    private final List<Integer> memberSlots;
    private final List<GuildMember> roleMembers;
    private final boolean canManage;

    public RoleEditorGui(Guild guild, GuildRole role, GuildMember viewer, GuiManager guiManager) {
        super(size(), title(role));
        this.guild = guild;
        this.role = role;
        this.viewer = viewer;
        this.guiManager = guiManager;

        GuiConfig gui = GuiItems.config();
        this.infoSlot = gui != null ? gui.slot("role-editor.slots.info", 4) : 4;
        this.setIconSlot = gui != null ? gui.slot("role-editor.slots.set-icon", 45) : 45;
        this.setColorSlot = gui != null ? gui.slot("role-editor.slots.set-color", 47) : 47;
        this.renameSlot = gui != null ? gui.slot("role-editor.slots.rename", 51) : 51;
        this.deleteSlot = gui != null ? gui.slot("role-editor.slots.delete", 53) : 53;
        this.backSlot = gui != null ? gui.slot("role-editor.slots.back", 49) : 49;
        this.memberSlots = StandardListGui.calculateInnerSlots(size() / 9);
        this.roleMembers = guild.getMembers().stream()
                .filter(m -> m.getRole().getIndex() == role.getIndex())
                .sorted((a, b) -> Long.compare(b.getContribution(), a.getContribution()))
                .toList();
        this.canManage = viewer.getRole().getIndex() == 0
                || viewer.getRole().hasPermission(GuildPermissionKey.ROLE_MANAGEMENT);
    }

    private static int size() {
        GuiConfig gui = GuiItems.config();
        return gui != null ? gui.size("role-editor.size", 54) : 54;
    }

    private static Component title(GuildRole role) {
        GuiConfig gui = GuiItems.config();
        if (gui == null) {
            return Component.text("Edit Role");
        }
        return gui.title("role-editor.title", Placeholder.unparsed("role", role.getName()));
    }

    @Override
    protected void build() {
        fillBorder(GuiItems.filler());

        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        ConfigManager configManager = plugin != null ? plugin.getConfigManager() : null;
        String colorName = configManager != null ? configManager.getRoleColorName(role.getColor()) : role.getColor().name();

        ItemStack info = GuiItems.get("role-editor-info",
                Placeholder.unparsed("role", role.getName()),
                Placeholder.parsed("color", colorName),
                Placeholder.unparsed("members", String.valueOf(roleMembers.size())));
        ItemStack roleIcon = ItemStackAdapter.fromSerializable(role.getIcon());
        if (!roleIcon.getType().isAir()) {
            var meta = info.getItemMeta();
            if (meta != null) {
                roleIcon.setItemMeta(meta);
                info = roleIcon;
            }
        }
        setSlot(infoSlot, info);

        for (int i = 0; i < memberSlots.size() && i < roleMembers.size(); i++) {
            setSlot(memberSlots.get(i), renderMember(roleMembers.get(i)));
        }

        if (canManage) {
            ItemStack setIconItem = GuiItems.get("role-editor-set-icon");
            if (!roleIcon.getType().isAir()) {
                ItemStack iconItem = roleIcon.clone();
                var meta = setIconItem.getItemMeta();
                if (meta != null) {
                    iconItem.setItemMeta(meta);
                    setIconItem = iconItem;
                }
            }
            setSlot(setIconSlot, setIconItem);
            setSlot(setColorSlot, colorButton());
            setSlot(renameSlot, GuiItems.get("role-editor-rename"));
            if (role.getIndex() != 0) {
                setSlot(deleteSlot, GuiItems.get("role-editor-delete"));
            }
        }

        setSlot(backSlot, navBackItem());
    }

    private ItemStack colorButton() {
        Material dye = Material.matchMaterial(role.getColor().dyeMaterial());
        if (dye == null) dye = Material.WHITE_DYE;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        ConfigManager configManager = plugin != null ? plugin.getConfigManager() : null;
        String colorName = configManager != null ? configManager.getRoleColorName(role.getColor()) : role.getColor().name();
        ItemStack template = GuiItems.get("role-editor-set-color",
                Placeholder.parsed("color", colorName));
        ItemStack item = new ItemStack(dye);
        var meta = template.getItemMeta();
        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack renderMember(GuildMember member) {
        var offline = Bukkit.getOfflinePlayer(member.getPlayerId());
        String name = offline.getName() != null
                ? offline.getName()
                : member.getPlayerId().toString().substring(0, 8);
        ItemStack head = GuiItems.get("role-editor-member",
                Placeholder.unparsed("player", name),
                Placeholder.unparsed("contribution", String.valueOf(member.getContribution())));
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(offline);
            head.setItemMeta(meta);
        }
        return head;
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return true;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        int slot = event.getRawSlot();

        if (slot == backSlot) {
            navigateBack(player);
            return true;
        }
        if (!canManage || plugin == null) {
            return true;
        }

        if (slot == setIconSlot) {
            changeRoleIcon(player, plugin, event.getCursor());
        } else if (slot == setColorSlot) {
            new RoleColorGui(guild, role, viewer, guiManager, this)
                    .returnTo(p -> reopen(p, guild))
                    .open(player);
        } else if (slot == renameSlot) {
            openRename(player, plugin);
        } else if (slot == deleteSlot && role.getIndex() != 0) {
            deleteRole(player, plugin);
        }
        return true;
    }

    private void changeRoleIcon(Player player, GuildRoyalePlugin plugin, ItemStack cursorItem) {
        if (cursorItem == null || cursorItem.getType().isAir()) {
            plugin.getMessages().send(player, "icon-invalid");
            return;
        }
        var icon = ItemStackAdapter.toSerializableItemType(cursorItem);
        plugin.getScheduler().runAsync(() ->
                plugin.getRoleService().setRoleIcon(guild.getId(), player.getUniqueId(), role.getIndex(), icon)
                        .thenCompose(result -> afterMutation(player, plugin, result, "icon-updated"))
        );
    }

    private void openRename(Player player, GuildRoyalePlugin plugin) {
        player.closeInventory();
        plugin.getDialogManager().openRoleNameDialog(player, "Enter a new name for " + role.getName(), newName ->
                plugin.getScheduler().runAsync(() ->
                        plugin.getRoleService().renameRole(guild.getId(), player.getUniqueId(), role.getIndex(), newName)
                                .thenCompose(result -> afterMutation(player, plugin, result, "role-renamed",
                                        Placeholder.unparsed("role", newName)))
                )
        );
    }

    private void deleteRole(Player player, GuildRoyalePlugin plugin) {
        String roleName = role.getName();
        plugin.getScheduler().runAsync(() ->
                plugin.getRoleService().deleteRole(guild.getId(), player.getUniqueId(), role.getIndex())
                        .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                            switch (result) {
                                case ActionResult.Success s -> {
                                    plugin.getMessages().send(player, "role-deleted",
                                            Placeholder.unparsed("role", roleName));
                                    navigateBack(player);
                                }
                                case ActionResult.Failure f ->
                                        plugin.getMessages().send(player, f.reason());
                            }
                        }))
        );
    }

    private java.util.concurrent.CompletableFuture<Void> afterMutation(
            Player player, GuildRoyalePlugin plugin, ActionResult result, String successKey,
            net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        return plugin.getGuildService().getGuild(guild.getId()).thenAccept(opt ->
                plugin.getScheduler().runForEntity(player, () -> {
                    switch (result) {
                        case ActionResult.Success s -> {
                            plugin.getMessages().send(player, successKey, resolvers);
                            if (opt.isPresent()) {
                                reopen(player, opt.get());
                            }
                        }
                        case ActionResult.Failure f ->
                                plugin.getMessages().send(player, f.reason());
                    }
                })
        );
    }

    private void reopen(Player player, Guild fresh) {
        GuildRole updated = fresh.getRole(role.getIndex()).orElse(role);
        GuildMember freshViewer = fresh.getMember(viewer.getPlayerId()).orElse(viewer);
        new RoleEditorGui(fresh, updated, freshViewer, guiManager)
                .returnTo(this)
                .open(player);
    }
}
