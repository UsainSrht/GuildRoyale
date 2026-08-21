package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.mission.ActiveMission;
import me.usainsrht.guildroyale.api.domain.mission.MissionTaskDefinition;
import me.usainsrht.guildroyale.api.domain.mission.MissionTaskProgress;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.config.MissionConfig;
import me.usainsrht.guildroyale.core.gui.AbstractGui;
import me.usainsrht.guildroyale.core.gui.GuiItems;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import me.usainsrht.guildroyale.core.gui.MissionEligibility;
import me.usainsrht.guildroyale.core.message.Text;
import me.usainsrht.guildroyale.core.service.MissionServiceImpl;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * GUI for viewing and starting guild missions.
 * Displays either inactive/start view or active task progression view.
 */
public final class GuildMissionGui extends AbstractGui {

    private static final int[] TASK_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final Guild guild;
    private final GuildMember viewer;
    private final GuiManager guiManager;
    private final boolean active;
    private final ActiveMission activeMission;

    private final int infoSlot;
    private final int startSlot;
    private final int overviewSlot;
    private final int cancelSlot;
    private final int backSlot;

    public GuildMissionGui(Guild guild, GuildMember viewer, GuiManager guiManager) {
        super(size(), title(guild));
        this.guild = guild;
        this.viewer = viewer;
        this.guiManager = guiManager;

        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        MissionServiceImpl service = plugin != null ? (MissionServiceImpl) plugin.getMissionService() : null;
        this.activeMission = (service != null && guild != null)
                ? service.getActiveMission(guild.getId()).join().orElse(null)
                : null;
        this.active = activeMission != null && !activeMission.isExpired();

        GuiConfig gui = GuiItems.config();
        this.infoSlot = gui != null ? gui.slot("missions.slots.info", 20) : 20;
        this.startSlot = gui != null ? gui.slot("missions.slots.start", 24) : 24;
        this.overviewSlot = gui != null ? gui.slot("missions.slots.overview", 4) : 4;
        this.cancelSlot = gui != null ? gui.slot("missions.slots.cancel", 47) : 47;
        this.backSlot = gui != null ? gui.slot("missions.slots.back", 49) : 49;
    }

    private static int size() {
        GuiConfig gui = GuiItems.config();
        return gui != null ? gui.size("missions.size", 54) : 54;
    }

    private static Component title(Guild guild) {
        GuiConfig gui = GuiItems.config();
        if (gui == null) {
            return Component.text("Guild Missions");
        }
        return gui.title("missions.title", Placeholder.unparsed("guild", guild.getName()));
    }

    @Override
    protected void build() {
        fillBorder(GuiItems.filler());
        setSlot(backSlot, GuiItems.get("gui-back"));

        if (active && activeMission != null) {
            buildActiveView();
        } else {
            buildInactiveView();
        }
    }

    private void buildInactiveView() {
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        MissionConfig cfg = plugin != null ? plugin.getConfigManager().getMissionConfig() : null;

        // Info item
        String durationStr = cfg != null ? MissionServiceImpl.formatDuration(cfg.getDurationSeconds()) : "02:00:00";
        String xpReward = cfg != null ? String.valueOf(cfg.getRewardXp()) : "5000";
        String moneyReward = (cfg != null && plugin != null)
                ? ((me.usainsrht.guildroyale.core.service.GuildServiceImpl) plugin.getGuildService()).economy().format(cfg.getRewardMoney())
                : "2500";
        String xpPenalty = cfg != null ? String.valueOf(cfg.getPenaltyXp()) : "2500";
        String moneyPenalty = (cfg != null && plugin != null)
                ? ((me.usainsrht.guildroyale.core.service.GuildServiceImpl) plugin.getGuildService()).economy().format(cfg.getPenaltyMoney())
                : "1000";

        ItemStack infoItem = GuiItems.get("missions-info",
                Placeholder.unparsed("duration", durationStr),
                Placeholder.unparsed("reward_xp", xpReward),
                Placeholder.unparsed("reward_money", moneyReward),
                Placeholder.unparsed("penalty_xp", xpPenalty),
                Placeholder.unparsed("penalty_money", moneyPenalty));
        setSlot(infoSlot, infoItem);

        // Start mission button (dynamically evaluated based on player and guild eligibility)
        Player player = Bukkit.getPlayer(viewer.getPlayerId());
        MissionEligibility.Status status = MissionEligibility.evaluate(player, guild, viewer);

        ItemStack startItem = GuiItems.get(status.itemKey());
        if (startItem != null && player != null) {
            ItemMeta meta = startItem.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.addAll(MissionEligibility.requirementLore(player, guild, viewer));
                meta.lore(lore);
                startItem.setItemMeta(meta);
            }
        }
        setSlot(startSlot, startItem);
    }

    private void buildActiveView() {
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null || activeMission == null) return;

        MissionConfig cfg = plugin.getConfigManager().getMissionConfig();

        // Overview item at top
        int percent = activeMission.getOverallProgressPercent();
        String progressBar = formatProgressBar(percent, 100, 10);
        String remainingTime = MissionServiceImpl.formatDuration(activeMission.getRemainingSeconds());
        int completedTasks = activeMission.getCompletedTaskCount();
        int totalTasks = activeMission.getTotalTaskCount();

        ItemStack overview = GuiItems.get("mission-overview",
                Placeholder.unparsed("percent", String.valueOf(percent)),
                Placeholder.parsed("progress_bar", progressBar),
                Placeholder.unparsed("time_remaining", remainingTime),
                Placeholder.unparsed("completed_tasks", String.valueOf(completedTasks)),
                Placeholder.unparsed("total_tasks", String.valueOf(totalTasks)));
        setSlot(overviewSlot, overview);

        // Render Task items
        List<MissionTaskDefinition> defs = cfg.getTasks();
        int slotIndex = 0;
        for (MissionTaskDefinition def : defs) {
            if (slotIndex >= TASK_SLOTS.length) break;

            int slot = TASK_SLOTS[slotIndex++];
            MissionTaskProgress progress = activeMission.getTask(def.getId())
                    .orElse(new MissionTaskProgress(def.getId(), 0L, def.getTarget()));

            ItemStack taskItem = buildTaskItem(def, progress);
            setSlot(slot, taskItem);
        }

        // Cancel / Forfeit button for leader/officers
        me.usainsrht.guildroyale.core.service.PermissionEvaluatorImpl evaluator = new me.usainsrht.guildroyale.core.service.PermissionEvaluatorImpl();
        if (evaluator.canAct(viewer, GuildPermissionKey.MISSION_START)) {
            setSlot(cancelSlot, GuiItems.get("mission-cancel"));
        }
    }

    private ItemStack buildTaskItem(MissionTaskDefinition def, MissionTaskProgress progress) {
        Material mat = Material.matchMaterial(def.getIconMaterial());
        if (mat == null || mat.isAir()) mat = Material.PAPER;

        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        meta.displayName(Text.parse(def.getDisplayName()));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        int taskPercent = progress.getProgressPercent();
        String taskBar = formatProgressBar(progress.getCurrent(), progress.getTarget(), 10);
        String statusText = progress.isCompleted()
                ? "<green><bold>✔ COMPLETED</bold></green>"
                : "<yellow>⏳ IN PROGRESS</yellow>";

        lore.add(Text.parse(" <gray>Status: " + statusText));
        lore.add(Text.parse(" <gray>Progress: <white>" + formatNumber(progress.getCurrent()) + "</white> <dark_gray>/</dark_gray> <white>" + formatNumber(progress.getTarget()) + "</white> <dark_gray>(" + taskPercent + "%)</dark_gray>"));
        lore.add(Text.parse(" <gray>Bar: " + taskBar));

        // Member contributions
        List<Map.Entry<UUID, Long>> top = progress.getTopContributors(5);
        if (!top.isEmpty()) {
            lore.add(Component.empty());
            lore.add(Text.parse(" <yellow><bold>Top Contributors:</bold></yellow>"));
            int rank = 1;
            for (Map.Entry<UUID, Long> entry : top) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                String pName = op.getName() != null ? op.getName() : "Unknown";
                lore.add(Text.parse("  <gray>" + rank + ". <white>" + pName + "</white>: <gold>" + formatNumber(entry.getValue()) + "</gold>"));
                rank++;
            }
        }

        lore.add(Component.empty());
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();

        if (slot == backSlot) {
            new GuildMainGui(guild, viewer, guiManager).returnTo(this).open(player);
            return true;
        }

        if (!active && slot == startSlot) {
            GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
            if (plugin == null) return true;

            MissionEligibility.Status status = MissionEligibility.evaluate(player, guild, viewer);
            if (!status.canStart()) {
                player.closeInventory();
                String msgKey = switch (status) {
                    case NO_PERMISSION -> "no-permission";
                    case ON_COOLDOWN -> "mission-on-cooldown";
                    case INSUFFICIENT_FUNDS -> "mission-insufficient-funds";
                    case MISSING_ITEMS -> "mission-missing-items";
                    case ALREADY_ACTIVE -> "mission-already-active";
                    default -> "unknown-error";
                };
                plugin.getMessages().send(player, msgKey);
                return true;
            }

            plugin.getMissionService().startMission(guild.getId(), player.getUniqueId()).thenAccept(res -> {
                plugin.getScheduler().runForEntity(player, () -> {
                    if (res.isSuccess()) {
                        new GuildMissionGui(guild, viewer, guiManager).open(player);
                    } else {
                        plugin.getMessages().send(player, res.failureReasonOrEmpty());
                        new GuildMissionGui(guild, viewer, guiManager).open(player);
                    }
                });
            });
            return true;
        }

        me.usainsrht.guildroyale.core.service.PermissionEvaluatorImpl evaluator = new me.usainsrht.guildroyale.core.service.PermissionEvaluatorImpl();
        if (active && slot == cancelSlot && evaluator.canAct(viewer, GuildPermissionKey.MISSION_START)) {
            GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
            if (plugin == null) return true;

            plugin.getMissionService().cancelMission(guild.getId(), player.getUniqueId()).thenAccept(res -> {
                plugin.getScheduler().runForEntity(player, () -> {
                    if (res.isSuccess()) {
                        new GuildMissionGui(guild, viewer, guiManager).open(player);
                    } else {
                        plugin.getMessages().send(player, res.failureReasonOrEmpty());
                    }
                });
            });
        }
        return true;
    }

    public static String formatProgressBar(long current, long required, int totalBars) {
        if (required <= 0) return "<green>" + "■".repeat(totalBars) + "</green>";
        double fraction = Math.min(1.0, Math.max(0.0, (double) current / (double) required));
        int completed = (int) Math.round(fraction * totalBars);
        int remaining = totalBars - completed;
        return "<green>" + "■".repeat(completed) + "</green><dark_gray>" + "■".repeat(remaining) + "</dark_gray>";
    }

    private static String formatNumber(long number) {
        return String.format("%,d", number);
    }
}
