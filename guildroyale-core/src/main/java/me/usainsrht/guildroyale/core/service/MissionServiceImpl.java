package me.usainsrht.guildroyale.core.service;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.mission.*;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.api.service.MissionService;
import me.usainsrht.guildroyale.api.storage.GuildRepository;
import me.usainsrht.guildroyale.api.storage.MissionRepository;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.ConfigManager;
import me.usainsrht.guildroyale.core.config.ItemRequirement;
import me.usainsrht.guildroyale.core.config.MissionConfig;
import me.usainsrht.guildroyale.core.event.*;
import me.usainsrht.guildroyale.core.message.Text;
import me.usainsrht.guildroyale.core.scheduler.FoliaScheduler;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link MissionService}.
 */
public final class MissionServiceImpl implements MissionService {

    private final GuildRoyalePlugin plugin;
    private final GuildRepository guildRepo;
    private final MissionRepository missionRepo;
    private final ConfigManager configManager;
    private final EconomyProvider economy;
    private final FoliaScheduler scheduler;
    private final EventDispatcher eventDispatcher;

    private final ConcurrentHashMap<UUID, ActiveMission> activeMissions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, GuildMissionData> missionDataCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, BossBar> guildBossBars = new ConcurrentHashMap<>();

    public MissionServiceImpl(GuildRoyalePlugin plugin, GuildRepository guildRepo,
                              MissionRepository missionRepo, ConfigManager configManager,
                              EconomyProvider economy, FoliaScheduler scheduler,
                              EventDispatcher eventDispatcher) {
        this.plugin = plugin;
        this.guildRepo = guildRepo;
        this.missionRepo = missionRepo;
        this.configManager = configManager;
        this.economy = economy;
        this.scheduler = scheduler;
        this.eventDispatcher = eventDispatcher;
    }

    public void init() {
        missionRepo.findAll().thenAccept(all -> {
            for (GuildMissionData data : all) {
                missionDataCache.put(data.getGuildId(), data);
                if (data.hasActiveMission()) {
                    ActiveMission mission = data.getActiveMission().get();
                    if (mission.isExpired()) {
                        handleMissionFailure(data.getGuildId(), mission);
                    } else {
                        activeMissions.put(data.getGuildId(), mission);
                        initBossBar(data.getGuildId(), mission);
                    }
                }
            }
            startTicker();
        });
    }

    public void shutdown() {
        for (Map.Entry<UUID, BossBar> entry : guildBossBars.entrySet()) {
            BossBar bar = entry.getValue();
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(bar);
            }
        }
        guildBossBars.clear();
    }

    private void startTicker() {
        scheduler.scheduleAsyncRepeating(this::tickMissions, 1L, 1L, TimeUnit.SECONDS);
    }

    private void tickMissions() {
        Instant now = Instant.now();
        for (Map.Entry<UUID, ActiveMission> entry : activeMissions.entrySet()) {
            UUID guildId = entry.getKey();
            ActiveMission mission = entry.getValue();

            if (mission.isExpired()) {
                handleMissionFailure(guildId, mission);
            } else {
                updateBossBar(guildId, mission);
            }
        }
    }

    @Override
    public List<MissionTaskDefinition> getTaskDefinitions() {
        return configManager.getMissionConfig().getTasks();
    }

    @Override
    public boolean isMissionActive(UUID guildId) {
        if (guildId == null) return false;
        ActiveMission m = activeMissions.get(guildId);
        return m != null && !m.isExpired();
    }

    @Override
    public CompletableFuture<Optional<ActiveMission>> getActiveMission(UUID guildId) {
        if (guildId == null) return CompletableFuture.completedFuture(Optional.empty());
        ActiveMission active = activeMissions.get(guildId);
        if (active != null && !active.isExpired()) {
            return CompletableFuture.completedFuture(Optional.of(active));
        }
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<Optional<Instant>> getLastStartedAt(UUID guildId) {
        if (guildId == null) return CompletableFuture.completedFuture(Optional.empty());
        GuildMissionData data = missionDataCache.get(guildId);
        if (data != null) {
            return CompletableFuture.completedFuture(data.getLastStartedAt());
        }
        return missionRepo.findByGuildId(guildId).thenApply(opt -> opt.flatMap(GuildMissionData::getLastStartedAt));
    }

    @Override
    public long getCooldownRemainingSeconds(UUID guildId) {
        if (guildId == null) return 0L;
        GuildMissionData data = missionDataCache.get(guildId);
        if (data == null || data.getLastStartedAt().isEmpty()) {
            return 0L;
        }

        Instant lastStartedAt = data.getLastStartedAt().get();
        MissionConfig cfg = configManager.getMissionConfig();
        return calculateCooldownRemainingSeconds(cfg, lastStartedAt, Instant.now());
    }

    public static long calculateCooldownRemainingSeconds(MissionConfig cfg, Instant lastStartedAt, Instant now) {
        if (cfg == null || lastStartedAt == null || now == null) return 0L;

        return switch (cfg.getCooldownType()) {
            case DELAY -> {
                Instant nextAvailable = lastStartedAt.plusSeconds(cfg.getDelaySeconds());
                yield Math.max(0L, Duration.between(now, nextAvailable).getSeconds());
            }
            case DAILY -> {
                ZoneId zone = cfg.getTimezone();
                ZonedDateTime zdtNow = now.atZone(zone);
                LocalTime resetTime = cfg.getDailyResetTime();

                ZonedDateTime todayReset = zdtNow.toLocalDate().atTime(resetTime).atZone(zone);
                ZonedDateTime lastReset = zdtNow.isBefore(todayReset)
                        ? todayReset.minusDays(1)
                        : todayReset;
                ZonedDateTime nextReset = lastReset.plusDays(1);

                if (lastStartedAt.isAfter(lastReset.toInstant()) || lastStartedAt.equals(lastReset.toInstant())) {
                    yield Math.max(0L, Duration.between(now, nextReset.toInstant()).getSeconds());
                }
                yield 0L;
            }
            case WEEKLY -> {
                ZoneId zone = cfg.getTimezone();
                ZonedDateTime zdtNow = now.atZone(zone);
                DayOfWeek resetDay = cfg.getWeeklyResetDay();
                LocalTime resetTime = cfg.getWeeklyResetTime();

                ZonedDateTime thisWeekReset = zdtNow.with(TemporalAdjusters.previousOrSame(resetDay))
                        .toLocalDate().atTime(resetTime).atZone(zone);
                ZonedDateTime lastReset = zdtNow.isBefore(thisWeekReset)
                        ? thisWeekReset.minusWeeks(1)
                        : thisWeekReset;
                ZonedDateTime nextReset = lastReset.plusWeeks(1);

                if (lastStartedAt.isAfter(lastReset.toInstant()) || lastStartedAt.equals(lastReset.toInstant())) {
                    yield Math.max(0L, Duration.between(now, nextReset.toInstant()).getSeconds());
                }
                yield 0L;
            }
        };
    }

    @Override
    public CompletableFuture<ActionResult> startMission(UUID guildId, UUID requesterId) {
        MissionConfig cfg = configManager.getMissionConfig();
        if (!cfg.isEnabled()) {
            return CompletableFuture.completedFuture(ActionResult.failure("feature-disabled"));
        }

        return guildRepo.findById(guildId).thenCompose(optGuild -> {
            if (optGuild.isEmpty()) {
                return CompletableFuture.completedFuture(ActionResult.failure("invalid-guild"));
            }
            Guild guild = optGuild.get();

            if (isMissionActive(guildId)) {
                return CompletableFuture.completedFuture(ActionResult.failure("mission-already-active"));
            }

            long cd = getCooldownRemainingSeconds(guildId);
            if (cd > 0) {
                return CompletableFuture.completedFuture(ActionResult.failure("mission-on-cooldown"));
            }

            Optional<GuildMember> memberOpt = guild.getMember(requesterId);
            if (memberOpt.isEmpty()) {
                return CompletableFuture.completedFuture(ActionResult.failure("not-in-guild"));
            }
            GuildMember member = memberOpt.get();

            if (!new PermissionEvaluatorImpl().canAct(member, GuildPermissionKey.MISSION_START)) {
                return CompletableFuture.completedFuture(ActionResult.failure("no-permission"));
            }

            Player player = Bukkit.getPlayer(requesterId);
            if (cfg.isPermissionEnabled() && player != null && !player.hasPermission(cfg.getPermissionNode())) {
                return CompletableFuture.completedFuture(ActionResult.failure("mission-no-permission"));
            }

            // Check money
            if (cfg.isMoneyEnabled() && cfg.getMoneyCost() > 0) {
                if (cfg.getMoneyTakeFrom() == MissionConfig.TakeFrom.BANK) {
                    if (!economy.has(guildId, cfg.getMoneyCost())) {
                        return CompletableFuture.completedFuture(ActionResult.failure("bank-insufficient-funds"));
                    }
                } else if (cfg.getMoneyTakeFrom() == MissionConfig.TakeFrom.PLAYER) {
                    if (!economy.has(requesterId, cfg.getMoneyCost())) {
                        return CompletableFuture.completedFuture(ActionResult.failure("mission-insufficient-funds"));
                    }
                }
            }

            // Check items
            if (cfg.isItemsEnabled() && player != null) {
                if (!hasRequiredItems(player, cfg.getItemRequirements())) {
                    return CompletableFuture.completedFuture(ActionResult.failure("mission-missing-items"));
                }
            }

            // Deduct money
            if (cfg.isMoneyEnabled() && cfg.getMoneyCost() > 0) {
                if (cfg.getMoneyTakeFrom() == MissionConfig.TakeFrom.BANK) {
                    economy.withdraw(guildId, cfg.getMoneyCost());
                } else {
                    economy.withdraw(requesterId, cfg.getMoneyCost());
                }
            }

            // Deduct items on player's region/main thread
            if (cfg.isItemsEnabled() && player != null) {
                scheduler.runForEntity(player, () -> removeRequiredItems(player, cfg.getItemRequirements()));
            }

            // Start active mission
            Instant startedAt = Instant.now();
            Instant expiresAt = startedAt.plusSeconds(cfg.getDurationSeconds());

            List<MissionTaskProgress> taskList = new ArrayList<>();
            for (MissionTaskDefinition def : cfg.getTasks()) {
                taskList.add(new MissionTaskProgress(def.getId(), 0L, def.getTarget()));
            }

            ActiveMission mission = new ActiveMission(guildId, startedAt, expiresAt, taskList);
            activeMissions.put(guildId, mission);

            GuildMissionData data = missionDataCache.computeIfAbsent(guildId, k -> new GuildMissionData(guildId, null, null, null));
            data.setActiveMission(mission);
            data.setLastStartedAt(startedAt);

            return missionRepo.save(data).thenApply(v -> {
                initBossBar(guildId, mission);
                eventDispatcher.fire(new GuildMissionStartEvent(guild, mission));

                for (GuildMember m : guild.getMembers()) {
                    Player p = Bukkit.getPlayer(m.getPlayerId());
                    if (p != null && p.isOnline()) {
                        plugin.getMessages().send(p, "mission-started",
                                Placeholder.unparsed("player", player != null ? player.getName() : "Someone"),
                                Placeholder.unparsed("duration", formatDuration(cfg.getDurationSeconds())));
                    }
                }

                return ActionResult.success();
            });
        });
    }

    @Override
    public CompletableFuture<ActionResult> cancelMission(UUID guildId, UUID requesterId) {
        if (!isMissionActive(guildId)) {
            return CompletableFuture.completedFuture(ActionResult.failure("mission-not-active"));
        }

        return guildRepo.findById(guildId).thenCompose(optGuild -> {
            if (optGuild.isEmpty()) return CompletableFuture.completedFuture(ActionResult.failure("invalid-guild"));
            Guild guild = optGuild.get();

            Optional<GuildMember> memberOpt = guild.getMember(requesterId);
            if (memberOpt.isEmpty() || !new PermissionEvaluatorImpl().canAct(memberOpt.get(), GuildPermissionKey.MISSION_START)) {
                return CompletableFuture.completedFuture(ActionResult.failure("no-permission"));
            }

            activeMissions.remove(guildId);
            removeBossBar(guildId);

            GuildMissionData data = missionDataCache.get(guildId);
            if (data != null) {
                data.setActiveMission(null);
                missionRepo.save(data);
            }

            for (GuildMember m : guild.getMembers()) {
                Player p = Bukkit.getPlayer(m.getPlayerId());
                if (p != null && p.isOnline()) {
                    plugin.getMessages().send(p, "mission-cancelled");
                }
            }

            return CompletableFuture.completedFuture(ActionResult.success());
        });
    }

    @Override
    public CompletableFuture<Boolean> addProgress(UUID playerId, String taskId, long amount) {
        if (playerId == null || taskId == null || amount <= 0) {
            return CompletableFuture.completedFuture(false);
        }
        return guildRepo.findByMember(playerId).thenCompose(optGuild -> {
            if (optGuild.isEmpty()) return CompletableFuture.completedFuture(false);
            return addProgress(optGuild.get().getId(), playerId, taskId, amount);
        });
    }

    @Override
    public CompletableFuture<Boolean> addProgress(UUID guildId, UUID playerId, String taskId, long amount) {
        if (guildId == null || taskId == null || amount <= 0) {
            return CompletableFuture.completedFuture(false);
        }

        ActiveMission mission = activeMissions.get(guildId);
        if (mission == null || mission.isExpired()) {
            return CompletableFuture.completedFuture(false);
        }

        Optional<MissionTaskProgress> taskOpt = mission.getTask(taskId);
        if (taskOpt.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        MissionTaskProgress task = taskOpt.get();
        if (task.isCompleted()) {
            return CompletableFuture.completedFuture(false);
        }

        long gained = task.addProgress(playerId, amount);
        if (gained <= 0) {
            return CompletableFuture.completedFuture(false);
        }

        return guildRepo.findById(guildId).thenApply(optGuild -> {
            if (optGuild.isPresent()) {
                Guild guild = optGuild.get();
                eventDispatcher.fire(new GuildMissionTaskProgressEvent(guild, mission, task, playerId, gained));

                if (task.isCompleted()) {
                    eventDispatcher.fire(new GuildMissionTaskCompleteEvent(guild, mission, task));

                    MissionTaskDefinition def = getTaskDefinitions().stream()
                            .filter(d -> d.getId().equalsIgnoreCase(taskId))
                            .findFirst().orElse(null);
                    String taskName = def != null ? def.getDisplayName() : taskId;

                    for (GuildMember m : guild.getMembers()) {
                        Player p = Bukkit.getPlayer(m.getPlayerId());
                        if (p != null && p.isOnline()) {
                            plugin.getMessages().send(p, "mission-task-completed",
                                    Placeholder.parsed("task", taskName));
                        }
                    }
                }

                updateBossBar(guildId, mission);

                GuildMissionData data = missionDataCache.get(guildId);
                if (data != null) {
                    missionRepo.save(data);
                }

                if (mission.isAllCompleted()) {
                    handleMissionSuccess(guildId, mission);
                }
            }
            return true;
        });
    }

    @Override
    public CompletableFuture<Boolean> addProgressByCustomId(UUID playerId, String customId, long amount) {
        if (playerId == null || customId == null || amount <= 0) {
            return CompletableFuture.completedFuture(false);
        }

        return guildRepo.findByMember(playerId).thenCompose(optGuild -> {
            if (optGuild.isEmpty()) return CompletableFuture.completedFuture(false);
            UUID guildId = optGuild.get().getId();

            ActiveMission mission = activeMissions.get(guildId);
            if (mission == null || mission.isExpired()) {
                return CompletableFuture.completedFuture(false);
            }

            boolean anyAdded = false;
            for (MissionTaskDefinition def : getTaskDefinitions()) {
                if (def.getType() == MissionTaskType.CUSTOM) {
                    String cid = def.getStringProperty("custom_id", def.getId());
                    if (customId.equalsIgnoreCase(cid) || customId.equalsIgnoreCase(def.getId())) {
                        addProgress(guildId, playerId, def.getId(), amount);
                        anyAdded = true;
                    }
                }
            }
            return CompletableFuture.completedFuture(anyAdded);
        });
    }

    private void handleMissionSuccess(UUID guildId, ActiveMission mission) {
        activeMissions.remove(guildId);
        removeBossBar(guildId);

        MissionConfig cfg = configManager.getMissionConfig();
        guildRepo.findById(guildId).thenAccept(optGuild -> {
            if (optGuild.isEmpty()) return;
            Guild guild = optGuild.get();

            // Give XP
            guild.addXp(cfg.getRewardXp());
            guildRepo.save(guild);

            // Give money to bank
            if (cfg.getRewardMoney() > 0) {
                economy.deposit(guildId, cfg.getRewardMoney());
            }

            GuildMissionData data = missionDataCache.computeIfAbsent(guildId, k -> new GuildMissionData(guildId, null, null, null));
            data.setActiveMission(null);
            data.setLastCompletedAt(Instant.now());
            missionRepo.save(data);

            eventDispatcher.fire(new GuildMissionCompleteEvent(guild, mission, cfg.getRewardXp(), cfg.getRewardMoney()));

            for (GuildMember m : guild.getMembers()) {
                Player p = Bukkit.getPlayer(m.getPlayerId());
                if (p != null && p.isOnline()) {
                    plugin.getMessages().send(p, "mission-completed",
                            Placeholder.unparsed("xp", String.valueOf(cfg.getRewardXp())),
                            Placeholder.unparsed("money", economy.format(cfg.getRewardMoney())));
                }
            }
        });
    }

    private void handleMissionFailure(UUID guildId, ActiveMission mission) {
        activeMissions.remove(guildId);
        removeBossBar(guildId);

        MissionConfig cfg = configManager.getMissionConfig();
        guildRepo.findById(guildId).thenAccept(optGuild -> {
            if (optGuild.isEmpty()) return;
            Guild guild = optGuild.get();

            // Deduct XP (XP can go negative, level does not drop)
            guild.setXp(guild.getXp() - cfg.getPenaltyXp());
            guildRepo.save(guild);

            // Deduct money from guild bank
            if (cfg.getPenaltyMoney() > 0) {
                double currentBal = economy.getBalance(guildId);
                double toDeduct = Math.min(currentBal, cfg.getPenaltyMoney());
                if (toDeduct > 0) {
                    economy.withdraw(guildId, toDeduct);
                }
            }

            GuildMissionData data = missionDataCache.computeIfAbsent(guildId, k -> new GuildMissionData(guildId, null, null, null));
            data.setActiveMission(null);
            missionRepo.save(data);

            eventDispatcher.fire(new GuildMissionFailEvent(guild, mission, cfg.getPenaltyXp(), cfg.getPenaltyMoney()));

            for (GuildMember m : guild.getMembers()) {
                Player p = Bukkit.getPlayer(m.getPlayerId());
                if (p != null && p.isOnline()) {
                    plugin.getMessages().send(p, "mission-failed",
                            Placeholder.unparsed("xp", String.valueOf(cfg.getPenaltyXp())),
                            Placeholder.unparsed("money", economy.format(cfg.getPenaltyMoney())));
                }
            }
        });
    }

    // ── BossBar Handling ──────────────────────────────────────────────────────

    private void initBossBar(UUID guildId, ActiveMission mission) {
        MissionConfig cfg = configManager.getMissionConfig();
        if (!cfg.isBossbarEnabled()) return;

        BossBar bar = BossBar.bossBar(
                Component.text("Guild Mission"),
                (float) mission.getOverallProgressFraction(),
                cfg.getBossbarColor(),
                cfg.getBossbarOverlay()
        );
        guildBossBars.put(guildId, bar);
        updateBossBar(guildId, mission);

        guildRepo.findById(guildId).thenAccept(optGuild -> {
            if (optGuild.isEmpty()) return;
            for (GuildMember m : optGuild.get().getMembers()) {
                Player p = Bukkit.getPlayer(m.getPlayerId());
                if (p != null && p.isOnline()) {
                    p.showBossBar(bar);
                }
            }
        });
    }

    private void updateBossBar(UUID guildId, ActiveMission mission) {
        BossBar bar = guildBossBars.get(guildId);
        if (bar == null) return;

        MissionConfig cfg = configManager.getMissionConfig();
        float fraction = (float) mission.getOverallProgressFraction();
        bar.progress(Math.max(0.0f, Math.min(1.0f, fraction)));

        String timeStr = formatDuration(mission.getRemainingSeconds());
        int percent = mission.getOverallProgressPercent();
        int completed = mission.getCompletedTaskCount();
        int total = mission.getTotalTaskCount();

        Component title = Text.parse(cfg.getBossbarTitle(),
                Placeholder.unparsed("percent", String.valueOf(percent)),
                Placeholder.unparsed("completed_tasks", String.valueOf(completed)),
                Placeholder.unparsed("total_tasks", String.valueOf(total)),
                Placeholder.unparsed("time_remaining", timeStr));

        bar.name(title);
    }

    public void showBossBarToPlayer(Player player, UUID guildId) {
        BossBar bar = guildBossBars.get(guildId);
        if (bar != null) {
            player.showBossBar(bar);
        }
    }

    public void hideBossBarFromPlayer(Player player, UUID guildId) {
        BossBar bar = guildBossBars.get(guildId);
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    private void removeBossBar(UUID guildId) {
        BossBar bar = guildBossBars.remove(guildId);
        if (bar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(bar);
            }
        }
    }

    // ── Admin Operations ──────────────────────────────────────────────────────

    @Override
    public CompletableFuture<Void> adminStartMission(UUID guildId) {
        MissionConfig cfg = configManager.getMissionConfig();
        Instant startedAt = Instant.now();
        Instant expiresAt = startedAt.plusSeconds(cfg.getDurationSeconds());

        List<MissionTaskProgress> taskList = new ArrayList<>();
        for (MissionTaskDefinition def : cfg.getTasks()) {
            taskList.add(new MissionTaskProgress(def.getId(), 0L, def.getTarget()));
        }

        ActiveMission mission = new ActiveMission(guildId, startedAt, expiresAt, taskList);
        activeMissions.put(guildId, mission);

        GuildMissionData data = missionDataCache.computeIfAbsent(guildId, k -> new GuildMissionData(guildId, null, null, null));
        data.setActiveMission(mission);
        data.setLastStartedAt(startedAt);

        return missionRepo.save(data).thenRun(() -> {
            initBossBar(guildId, mission);
            guildRepo.findById(guildId).thenAccept(optGuild -> {
                optGuild.ifPresent(guild -> eventDispatcher.fire(new GuildMissionStartEvent(guild, mission)));
            });
        });
    }

    @Override
    public CompletableFuture<Void> adminStopMission(UUID guildId) {
        activeMissions.remove(guildId);
        removeBossBar(guildId);

        GuildMissionData data = missionDataCache.get(guildId);
        if (data != null) {
            data.setActiveMission(null);
            return missionRepo.save(data);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> adminCompleteMission(UUID guildId) {
        ActiveMission mission = activeMissions.get(guildId);
        if (mission != null) {
            handleMissionSuccess(guildId, mission);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> adminAddProgress(UUID guildId, String taskId, long amount, UUID contributorId) {
        return addProgress(guildId, contributorId, taskId, amount).thenApply(v -> null);
    }

    @Override
    public CompletableFuture<Void> adminRemoveProgress(UUID guildId, String taskId, long amount, UUID contributorId) {
        ActiveMission mission = activeMissions.get(guildId);
        if (mission != null) {
            mission.getTask(taskId).ifPresent(t -> {
                t.removeProgress(contributorId, amount);
                updateBossBar(guildId, mission);
                GuildMissionData data = missionDataCache.get(guildId);
                if (data != null) missionRepo.save(data);
            });
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> adminResetCooldown(UUID guildId) {
        GuildMissionData data = missionDataCache.get(guildId);
        if (data != null) {
            data.setLastStartedAt(null);
            return missionRepo.save(data);
        }
        return CompletableFuture.completedFuture(null);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static boolean hasRequiredItems(Player player, List<ItemRequirement> requirements) {
        for (ItemRequirement req : requirements) {
            Material mat = Material.matchMaterial(req.material());
            if (mat == null || mat.isAir()) continue;
            if (!player.getInventory().containsAtLeast(new ItemStack(mat), req.amount())) {
                return false;
            }
        }
        return true;
    }

    private static void removeRequiredItems(Player player, List<ItemRequirement> requirements) {
        for (ItemRequirement req : requirements) {
            Material mat = Material.matchMaterial(req.material());
            if (mat == null || mat.isAir()) continue;
            player.getInventory().removeItem(new ItemStack(mat, req.amount()));
        }
    }

    public static String formatDuration(long totalSeconds) {
        if (totalSeconds <= 0) return "00:00";
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }
}
