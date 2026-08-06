package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.gui.AbstractGui;
import me.usainsrht.guildroyale.core.gui.GuiItems;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Actions available for a specific guild member: view profile, kick, promote, demote.
 * Actions are rendered only if the viewer has the required permissions.
 */
public final class MemberActionGui extends AbstractGui {

    private final Guild guild;
    private final GuildMember target;
    private final GuildMember viewer;
    private final GuiManager guiManager;
    private final int headSlot;
    private final int kickSlot;
    private final int promoteSlot;
    private final int demoteSlot;
    private final int backSlot;

    public MemberActionGui(Guild guild, GuildMember target, GuildMember viewer, GuiManager guiManager) {
        super(size(), title(target));
        this.guild = guild;
        this.target = target;
        this.viewer = viewer;
        this.guiManager = guiManager;

        GuiConfig gui = GuiItems.config();
        this.headSlot = gui != null ? gui.slot("member-action.slots.head", 13) : 13;
        this.kickSlot = gui != null ? gui.slot("member-action.slots.kick", 29) : 29;
        this.promoteSlot = gui != null ? gui.slot("member-action.slots.promote", 31) : 31;
        this.demoteSlot = gui != null ? gui.slot("member-action.slots.demote", 33) : 33;
        this.backSlot = gui != null ? gui.slot("member-action.slots.back", 49) : 49;
    }

    private static int size() {
        GuiConfig gui = GuiItems.config();
        return gui != null ? gui.size("member-action.size", 54) : 54;
    }

    private static Component title(GuildMember target) {
        String name = Bukkit.getOfflinePlayer(target.getPlayerId()).getName();
        if (name == null) name = "Unknown";
        GuiConfig gui = GuiItems.config();
        if (gui == null) {
            return Component.text("Member: " + name);
        }
        return gui.title("member-action.title", Placeholder.unparsed("player", name));
    }

    @Override
    protected void build() {
        fillBorder(GuiItems.filler());

        var offline = Bukkit.getOfflinePlayer(target.getPlayerId());
        String tName = offline.getName() != null ? offline.getName() : "Unknown";

        ItemStack head = GuiItems.get("member-action-head",
                Placeholder.unparsed("player", tName),
                Placeholder.unparsed("role", target.getRole().getName()),
                Placeholder.unparsed("contribution", String.valueOf(target.getContribution())));
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(offline);
            head.setItemMeta(meta);
        }
        setSlot(headSlot, head);

        boolean canActOn = viewer.getRole().getIndex() < target.getRole().getIndex();
        if (canActOn) {
            setSlot(kickSlot, GuiItems.get("member-action-kick"));
            setSlot(promoteSlot, GuiItems.get("member-action-promote"));
            setSlot(demoteSlot, GuiItems.get("member-action-demote"));
        }

        setSlot(backSlot, navBackItem());
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return true;
        if (event.getRawSlot() == backSlot) {
            navigateBack(player);
        }
        return true;
    }
}
