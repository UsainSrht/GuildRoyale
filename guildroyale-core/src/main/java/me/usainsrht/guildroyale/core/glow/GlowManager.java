package me.usainsrht.guildroyale.core.glow;

import fr.skytasul.glowingentities.GlowingEntities;
import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.GuildRole;
import me.usainsrht.guildroyale.api.domain.RoleColor;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages per-player guild member glow states using {@link GlowingEntities}.
 * When a guild has the glow setting enabled, all online members glow to each other
 * with their assigned role's glow color.
 */
public final class GlowManager {

    private final GuildRoyalePlugin plugin;
    private final GlowingEntities glowingEntities;

    public GlowManager(GuildRoyalePlugin plugin) {
        this.plugin = plugin;
        this.glowingEntities = new GlowingEntities(plugin);
    }

    /**
     * Updates the glow effects for all online members of a guild.
     */
    public void updateGuildGlow(Guild guild) {
        if (guild == null) return;
        plugin.getScheduler().runOnMainThread(() -> {
            Map<Player, GuildRole> onlineMembers = new HashMap<>();
            for (GuildMember m : guild.getMembers()) {
                Player p = Bukkit.getPlayer(m.getPlayerId());
                if (p != null && p.isOnline()) {
                    onlineMembers.put(p, m.getRole());
                }
            }

            boolean enabled = guild.isGlow();
            for (Map.Entry<Player, GuildRole> entryA : onlineMembers.entrySet()) {
                Player playerA = entryA.getKey();
                ChatColor colorA = toChatColor(entryA.getValue().getGlowColor());

                for (Player playerB : onlineMembers.keySet()) {
                    if (playerA.equals(playerB)) continue;
                    try {
                        if (enabled) {
                            glowingEntities.setGlowing(playerA, playerB, colorA);
                        } else {
                            glowingEntities.unsetGlowing(playerA, playerB);
                        }
                    } catch (ReflectiveOperationException e) {
                        plugin.getSLF4JLogger().warn("Failed to update glow from {} to {}",
                                playerA.getName(), playerB.getName(), e);
                    }
                }
            }
        });
    }

    /**
     * Synchronizes glowing between a specific online player and all other online members of their guild.
     */
    public void updatePlayerGlow(Player player) {
        if (player == null || !player.isOnline()) return;
        plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenAccept(opt -> {
            if (opt.isEmpty()) return;
            Guild guild = opt.get();
            plugin.getScheduler().runForEntity(player, () -> {
                if (!player.isOnline()) return;
                Optional<GuildMember> currentMemberOpt = guild.getMember(player.getUniqueId());
                if (currentMemberOpt.isEmpty()) return;

                GuildRole currentRole = currentMemberOpt.get().getRole();
                ChatColor currentColor = toChatColor(currentRole.getGlowColor());
                boolean enabled = guild.isGlow();

                for (GuildMember m : guild.getMembers()) {
                    if (m.getPlayerId().equals(player.getUniqueId())) continue;
                    Player other = Bukkit.getPlayer(m.getPlayerId());
                    if (other != null && other.isOnline()) {
                        ChatColor otherColor = toChatColor(m.getRole().getGlowColor());
                        try {
                            if (enabled) {
                                glowingEntities.setGlowing(player, other, currentColor);
                                glowingEntities.setGlowing(other, player, otherColor);
                            } else {
                                glowingEntities.unsetGlowing(player, other);
                                glowingEntities.unsetGlowing(other, player);
                            }
                        } catch (ReflectiveOperationException e) {
                            plugin.getSLF4JLogger().warn("Failed to update glow between {} and {}",
                                    player.getName(), other.getName(), e);
                        }
                    }
                }
            });
        });
    }

    /**
     * Cleans up all glow effects between a player and online members of the specified guild.
     */
    public void removePlayerGlow(Player player, Guild guild) {
        if (player == null || guild == null) return;
        plugin.getScheduler().runOnMainThread(() -> {
            for (GuildMember m : guild.getMembers()) {
                if (m.getPlayerId().equals(player.getUniqueId())) continue;
                Player other = Bukkit.getPlayer(m.getPlayerId());
                if (other != null && other.isOnline()) {
                    try {
                        glowingEntities.unsetGlowing(player, other);
                        glowingEntities.unsetGlowing(other, player);
                    } catch (ReflectiveOperationException e) {
                        plugin.getSLF4JLogger().warn("Failed to remove glow between {} and {}",
                                player.getName(), other.getName(), e);
                    }
                }
            }
        });
    }

    /**
     * Cleans up all glow effects between a player ID and online members of the specified guild.
     */
    public void removePlayerGlow(UUID playerId, Guild guild) {
        if (playerId == null || guild == null) return;
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            removePlayerGlow(player, guild);
        }
    }

    /**
     * Maps {@link RoleColor} to Bukkit {@link ChatColor} for GlowingEntities.
     */
    public static ChatColor toChatColor(RoleColor color) {
        if (color == null) return ChatColor.WHITE;
        return switch (color) {
            case WHITE -> ChatColor.WHITE;
            case ORANGE -> ChatColor.GOLD;
            case MAGENTA -> ChatColor.LIGHT_PURPLE;
            case LIGHT_BLUE -> ChatColor.AQUA;
            case YELLOW -> ChatColor.YELLOW;
            case LIME -> ChatColor.GREEN;
            case PINK -> ChatColor.LIGHT_PURPLE;
            case GRAY -> ChatColor.DARK_GRAY;
            case LIGHT_GRAY -> ChatColor.GRAY;
            case CYAN -> ChatColor.DARK_AQUA;
            case PURPLE -> ChatColor.DARK_PURPLE;
            case BLUE -> ChatColor.BLUE;
            case BROWN -> ChatColor.GOLD;
            case GREEN -> ChatColor.DARK_GREEN;
            case RED -> ChatColor.RED;
            case BLACK -> ChatColor.BLACK;
        };
    }

    /**
     * Shuts down GlowingEntities and unhooks packet handlers.
     */
    public void shutdown() {
        if (glowingEntities != null) {
            glowingEntities.disable();
        }
    }

    public GlowingEntities getGlowingEntities() {
        return glowingEntities;
    }
}
