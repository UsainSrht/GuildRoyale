package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildLevel;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.gui.GuiItems;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import me.usainsrht.guildroyale.core.gui.StandardListGui;
import me.usainsrht.guildroyale.core.message.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Paginated list GUI displaying all contributors to guild XP,
 * including former members who have left the guild.
 */
public final class GuildLevelGui extends StandardListGui<GuildLevelGui.ContributorEntry> {

    public record ContributorEntry(UUID playerId, long contribution) {}

    private final Guild guild;
    private final GuildMember viewer;
    private final GuiManager guiManager;
    private final long totalContributionsSum;

    public GuildLevelGui(Guild guild, GuildMember viewer, GuiManager guiManager, int page) {
        super(size(), title(guild, page), getSortedContributors(guild), page, "level");
        this.guild = guild;
        this.viewer = viewer;
        this.guiManager = guiManager;
        this.totalContributionsSum = guild.getContributions().values().stream()
                .mapToLong(Long::longValue)
                .sum();
    }

    private static List<ContributorEntry> getSortedContributors(Guild guild) {
        return guild.getContributions().entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null && e.getValue() > 0)
                .map(e -> new ContributorEntry(e.getKey(), e.getValue()))
                .sorted((a, b) -> Long.compare(b.contribution(), a.contribution()))
                .toList();
    }

    private static int size() {
        GuiConfig gui = GuiItems.config();
        return gui != null ? gui.size("level.size", 54) : 54;
    }

    private static Component title(Guild guild, int page) {
        GuiConfig gui = GuiItems.config();
        int innerCount = StandardListGui.calculateInnerSlots(size() / 9).size();
        int contributorCount = (int) guild.getContributions().values().stream().filter(v -> v > 0).count();
        int maxPage = Math.max(1, (int) Math.ceil(contributorCount / (double) innerCount));
        int safePage = Math.max(0, Math.min(page, maxPage - 1)) + 1;
        if (gui == null) {
            return formatStandardTitle("Guild Level & Contributors");
        }
        return gui.listTitle("level", safePage, maxPage,
                Placeholder.unparsed("guild", guild.getName()),
                Placeholder.unparsed("level", String.valueOf(guild.getLevel())),
                Placeholder.unparsed("page", String.valueOf(safePage)),
                Placeholder.unparsed("max_page", String.valueOf(maxPage)));
    }

    @Override
    protected ItemStack renderItem(ContributorEntry entry, int index) {
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.playerId());
        String rawName = offlinePlayer.getName() != null
                ? offlinePlayer.getName()
                : entry.playerId().toString().substring(0, 8);

        Optional<GuildMember> memberOpt = guild.getMember(entry.playerId());
        GuiConfig gui = GuiItems.config();

        String status;
        Component roleComp;
        var playerResolver = (plugin != null && memberOpt.isPresent())
                ? plugin.getTagService().memberResolver(memberOpt.get(), guild, null)
                : (plugin != null
                    ? plugin.getTagService().playerResolver(entry.playerId(), rawName, null)
                    : Placeholder.unparsed("player", rawName));

        if (memberOpt.isPresent()) {
            GuildMember member = memberOpt.get();
            status = gui != null ? gui.string("level.status-active", "<green>Active Member</green>") : "<green>Active Member</green>";
            roleComp = plugin != null
                    ? plugin.getTagService().renderRoleTag(member.getRole(), guild, null)
                    : Component.text(member.getRole().getName());
        } else {
            status = gui != null ? gui.string("level.status-former", "<red>Former Member</red>") : "<red>Former Member</red>";
            roleComp = Component.text("None");
        }

        long percent = totalContributionsSum > 0
                ? Math.round((double) entry.contribution() / totalContributionsSum * 100.0)
                : 0L;

        ItemStack head = GuiItems.get("level-entry",
                playerResolver,
                Placeholder.component("role", roleComp),
                Placeholder.parsed("status", status),
                Placeholder.unparsed("contribution", String.valueOf(entry.contribution())),
                Placeholder.unparsed("percent", String.valueOf(percent)));

        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(offlinePlayer);
            head.setItemMeta(meta);
        }
        return head;
    }

    @Override
    protected void onItemClick(InventoryClickEvent event, ContributorEntry entry, int index) {
        // Display-only contributor entry
    }

    @Override
    protected ItemStack getTopActionItem() {
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        int currentLevel = guild.getLevel();
        long currentXp = guild.getXp();
        int cap = plugin != null ? plugin.getConfigManager().getLevelCap() : 10;
        boolean isMax = new GuildLevel(currentLevel).isMaxLevel(cap);
        int contributorCount = (int) guild.getContributions().values().stream().filter(v -> v > 0).count();

        if (isMax) {
            return GuiItems.get("level-summary-max",
                    Placeholder.unparsed("level", String.valueOf(currentLevel)),
                    Placeholder.unparsed("xp", String.valueOf(currentXp)),
                    Placeholder.unparsed("total_xp", String.valueOf(currentXp)),
                    Placeholder.unparsed("contributors", String.valueOf(contributorCount)));
        }

        long requiredXp = plugin != null
                ? plugin.getGuildService().xpRequiredForLevel(currentLevel + 1)
                : 1000L;
        long percent = requiredXp > 0
                ? Math.min(100L, Math.max(0L, Math.round((double) currentXp / requiredXp * 100.0)))
                : 0L;
        String progressBar = formatProgressBar(currentXp, requiredXp, 10);

        return GuiItems.get("level-summary",
                Placeholder.unparsed("level", String.valueOf(currentLevel)),
                Placeholder.unparsed("xp", String.valueOf(currentXp)),
                Placeholder.unparsed("xp_needed", String.valueOf(requiredXp)),
                Placeholder.parsed("progress_bar", progressBar),
                Placeholder.unparsed("percent", String.valueOf(percent)),
                Placeholder.unparsed("contributors", String.valueOf(contributorCount)));
    }

    @Override
    protected void onPreviousPage(Player player) {
        new GuildLevelGui(guild, viewer, guiManager, page - 1).returnTo(this).open(player);
    }

    @Override
    protected void onNextPage(Player player) {
        new GuildLevelGui(guild, viewer, guiManager, page + 1).returnTo(this).open(player);
    }

    public static String formatProgressBar(long current, long required, int totalBars) {
        if (required <= 0) return "<green>" + "■".repeat(totalBars) + "</green>";
        double fraction = Math.min(1.0, Math.max(0.0, (double) current / required));
        int completed = (int) Math.round(fraction * totalBars);
        int remaining = totalBars - completed;
        return "<green>" + "■".repeat(completed) + "</green><dark_gray>" + "■".repeat(remaining) + "</dark_gray>";
    }
}
