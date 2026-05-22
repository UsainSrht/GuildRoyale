package dev.guildroyale.core.gui.impl;

import dev.guildroyale.api.domain.Guild;
import dev.guildroyale.api.domain.GuildMember;
import dev.guildroyale.core.gui.AbstractGui;
import dev.guildroyale.core.gui.GuiManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

/**
 * Paginated member list. Each member head is clickable to open {@link MemberActionGui}.
 */
public final class GuildMembersGui extends AbstractGui {

    private static final int PAGE_SIZE = 36; // 4 rows of 9

    private final Guild guild;
    private final GuildMember viewer;
    private final GuiManager guiManager;
    private final int page;
    private final List<GuildMember> members;

    public GuildMembersGui(Guild guild, GuildMember viewer, GuiManager guiManager, int page) {
        super(54, "Members — " + guild.getName() + " (Page " + (page + 1) + ")");
        this.guild = guild;
        this.viewer = viewer;
        this.guiManager = guiManager;
        this.page = page;
        this.members = guild.getMembers().stream()
                .sorted((a, b) -> Integer.compare(a.getRole().getIndex(), b.getRole().getIndex()))
                .toList();
    }

    @Override
    protected void build() {
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && (start + i) < members.size(); i++) {
            GuildMember member = members.get(start + i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            var offlinePlayer = Bukkit.getOfflinePlayer(member.getPlayerId());
            meta.setOwningPlayer(offlinePlayer);
            String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : member.getPlayerId().toString().substring(0, 8);
            meta.displayName(Component.text(name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("§7Role: §f" + member.getRole().getName()).decoration(TextDecoration.ITALIC, false),
                    Component.text("§7Contribution: §f" + member.getContribution() + " XP").decoration(TextDecoration.ITALIC, false),
                    Component.text("§eClick to manage").decoration(TextDecoration.ITALIC, false)
            ));
            head.setItemMeta(meta);
            setSlot(i, head);
        }

        // Navigation row
        if (page > 0) {
            setSlot(45, makeNav(Material.ARROW, "« Previous Page", NamedTextColor.AQUA));
        }
        if ((page + 1) * PAGE_SIZE < members.size()) {
            setSlot(53, makeNav(Material.ARROW, "Next Page »", NamedTextColor.AQUA));
        }
        setSlot(49, makeNav(Material.BARRIER, "Close", NamedTextColor.RED));
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (!(event.getWhoClicked() instanceof Player player)) return true;

        if (slot == 45 && page > 0) {
            new GuildMembersGui(guild, viewer, guiManager, page - 1).open(player);
        } else if (slot == 53 && (page + 1) * PAGE_SIZE < members.size()) {
            new GuildMembersGui(guild, viewer, guiManager, page + 1).open(player);
        } else if (slot == 49) {
            player.closeInventory();
        } else if (slot < PAGE_SIZE) {
            int idx = page * PAGE_SIZE + slot;
            if (idx < members.size()) {
                new MemberActionGui(guild, members.get(idx), viewer, guiManager).open(player);
            }
        }
        return true;
    }

    private static ItemStack makeNav(Material mat, String label, NamedTextColor color) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, color).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
}
