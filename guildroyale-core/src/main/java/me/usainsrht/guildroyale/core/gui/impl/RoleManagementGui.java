package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.GuildRole;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import me.usainsrht.guildroyale.core.adapter.ItemStackAdapter;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.gui.AbstractGui;
import me.usainsrht.guildroyale.core.gui.GuiItems;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
    private final int contentSlots;
    private final int createSlot;
    private final int backSlot;

    public RoleManagementGui(Guild guild, GuildMember viewer, GuiManager guiManager) {
        super(size(), title(guild));
        this.guild = guild;
        this.viewer = viewer;
        this.guiManager = guiManager;
        this.roles = guild.getRoles().stream()
                .sorted((a, b) -> Integer.compare(a.getIndex(), b.getIndex()))
                .toList();

        GuiConfig gui = GuiItems.config();
        this.contentSlots = gui != null ? gui.pageSize("roles.content-slots", 45) : 45;
        this.createSlot = gui != null ? gui.slot("roles.slots.create", 49) : 49;
        this.backSlot = gui != null ? gui.slot("roles.slots.back", 45) : 45;
    }

    private static int size() {
        GuiConfig gui = GuiItems.config();
        return gui != null ? gui.size("roles.size", 54) : 54;
    }

    private static Component title(Guild guild) {
        GuiConfig gui = GuiItems.config();
        if (gui == null) {
            return Component.text("Roles");
        }
        return gui.title("roles.title", Placeholder.unparsed("guild", guild.getName()));
    }

    @Override
    protected void build() {
        for (int i = 0; i < roles.size() && i < contentSlots; i++) {
            GuildRole role = roles.get(i);
            ItemStack icon = ItemStackAdapter.fromSerializable(role.getIcon());
            ItemStack template = GuiItems.get("roles-entry",
                    Placeholder.unparsed("role", role.getName()),
                    Placeholder.unparsed("index", String.valueOf(role.getIndex())),
                    Placeholder.unparsed("permissions", String.valueOf(role.getPermissions().size())));
            if (icon.getType().isAir()) {
                icon = template;
            } else {
                ItemMeta meta = template.getItemMeta();
                if (meta != null) {
                    icon.setItemMeta(meta);
                }
            }
            setSlot(i, icon);
        }

        boolean canManage = viewer.getRole().getIndex() == 0
                || viewer.getRole().hasPermission(GuildPermissionKey.ROLE_MANAGEMENT);
        if (canManage) {
            setSlot(createSlot, GuiItems.get("roles-create"));
        }

        setSlot(backSlot, GuiItems.get("gui-back"));
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return true;
        int slot = event.getRawSlot();
        if (slot == backSlot) {
            new GuildMainGui(guild, viewer, guiManager).open(player);
        } else if (slot < roles.size() && slot < contentSlots) {
            new RoleEditorGui(guild, roles.get(slot), viewer, guiManager).open(player);
        }
        return true;
    }
}
