package me.usainsrht.guildroyale.core.listener;

import io.papermc.paper.event.player.PlayerPurchaseEvent;
import me.usainsrht.guildroyale.api.domain.mission.MissionTaskDefinition;
import me.usainsrht.guildroyale.api.domain.mission.MissionTaskType;
import me.usainsrht.guildroyale.api.service.GuildService;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.event.GuildMemberJoinEvent;
import me.usainsrht.guildroyale.core.event.GuildMemberKickedEvent;
import me.usainsrht.guildroyale.core.event.GuildMemberLeaveEvent;
import me.usainsrht.guildroyale.core.service.MissionServiceImpl;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.List;

/**
 * Listens for in-game actions that progress guild missions.
 */
public final class MissionListener implements Listener {

    private final GuildRoyalePlugin plugin;
    private final MissionServiceImpl missionService;
    private final GuildService guildService;

    public MissionListener(GuildRoyalePlugin plugin, MissionServiceImpl missionService, GuildService guildService) {
        this.plugin = plugin;
        this.missionService = missionService;
        this.guildService = guildService;
    }

    // ── Block Break ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();
        String blockName = blockType.name();

        for (MissionTaskDefinition task : missionService.getTaskDefinitions()) {
            if (task.getType() != MissionTaskType.BLOCK_BREAK) continue;

            List<String> allowedBlocks = task.getStringListProperty("blocks");
            String singleBlock = task.getStringProperty("block", null);

            boolean match = false;
            if (allowedBlocks != null && !allowedBlocks.isEmpty()) {
                match = allowedBlocks.stream().anyMatch(b -> b.equalsIgnoreCase(blockName));
            } else if (singleBlock != null) {
                match = singleBlock.equalsIgnoreCase(blockName);
            } else {
                // If no filter specified, matches icon material
                match = task.getIconMaterial().equalsIgnoreCase(blockName);
            }

            if (match) {
                missionService.addProgress(player.getUniqueId(), task.getId(), 1L);
            }
        }
    }

    // ── Entity Kill ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        String entityType = entity.getType().name();

        for (MissionTaskDefinition task : missionService.getTaskDefinitions()) {
            if (task.getType() != MissionTaskType.KILL_ENTITY) continue;

            List<String> allowedEntities = task.getStringListProperty("entities");
            String singleEntity = task.getStringProperty("entity", null);

            boolean match = false;
            if (allowedEntities != null && !allowedEntities.isEmpty()) {
                match = allowedEntities.stream().anyMatch(e -> e.equalsIgnoreCase(entityType));
            } else if (singleEntity != null) {
                match = singleEntity.equalsIgnoreCase(entityType);
            } else {
                match = task.getIconMaterial().replace("_HEAD", "").replace("_SPAWN_EGG", "")
                        .equalsIgnoreCase(entityType);
            }

            if (match) {
                missionService.addProgress(killer.getUniqueId(), task.getId(), 1L);
            }
        }
    }

    // ── Craft Item ────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack result = event.getRecipe().getResult();
        if (result == null || result.getType().isAir()) return;

        String matName = result.getType().name();
        long amountCrafted = event.isShiftClick()
                ? calculateShiftCraftAmount(event)
                : result.getAmount();

        if (amountCrafted <= 0) return;

        for (MissionTaskDefinition task : missionService.getTaskDefinitions()) {
            if (task.getType() != MissionTaskType.CRAFT_ITEM) continue;

            List<String> allowedItems = task.getStringListProperty("items");
            String singleItem = task.getStringProperty("item", null);

            boolean match = false;
            if (allowedItems != null && !allowedItems.isEmpty()) {
                match = allowedItems.stream().anyMatch(i -> i.equalsIgnoreCase(matName));
            } else if (singleItem != null) {
                match = singleItem.equalsIgnoreCase(matName);
            } else {
                match = task.getIconMaterial().equalsIgnoreCase(matName);
            }

            if (match) {
                missionService.addProgress(player.getUniqueId(), task.getId(), amountCrafted);
            }
        }
    }

    // ── Villager Trades ───────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPurchase(PlayerPurchaseEvent event) {
        Player player = event.getPlayer();
        MerchantRecipe recipe = event.getTrade();
        if (recipe == null) return;

        ItemStack result = recipe.getResult();
        List<ItemStack> ingredients = recipe.getIngredients();

        for (MissionTaskDefinition task : missionService.getTaskDefinitions()) {
            if (task.getType() == MissionTaskType.VILLAGER_TRADE) {
                missionService.addProgress(player.getUniqueId(), task.getId(), 1L);
            } else if (task.getType() == MissionTaskType.VILLAGER_TRADE_ITEM) {
                String targetMat = task.getStringProperty("item", task.getIconMaterial());
                String mode = task.getStringProperty("mode", "ANY"); // GIVEN, RECEIVED, ANY

                long tradedAmount = 0L;

                boolean checkGiven = "GIVEN".equalsIgnoreCase(mode) || "ANY".equalsIgnoreCase(mode);
                boolean checkReceived = "RECEIVED".equalsIgnoreCase(mode) || "ANY".equalsIgnoreCase(mode);

                if (checkReceived && result != null && result.getType().name().equalsIgnoreCase(targetMat)) {
                    tradedAmount += result.getAmount();
                }

                if (checkGiven && ingredients != null) {
                    for (ItemStack ing : ingredients) {
                        if (ing != null && ing.getType().name().equalsIgnoreCase(targetMat)) {
                            tradedAmount += ing.getAmount();
                        }
                    }
                }

                if (tradedAmount > 0) {
                    missionService.addProgress(player.getUniqueId(), task.getId(), tradedAmount);
                }
            }
        }
    }

    // ── Player Join / Quit / Guild Membership BossBar Sync ───────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        guildService.getGuildByMember(player.getUniqueId()).thenAccept(optGuild -> {
            if (optGuild.isPresent()) {
                missionService.showBossBarToPlayer(player, optGuild.get().getId());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        guildService.getGuildByMember(player.getUniqueId()).thenAccept(optGuild -> {
            if (optGuild.isPresent()) {
                missionService.hideBossBarFromPlayer(player, optGuild.get().getId());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMemberJoin(GuildMemberJoinEvent event) {
        Player player = Bukkit.getPlayer(event.getMember().getPlayerId());
        if (player != null && player.isOnline()) {
            missionService.showBossBarToPlayer(player, event.getGuild().getId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMemberLeave(GuildMemberLeaveEvent event) {
        Player player = Bukkit.getPlayer(event.getMember().getPlayerId());
        if (player != null && player.isOnline()) {
            missionService.hideBossBarFromPlayer(player, event.getGuild().getId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMemberKicked(GuildMemberKickedEvent event) {
        Player player = Bukkit.getPlayer(event.getKickedMember().getPlayerId());
        if (player != null && player.isOnline()) {
            missionService.hideBossBarFromPlayer(player, event.getGuild().getId());
        }
    }

    private static long calculateShiftCraftAmount(CraftItemEvent event) {
        ItemStack[] matrix = event.getInventory().getMatrix();
        int maxCrafts = Integer.MAX_VALUE;
        for (ItemStack is : matrix) {
            if (is != null && !is.getType().isAir()) {
                maxCrafts = Math.min(maxCrafts, is.getAmount());
            }
        }
        if (maxCrafts == Integer.MAX_VALUE || maxCrafts <= 0) maxCrafts = 1;
        ItemStack result = event.getRecipe().getResult();
        return (long) maxCrafts * result.getAmount();
    }
}
