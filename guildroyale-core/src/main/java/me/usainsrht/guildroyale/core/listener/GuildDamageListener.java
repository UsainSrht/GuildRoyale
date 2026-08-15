package me.usainsrht.guildroyale.core.listener;

import me.usainsrht.guildroyale.api.service.GuildService;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Handles PVP damage cancellation based on guild friendly fire settings.
 *
 * <p>Only direct player damage and projectile damage with a player owner are evaluated.
 * Self hits are allowed.
 */
public final class GuildDamageListener implements Listener {

    private final GuildService guildService;

    public GuildDamageListener(GuildService guildService) {
        this.guildService = guildService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player p) {
                attacker = p;
            }
        }

        if (attacker == null) {
            return;
        }

        if (!guildService.canHit(attacker.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
