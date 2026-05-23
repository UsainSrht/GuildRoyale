package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.GuildRole;
import me.usainsrht.guildroyale.core.adapter.ItemStackAdapter;
import me.usainsrht.guildroyale.core.gui.AbstractGui;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Lists all roles in the guild. Clicking a role opens {@link RoleEditorGui}.
 */
public final class RoleManagementGui extends AbstractGui {

    private final Guild guild;
    private final GuildMember viewer;
    private final GuiManager guiManager;
    private final List<GuildRole> roles;

    public RoleManagementGui(Guild guild, GuildMember viewer, GuiManager guiManager) {
        super(54, "Roles — " + guild.getName());
        this.guild = guild;
        this.viewer = viewer;
        this.guiManager = guiManager;
        this.roles = guild.getRoles().stream()
                .sorted((a, b) -> Integer.compare(a.getIndex(), b.getIndex()))
                .toList();
    }

    @Override
    protected void build() {
        for (int i = 0; i < roles.size() && i < 45; i++) {
            GuildRole role = roles.get(i);
            ItemStack icon = ItemStackAdapter.fromSerializable(role.getIcon());
            if (icon.getType().isAir()) icon = new ItemStack(Material.NAME_TAG);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(role.getName(), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("§7Index: §f" + role.getIndex()).decoration(TextDecoration.ITALIC, false),
                    Component.text("§7Permissions: §f" + role.getPermissions().size()).decoration(TextDecoration.ITALIC, false),
                    Component.text("§eClick to edit").decoration(TextDecoration.ITALIC, false)
            ));
            icon.setItemMeta(meta);
            setSlot(i, icon);
        }

        // Add role button (if not leader and has permission)
        boolean canManage = viewer.getRole().getIndex() == 0 ||
                viewer.getRole().hasPermission(GuildPermissionKey.ROLE_MANAGEMENT);
        if (canManage) {
            ItemStack addBtn = new ItemStack(Material.LIME_DYE);
            ItemMeta addMeta = addBtn.getItemMeta();
            addMeta.displayName(Component.text("+ Create New Role", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            addBtn.setItemMeta(addMeta);
            setSlot(49, addBtn);
        }

        // Back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("Back", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(backMeta);
        setSlot(45, back);
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return true;
        int slot = event.getRawSlot();
        if (slot == 45) {
            new GuildMainGui(guild, viewer, guiManager).open(player);
        } else if (slot < roles.size()) {
            new RoleEditorGui(guild, roles.get(slot), viewer, guiManager).open(player);
        }
        return true;
    }
}
