package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.GuildRole;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.gui.AbstractGui;
import me.usainsrht.guildroyale.core.gui.GuiItems;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import me.usainsrht.guildroyale.core.gui.StandardListGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
    private final int infoSlot;
    private final int permissionsStart;
    private final int setIconSlot;
    private final int deleteSlot;
    private final int backSlot;

    public RoleEditorGui(Guild guild, GuildRole role, GuildMember viewer, GuiManager guiManager) {
        super(size(), title(role));
        this.guild = guild;
        this.role = role;
        this.viewer = viewer;
        this.guiManager = guiManager;

        GuiConfig gui = GuiItems.config();
        this.infoSlot = gui != null ? gui.slot("role-editor.slots.info", 4) : 4;
        this.permissionsStart = gui != null ? gui.slot("role-editor.slots.permissions-start", 10) : 10;
        this.setIconSlot = gui != null ? gui.slot("role-editor.slots.set-icon", 47) : 47;
        this.deleteSlot = gui != null ? gui.slot("role-editor.slots.delete", 51) : 51;
        this.backSlot = gui != null ? gui.slot("role-editor.slots.back", 49) : 49;
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

        setSlot(infoSlot, GuiItems.get("role-editor-info",
                Placeholder.unparsed("role", role.getName())));

        GuiConfig gui = GuiItems.config();
        String enabledTpl = gui != null
                ? gui.string("role-editor.permission-enabled", "<green><bold>✔ <permission>")
                : "<green><bold>✔ <permission>";
        String disabledTpl = gui != null
                ? gui.string("role-editor.permission-disabled", "<red><bold>✗ <permission>")
                : "<red><bold>✗ <permission>";
        Material enabledMat = gui != null
                ? gui.material("role-editor.materials.permission-enabled", Material.LIME_DYE)
                : Material.LIME_DYE;
        Material disabledMat = gui != null
                ? gui.material("role-editor.materials.permission-disabled", Material.RED_DYE)
                : Material.RED_DYE;

        GuildPermissionKey[] keys = GuildPermissionKey.values();
        MiniMessage mm = MiniMessage.miniMessage();
        List<Integer> contentSlots = StandardListGui.calculateInnerSlots(size / 9);
        int startIdx = 0;
        for (int i = 0; i < contentSlots.size(); i++) {
            if (contentSlots.get(i) >= permissionsStart) {
                startIdx = i;
                break;
            }
        }
        for (int i = 0; i < keys.length && startIdx + i < contentSlots.size(); i++) {
            GuildPermissionKey key = keys[i];
            boolean has = role.hasPermission(key);
            ItemStack item = new ItemStack(has ? enabledMat : disabledMat);
            ItemMeta meta = item.getItemMeta();
            String tpl = has ? enabledTpl : disabledTpl;
            meta.displayName(mm.deserialize(tpl, Placeholder.unparsed("permission", key.name()))
                    .decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
            setSlot(contentSlots.get(startIdx + i), item);
        }

        setSlot(setIconSlot, GuiItems.get("role-editor-set-icon"));

        if (role.getIndex() != 0) {
            setSlot(deleteSlot, GuiItems.get("role-editor-delete"));
        }

        setSlot(backSlot, navBackItem());
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return true;
        int slot = event.getRawSlot();
        if (slot == backSlot) {
            navigateBack(player);
        }
        // Permission toggle clicks would call RoleService async
        return true;
    }
}
