package me.usainsrht.guildroyale.api.permission;

/**
 * String-keyed permission that controls actions within a guild.
 *
 * <p>Permission checks always validate two conditions:
 * <ol>
 *   <li>The actor's role carries this permission.</li>
 *   <li>Where a target member is involved, the actor's role index is
 *       <em>strictly less than</em> the target's role index (i.e. the actor
 *       outranks the target).</li>
 * </ol>
 */
public enum GuildPermissionKey {

    /** Invite new members and kick existing members. */
    MEMBER_MANAGEMENT,

    /** Create, delete, rename, or modify role permissions/icons. */
    ROLE_MANAGEMENT,

    /** Change guild name, shortname, or other guild-wide settings. */
    GUILD_SETTINGS,

    /** Change the guild icon. */
    ICON_CHANGE,

    /** Change the guild shortname (may have associated cost). */
    SHORTNAME_CHANGE,

    /** Disband the guild entirely. */
    DISBANDMENT,

    /** Send invitations to players outside the guild. */
    INVITE,

    /** Kick members from the guild. */
    KICK;

    /** Returns the lowercase string key used in config/storage. */
    public String key() { return name().toLowerCase(); }
}
