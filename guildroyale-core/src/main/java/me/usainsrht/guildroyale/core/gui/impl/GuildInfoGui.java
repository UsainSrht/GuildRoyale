package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.core.gui.AbstractGui;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Displays read-only guild stats: name, shortname, level, XP, member count,
 * leader name, and creation date.
 */
public final class GuildInfoGui extends AbstractGui {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final Guild guild;
    private final GuiManager guiManager;

    public GuildInfoGui(Guild guild, GuiManager guiManager) {
        super(27, "Guild Info — " + guild.getName());
        this.guild = guild;
        this.guiManager = guiManager;
    }

    @Override
    protected void build() {
        // Guild icon / head
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta meta = infoItem.getItemMeta();
        meta.displayName(Component.text(guild.getName(), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        Optional<GuildMember> leaderOpt = guild.getMembers().stream()
                .filter(m -> m.getRole().getIndex() == 0).findFirst();
        String leaderName = leaderOpt.map(m -> Bukkit.getOfflinePlayer(m.getPlayerId()).getName())
                .filter(s -> s != null).orElse("Unknown");

        meta.lore(List.of(
                Component.text("§7Shortname: §f[" + guild.getShortname() + "]").decoration(TextDecoration.ITALIC, false),
                Component.text("§7Level: §f" + guild.getLevel()).decoration(TextDecoration.ITALIC, false),
                Component.text("§7XP: §f" + guild.getXp()).decoration(TextDecoration.ITALIC, false),
                Component.text("§7Members: §f" + guild.getMemberCount()).decoration(TextDecoration.ITALIC, false),
                Component.text("§7Leader: §f" + leaderName).decoration(TextDecoration.ITALIC, false),
                Component.text("§7Founded: §f" + DATE_FMT.format(guild.getCreatedAt())).decoration(TextDecoration.ITALIC, false)
        ));
        infoItem.setItemMeta(meta);
        setSlot(13, infoItem);

        // Back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("Back", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(backMeta);
        setSlot(22, back);
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        return true; // all clicks cancelled, nothing to do
    }
}
