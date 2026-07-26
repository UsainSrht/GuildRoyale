package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.service.LeaderboardService;
import me.usainsrht.guildroyale.core.adapter.ItemStackAdapter;
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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Shows the global guild leaderboard. Entries are loaded from {@link LeaderboardService}.
 */
public final class LeaderboardGui extends AbstractGui {

    private final GuiManager guiManager;
    private final LeaderboardService leaderboardService;
    private final int page;
    private final int pageSize;
    private final int previousSlot;
    private final int nextSlot;
    private final int closeSlot;
    private List<Guild> guilds = List.of();

    public LeaderboardGui(GuiManager guiManager, LeaderboardService leaderboardService, int page) {
        super(size(), Component.empty());
        this.guiManager = guiManager;
        this.leaderboardService = leaderboardService;
        this.page = Math.max(0, page);

        GuiConfig gui = GuiItems.config();
        this.pageSize = gui != null ? gui.pageSize("leaderboard.page-size", 36) : 36;
        this.previousSlot = gui != null ? gui.slot("leaderboard.slots.previous", 45) : 45;
        this.nextSlot = gui != null ? gui.slot("leaderboard.slots.next", 53) : 53;
        this.closeSlot = gui != null ? gui.slot("leaderboard.slots.close", 49) : 49;
    }

    private static int size() {
        GuiConfig gui = GuiItems.config();
        return gui != null ? gui.size("leaderboard.size", 54) : 54;
    }

    /** Sets the guild list before building. Call before {@link #open(Player)}. */
    public void setGuilds(List<Guild> guilds) {
        this.guilds = guilds != null ? guilds : List.of();
    }

    @Override
    public void open(Player player) {
        int maxPage = Math.max(1, (int) Math.ceil(Math.max(guilds.size(), 1) / (double) pageSize));
        GuiConfig gui = GuiItems.config();
        Component resolved = gui != null
                ? gui.title("leaderboard.title",
                        Placeholder.unparsed("page", String.valueOf(page + 1)),
                        Placeholder.unparsed("max_page", String.valueOf(maxPage)))
                : Component.text("Guild Leaderboard");
        this.inventory = Bukkit.createInventory(this, size, resolved);
        build();
        player.openInventory(inventory);
    }

    @Override
    protected void build() {
        for (int i = 0; i < pageSize && i < guilds.size(); i++) {
            Guild guild = guilds.get(i);
            int rank = page * pageSize + i + 1;
            ItemStack icon = ItemStackAdapter.fromSerializable(guild.getIcon());
            if (icon.getType().isAir()) {
                icon = GuiItems.get("leaderboard-entry",
                        Placeholder.unparsed("rank", String.valueOf(rank)),
                        Placeholder.unparsed("guild", guild.getName()),
                        Placeholder.unparsed("shortname", guild.getShortname()),
                        Placeholder.unparsed("level", String.valueOf(guild.getLevel())),
                        Placeholder.unparsed("xp", String.valueOf(guild.getXp())),
                        Placeholder.unparsed("members", String.valueOf(guild.getMemberCount())));
            } else {
                ItemStack template = GuiItems.get("leaderboard-entry",
                        Placeholder.unparsed("rank", String.valueOf(rank)),
                        Placeholder.unparsed("guild", guild.getName()),
                        Placeholder.unparsed("shortname", guild.getShortname()),
                        Placeholder.unparsed("level", String.valueOf(guild.getLevel())),
                        Placeholder.unparsed("xp", String.valueOf(guild.getXp())),
                        Placeholder.unparsed("members", String.valueOf(guild.getMemberCount())));
                ItemMeta meta = template.getItemMeta();
                if (meta != null) {
                    icon.setItemMeta(meta);
                }
            }
            setSlot(i, icon);
        }

        if (page > 0) setSlot(previousSlot, GuiItems.get("gui-previous"));
        if ((page + 1) * pageSize < guilds.size()) setSlot(nextSlot, GuiItems.get("gui-next"));
        setSlot(closeSlot, GuiItems.get("gui-close"));
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return true;
        int slot = event.getRawSlot();
        if (slot == closeSlot) player.closeInventory();
        return true;
    }
}
