package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.GuildRole;
import me.usainsrht.guildroyale.api.domain.RoleColor;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.ConfigManager;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.gui.AbstractGui;
import me.usainsrht.guildroyale.core.gui.GuiItems;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import me.usainsrht.guildroyale.core.message.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

/**
 * Picks one of the 16 Minecraft dye colors for a role.
 */
public final class RoleColorGui extends AbstractGui {

    private static final int[] DYE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            30, 31
    };

    private final Guild guild;
    private final GuildRole role;
    private final GuildMember viewer;
    private final GuiManager guiManager;
    private final AbstractGui parentEditor;
    private final int backSlot;
    private final Map<Integer, RoleColor> slotColors = new HashMap<>();

    public RoleColorGui(Guild guild, GuildRole role, GuildMember viewer,
                        GuiManager guiManager, AbstractGui parentEditor) {
        super(size(), title(role));
        this.guild = guild;
        this.role = role;
        this.viewer = viewer;
        this.guiManager = guiManager;
        this.parentEditor = parentEditor;

        GuiConfig gui = GuiItems.config();
        this.backSlot = gui != null ? gui.slot("role-color.slots.back", 49) : 49;
    }

    private static int size() {
        GuiConfig gui = GuiItems.config();
        return gui != null ? gui.size("role-color.size", 54) : 54;
    }

    private static Component title(GuildRole role) {
        GuiConfig gui = GuiItems.config();
        if (gui == null) {
            return Component.text("Role Color");
        }
        return gui.title("role-color.title", Placeholder.unparsed("role", role.getName()));
    }

    @Override
    protected void build() {
        fillBorder(GuiItems.filler());

        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        ConfigManager configManager = plugin != null ? plugin.getConfigManager() : null;

        RoleColor[] colors = RoleColor.values();
        for (int i = 0; i < colors.length && i < DYE_SLOTS.length; i++) {
            RoleColor color = colors[i];
            int slot = DYE_SLOTS[i];
            slotColors.put(slot, color);

            Material dye = Material.matchMaterial(color.dyeMaterial());
            if (dye == null) dye = Material.WHITE_DYE;
            ItemStack item = new ItemStack(dye);
            ItemMeta meta = item.getItemMeta();
            String colorDisplayName = configManager != null ? configManager.getRoleColorName(color) : (color.miniMessage() + color.name());
            String nameTpl = color == role.getColor()
                    ? "<green><bold><color> <dark_gray>« selected"
                    : color.miniMessage() + "<bold><color>";
            GuiConfig gui = GuiItems.config();
            if (gui != null) {
                nameTpl = color == role.getColor()
                        ? gui.string("role-color.selected", "<green><bold><color> <dark_gray>« selected")
                        : gui.string("role-color.unselected", "<color_tag><bold><color>");
            }
            String resolved = nameTpl
                    .replace("<color_tag>", color.miniMessage())
                    .replace("<color>", colorDisplayName);
            meta.displayName(Text.parse(resolved).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
            setSlot(slot, item);
        }

        setSlot(backSlot, navBackItem());
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return true;
        int slot = event.getRawSlot();
        if (slot == backSlot) {
            navigateBack(player);
            return true;
        }

        RoleColor color = slotColors.get(slot);
        if (color == null) return true;

        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return true;

        plugin.getScheduler().runAsync(() ->
                plugin.getRoleService().setRoleColor(guild.getId(), player.getUniqueId(), role.getIndex(), color)
                        .thenCompose(result -> plugin.getGuildService().getGuild(guild.getId())
                                .thenAccept(opt -> plugin.getScheduler().runForEntity(player, () -> {
                                    switch (result) {
                                        case ActionResult.Success s -> {
                                            String colorDisplayName = plugin.getConfigManager().getRoleColorName(color);
                                            plugin.getMessages().send(player, "role-color-updated",
                                                    Placeholder.parsed("color", colorDisplayName));
                                            if (opt.isPresent()) {
                                                Guild fresh = opt.get();
                                                GuildRole updated = fresh.getRole(role.getIndex()).orElse(role);
                                                GuildMember freshViewer = fresh.getMember(viewer.getPlayerId()).orElse(viewer);
                                                new RoleEditorGui(fresh, updated, freshViewer, guiManager)
                                                        .returnTo(parentEditor)
                                                        .open(player);
                                            }
                                        }
                                        case ActionResult.Failure f ->
                                                plugin.getMessages().send(player, f.reason());
                                    }
                                })))
        );
        return true;
    }
}
