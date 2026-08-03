package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.service.LeaderboardService;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.adapter.ItemStackAdapter;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.gui.GuiItems;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import me.usainsrht.guildroyale.core.gui.StandardListGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the global guild leaderboard using {@link StandardListGui}.
 * Entries are loaded from {@link LeaderboardService}.
 */
public final class LeaderboardGui extends StandardListGui<Guild> {

    private final GuiManager guiManager;
    private final LeaderboardService leaderboardService;

    public LeaderboardGui(GuiManager guiManager, LeaderboardService leaderboardService, int page) {
        this(guiManager, leaderboardService, page, new ArrayList<>());
    }

    public LeaderboardGui(GuiManager guiManager, LeaderboardService leaderboardService, int page, List<Guild> guilds) {
        super(size(), title(guilds != null ? guilds.size() : 0, page), guilds != null ? guilds : List.of(), page, "leaderboard");
        this.guiManager = guiManager;
        this.leaderboardService = leaderboardService;
    }

    private static int size() {
        GuiConfig gui = GuiItems.config();
        return gui != null ? gui.size("leaderboard.size", 54) : 54;
    }

    private static Component title(int totalCount, int page) {
        GuiConfig gui = GuiItems.config();
        int innerCount = StandardListGui.calculateInnerSlots(size() / 9).size();
        int maxPage = Math.max(1, (int) Math.ceil(Math.max(totalCount, 1) / (double) innerCount));
        int safePage = Math.max(0, Math.min(page, maxPage - 1)) + 1;
        if (gui == null) {
            return formatStandardTitle("Guild Leaderboard");
        }
        return gui.title("leaderboard.title",
                Placeholder.unparsed("page", String.valueOf(safePage)),
                Placeholder.unparsed("max_page", String.valueOf(maxPage)));
    }

    /** Sets the guild list before building. Call before {@link #open(Player)}. */
    public void setGuilds(List<Guild> guilds) {
        this.items.clear();
        if (guilds != null) {
            this.items.addAll(guilds);
        }
    }

    @Override
    protected ItemStack renderItem(Guild guild, int index) {
        int rank = page * pageSize + index + 1;
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
        return icon;
    }

    @Override
    protected void onItemClick(InventoryClickEvent event, Guild item, int index) {}

    @Override
    protected void onPreviousPage(Player player) {
        if (page <= 0) return;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin != null) {
            plugin.getScheduler().runAsync(() ->
                    leaderboardService.getGlobalLeaderboard(page - 1, pageSize).thenAccept(guilds ->
                            plugin.getScheduler().runForEntity(player, () -> {
                                LeaderboardGui gui = new LeaderboardGui(guiManager, leaderboardService, page - 1);
                                gui.setGuilds(guilds);
                                gui.open(player);
                            })
                    )
            );
        }
    }

    @Override
    protected void onNextPage(Player player) {
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin != null) {
            plugin.getScheduler().runAsync(() ->
                    leaderboardService.getGlobalLeaderboard(page + 1, pageSize).thenAccept(guilds ->
                            plugin.getScheduler().runForEntity(player, () -> {
                                LeaderboardGui gui = new LeaderboardGui(guiManager, leaderboardService, page + 1);
                                gui.setGuilds(guilds);
                                gui.open(player);
                            })
                    )
            );
        }
    }

    @Override
    protected void onBack(Player player) {
        player.closeInventory();
    }
}
