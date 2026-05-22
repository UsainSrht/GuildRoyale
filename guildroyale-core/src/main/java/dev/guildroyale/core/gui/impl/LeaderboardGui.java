package dev.guildroyale.core.gui.impl;

import dev.guildroyale.api.domain.Guild;
import dev.guildroyale.api.service.LeaderboardService;
import dev.guildroyale.core.adapter.ItemStackAdapter;
import dev.guildroyale.core.gui.AbstractGui;
import dev.guildroyale.core.gui.GuiManager;
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
 * Shows the global guild leaderboard. Entries are loaded from {@link LeaderboardService}.
 */
public final class LeaderboardGui extends AbstractGui {

    private static final int PAGE_SIZE = 36;

    private final GuiManager guiManager;
    private final LeaderboardService leaderboardService;
    private final int page;
    private List<Guild> guilds = List.of();

    public LeaderboardGui(GuiManager guiManager, LeaderboardService leaderboardService, int page) {
        super(54, "Guild Leaderboard — Page " + (page + 1));
        this.guiManager = guiManager;
        this.leaderboardService = leaderboardService;
        this.page = page;
    }

    /** Sets the guild list before building. Call before {@link #open(Player)}. */
    public void setGuilds(List<Guild> guilds) {
        this.guilds = guilds;
    }

    @Override
    protected void build() {
        for (int i = 0; i < PAGE_SIZE && i < guilds.size(); i++) {
            Guild guild = guilds.get(i);
            int rank = page * PAGE_SIZE + i + 1;
            ItemStack icon = ItemStackAdapter.fromSerializable(guild.getIcon());
            if (icon.getType().isAir()) icon = new ItemStack(Material.GOLDEN_HELMET);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text("#" + rank + " " + guild.getName(), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("§7[" + guild.getShortname() + "]").decoration(TextDecoration.ITALIC, false),
                    Component.text("§7Level: §f" + guild.getLevel()).decoration(TextDecoration.ITALIC, false),
                    Component.text("§7XP: §f" + guild.getXp()).decoration(TextDecoration.ITALIC, false),
                    Component.text("§7Members: §f" + guild.getMemberCount()).decoration(TextDecoration.ITALIC, false)
            ));
            icon.setItemMeta(meta);
            setSlot(i, icon);
        }

        if (page > 0) setSlot(45, makeNav(Material.ARROW, "« Previous Page"));
        if ((page + 1) * PAGE_SIZE < guilds.size()) setSlot(53, makeNav(Material.ARROW, "Next Page »"));
        setSlot(49, makeNav(Material.BARRIER, "Close"));
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return true;
        int slot = event.getRawSlot();
        if (slot == 49) player.closeInventory();
        return true;
    }

    private static ItemStack makeNav(Material mat, String label) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
}
