package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.gui.AbstractGui;
import me.usainsrht.guildroyale.core.gui.GuiItems;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Displays read-only guild stats.
 */
public final class GuildInfoGui extends AbstractGui {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final Guild guild;
    private final GuiManager guiManager;
    private final int infoSlot;
    private final int backSlot;

    public GuildInfoGui(Guild guild, GuiManager guiManager) {
        super(size(), title(guild));
        this.guild = guild;
        this.guiManager = guiManager;

        GuiConfig gui = GuiItems.config();
        this.infoSlot = gui != null ? gui.slot("info.slots.info", 13) : 13;
        this.backSlot = gui != null ? gui.slot("info.slots.back", 22) : 22;
    }

    private static int size() {
        GuiConfig gui = GuiItems.config();
        return gui != null ? gui.size("info.size", 27) : 27;
    }

    private static Component title(Guild guild) {
        GuiConfig gui = GuiItems.config();
        if (gui == null) {
            return Component.text("Guild Info");
        }
        return gui.title("info.title", Placeholder.unparsed("guild", guild.getName()));
    }

    @Override
    protected void build() {
        fillBorder(GuiItems.filler());

        Optional<GuildMember> leaderOpt = guild.getMembers().stream()
                .filter(m -> m.getRole().getIndex() == 0).findFirst();
        String leaderName = leaderOpt.map(m -> Bukkit.getOfflinePlayer(m.getPlayerId()).getName())
                .filter(s -> s != null).orElse("Unknown");

        ItemStack infoItem = GuiItems.get("info-book",
                Placeholder.unparsed("guild", guild.getName()),
                Placeholder.unparsed("shortname", guild.getShortname()),
                Placeholder.unparsed("level", String.valueOf(guild.getLevel())),
                Placeholder.unparsed("xp", String.valueOf(guild.getXp())),
                Placeholder.unparsed("members", String.valueOf(guild.getMemberCount())),
                Placeholder.unparsed("leader", leaderName),
                Placeholder.unparsed("founded", DATE_FMT.format(guild.getCreatedAt())));

        if (guild.getActiveBadgeId() != null) {
            appendBadgeLine(infoItem);
        }

        setSlot(infoSlot, infoItem);
        setSlot(backSlot, GuiItems.get("gui-back"));
    }

    private void appendBadgeLine(ItemStack infoItem) {
        GuiConfig gui = GuiItems.config();
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        String display = plugin != null
                ? plugin.getConfigManager().getBadgeDisplay(guild.getActiveBadgeId())
                        .orElse(guild.getActiveBadgeId())
                : guild.getActiveBadgeId();

        String template = gui != null
                ? gui.string("info.badge-line", "<gray>Badge: <badge>")
                : "<gray>Badge: <badge>";

        ItemMeta meta = infoItem.getItemMeta();
        if (meta == null) return;
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(MiniMessage.miniMessage()
                .deserialize(template, Placeholder.component("badge", MiniMessage.miniMessage().deserialize(display)))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        infoItem.setItemMeta(meta);
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return true;
        if (event.getRawSlot() == backSlot) {
            GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
            if (plugin == null) return true;
            var memberOpt = guild.getMember(player.getUniqueId());
            if (memberOpt.isPresent()) {
                new GuildMainGui(guild, memberOpt.get(), guiManager).open(player);
            } else {
                player.closeInventory();
            }
        }
        return true;
    }
}
