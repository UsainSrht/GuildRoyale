package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.GuildRole;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import me.usainsrht.guildroyale.core.gui.AbstractGui;
import me.usainsrht.guildroyale.core.gui.GuiManager;
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
 * Displays and allows editing of a single guild role: its name, icon, and permissions.
 */
public final class RoleEditorGui extends AbstractGui {

    private final Guild guild;
    private final GuildRole role;
    private final GuildMember viewer;
    private final GuiManager guiManager;

    public RoleEditorGui(Guild guild, GuildRole role, GuildMember viewer, GuiManager guiManager) {
        super(54, "Edit Role: " + role.getName());
        this.guild = guild;
        this.role = role;
        this.viewer = viewer;
        this.guiManager = guiManager;
    }

    @Override
    protected void build() {
        // Role info
        setSlot(4, makeLabel("Role: " + role.getName(), Material.NAME_TAG, NamedTextColor.GOLD));

        // Permission toggles — one slot per permission key
        GuildPermissionKey[] keys = GuildPermissionKey.values();
        for (int i = 0; i < keys.length; i++) {
            GuildPermissionKey key = keys[i];
            boolean has = role.hasPermission(key);
            Material mat = has ? Material.LIME_DYE : Material.RED_DYE;
            NamedTextColor color = has ? NamedTextColor.GREEN : NamedTextColor.RED;
            setSlot(18 + i, makeLabel(
                    (has ? "✔ " : "✗ ") + key.name(),
                    mat, color
            ));
        }

        // Set icon button
        setSlot(46, makeButton(Material.PAINTING, "Set Icon", NamedTextColor.AQUA,
                List.of("§7Hold an item and click to set as role icon")));

        // Delete role (protected: index 0 cannot be deleted)
        if (role.getIndex() != 0) {
            setSlot(52, makeButton(Material.BARRIER, "Delete Role", NamedTextColor.RED,
                    List.of("§cRemove this role. Members will be reassigned.")));
        }

        // Back
        setSlot(45, makeButton(Material.ARROW, "Back", NamedTextColor.GRAY, List.of()));
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return true;
        int slot = event.getRawSlot();
        if (slot == 45) {
            new RoleManagementGui(guild, viewer, guiManager).open(player);
        }
        // Permission toggle clicks would call RoleService async
        return true;
    }

    private static ItemStack makeLabel(String label, Material mat, NamedTextColor color) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, color).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack makeButton(Material mat, String label, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(l -> Component.text(l).decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }
}
