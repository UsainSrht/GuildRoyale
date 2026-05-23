package me.usainsrht.guildroyale.api.permission;

import me.usainsrht.guildroyale.api.domain.GuildMember;

/**
 * Evaluates whether a guild member may perform a permission-gated action,
 * optionally against another member.
 *
 * <p>Two conditions must hold:
 * <ol>
 *   <li>The actor's role carries the required {@link GuildPermissionKey}.</li>
 *   <li>If {@code target} is non-null, the actor's role index must be
 *       <em>strictly less than</em> the target's role index.</li>
 * </ol>
 */
public interface PermissionEvaluator {

    /**
     * Returns {@code true} if {@code actor} may perform the action identified by
     * {@code key}.  No target-rank check is performed.
     */
    boolean canAct(GuildMember actor, GuildPermissionKey key);

    /**
     * Returns {@code true} if {@code actor} may perform the action identified by
     * {@code key} against {@code target}.
     *
     * <p>Requires {@code actor.role.index < target.role.index}.
     */
    boolean canActOn(GuildMember actor, GuildMember target, GuildPermissionKey key);
}
