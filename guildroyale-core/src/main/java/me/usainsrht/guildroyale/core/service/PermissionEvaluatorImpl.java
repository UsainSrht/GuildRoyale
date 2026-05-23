package me.usainsrht.guildroyale.core.service;

import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import me.usainsrht.guildroyale.api.permission.PermissionEvaluator;

/**
 * Checks whether a guild member may perform an action, enforcing:
 * <ol>
 *   <li>The actor's role carries the required {@link GuildPermissionKey}.</li>
 *   <li>When a target is involved, the actor's role index is strictly less than
 *       the target's role index (actor outranks the target).</li>
 * </ol>
 */
public final class PermissionEvaluatorImpl implements PermissionEvaluator {

    @Override
    public boolean canAct(GuildMember actor, GuildPermissionKey key) {
        return actor.getRole().hasPermission(key);
    }

    @Override
    public boolean canActOn(GuildMember actor, GuildMember target, GuildPermissionKey key) {
        if (!canAct(actor, key)) return false;
        return actor.getRole().getIndex() < target.getRole().getIndex();
    }
}
