package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.core.adapter.ItemStackAdapter;
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
 * Hub GUI — the main guild menu, opened by {@code /guild menu}.
 *
 * <pre>
 * Slots: [Info][Members][Roles][Leaderboard][  ][Settings][Disband]
 * </pre>
 */
public final class GuildMainGui extends AbstractGui {

    private final Guild guild;
    private final GuildMember viewer;
    private final GuiManager guiManager;

    public GuildMainGui(Guild guild, GuildMember viewer, GuiManager guiManager) {
        super(27, "Guild Menu — " + guild.getName());
        this.guild = guild;
        this.viewer = viewer;
        this.guiManager = guiManager;
    }

    @Override
    protected void build() {
        setSlot(10, makeButton(Material.PAPER, "Info", NamedTextColor.AQUA,
                List.of("§7View guild information")));
        setSlot(11, makeButton(Material.PLAYER_HEAD, "Members", NamedTextColor.GREEN,
                List.of("§7View and manage members")));
        setSlot(12, makeButton(Material.WRITABLE_BOOK, "Roles", NamedTextColor.YELLOW,
                List.of("§7Manage guild roles")));
        setSlot(13, makeButton(Material.GOLDEN_HELMET, "Leaderboard", NamedTextColor.GOLD,
                List.of("§7View the global guild leaderboard")));
        // Guild icon in centre
        ItemStack icon = ItemStackAdapter.fromSerializable(guild.getIcon());
        if (!icon.getType().isAir()) setSlot(4, icon);
        setSlot(15, makeButton(Material.COMPARATOR, "Settings", NamedTextColor.GRAY,
                List.of("§7Change guild name, shortname, icon")));
        setSlot(16, makeButton(Material.BARRIER, "Disband", NamedTextColor.RED,
                List.of("§cDisband and delete this guild")));
        // Fill border with glass panes
        fillBorder();
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (!(event.getWhoClicked() instanceof Player player)) return true;
        switch (slot) {
            case 10 -> new GuildInfoGui(guild, guiManager).open(player);
            case 11 -> new GuildMembersGui(guild, viewer, guiManager, 0).open(player);
            case 12 -> new RoleManagementGui(guild, viewer, guiManager).open(player);
            case 13 -> new LeaderboardGui(guiManager, null, 0).open(player);
            default -> { /* no-op */ }
        }
        return true;
    }

    private void fillBorder() {
        ItemStack pane = makeButton(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.WHITE, List.of());
        for (int i = 0; i < 9; i++) setSlot(i, pane);
        for (int i = 18; i < 27; i++) setSlot(i, pane);
        setSlot(9, pane); setSlot(17, pane);
    }

    private static ItemStack makeButton(Material mat, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(l -> Component.text(l).decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }
}
