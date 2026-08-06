# GuildRoyale

A modern guilds plugin for Paper **26.2**, with full Folia support.

Guilds have levels, roles with granular permissions, shared storage, a bank, cosmetic
badges and a global leaderboard. Everything players see is configurable — command
labels, GUI layouts and messages all live in YAML.

---

## Requirements

| | |
|---|---|
| Server | Paper 26.2+ (Folia supported) |
| Java | 25 |
| Required plugin | [Vault](https://www.spigotmc.org/resources/vault.34315/) or VaultUnlocked |
| Optional plugin | [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) |

Economy features (creation cost, badge purchases, the guild bank) degrade gracefully:
if no economy provider is registered, they are treated as free.

## Installation

1. Download `guildroyale-core-<version>.jar` from the releases page, or build it yourself.
2. Drop it into `plugins/`.
3. Start the server once to generate `config.yml`, `messages.yml` and `guis.yml`.
4. Edit those files, then restart.

Database drivers, HikariCP, Gson and MorePaperLib are **not** bundled — Paper's plugin
loader downloads them at startup, so the jar stays small.

## Building

```bash
mvn clean package
```

The shaded plugin jar is written to `guildroyale-core/target/`.

---

## Modules

| Module | Purpose |
|---|---|
| `guildroyale-api` | Domain model, service and repository interfaces. Plain Java, no Minecraft dependency — this is what other plugins compile against. |
| `guildroyale-core` | Paper implementation: commands, GUIs, storage backends and integrations. |

The layering is one-directional:

```
commands / GUIs / dialogs / listeners
              ↓
        service layer
              ↓
   GuildRepository (JSON | SQLite | MySQL)
              ↓
      domain model (guildroyale-api)
```

---

## Commands

All labels and aliases below are the defaults; every one of them can be renamed under
`commands:` in `config.yml`. Permission nodes are hardcoded and never change when you
rename a label.

### `/guild` (alias `/g`)

| Subcommand | Permission | Description |
|---|---|---|
| `create` (`c`, `new`) | `guildroyale.command.create` | Create a guild |
| `disband` | `guildroyale.command.disband` | Disband your guild |
| `info` | `guildroyale.command.info` | Show guild information |
| `invite` | `guildroyale.command.invite` | Invite a player |
| `join` | `guildroyale.command.join` | Accept a pending invite |
| `leave` | `guildroyale.command.leave` | Leave your guild |
| `kick` | `guildroyale.command.kick` | Remove a member |
| `role` (`roles`) | `guildroyale.command.role` | `create`, `delete`, `rename`, `setpermission` |
| `icon` | `guildroyale.command.icon` | Set the guild icon |
| `shortname` (`tag`, `sn`) | `guildroyale.command.shortname` | Change the guild tag |
| `leaderboard` (`lb`, `top`) | `guildroyale.command.leaderboard` | Global rankings |
| `leader` | `guildroyale.command.leader` | Transfer leadership |
| `menu` (`gui`, `hub`) | `guildroyale.command.menu` | Open the main menu |
| `badge` (`badges`) | `guildroyale.command.badge` | `list`, `buy`, `equip` |
| `storage` (`chest`) | `guildroyale.command.storage` | Open shared storage |
| `bank` (`b`) | `guildroyale.command.bank` | `balance`, `deposit`, `withdraw` |
| `help` (`?`) | `guildroyale.command.help` | Command overview |

### `/guildadmin` (alias `/ga`)

Requires `guildroyale.admin`.

| Subcommand | Description |
|---|---|
| `reload` | Reload `config.yml`, `messages.yml` and `guis.yml` |
| `addxp` | Grant XP to a guild |
| `setlevel` | Set a guild's level |
| `delete` | Force-delete a guild |
| `badge grant` / `badge revoke` | Manage a guild's badges |

> Command **names and aliases** are registered with Brigadier at startup. Changing them
> requires a full server restart — `/guildadmin reload` will not pick them up.

---

## Configuration

`config.yml` is grouped into the following sections.

| Section | What it controls |
|---|---|
| `storage` | Backend (`JSON`, `SQLITE`, `MYSQL`) and connection settings |
| `creation` | Permission, money cost and item cost for creating a guild — each toggled independently |
| `xp` | Level curve (`base * multiplier^(n-1)`) and level cap |
| `features` | The level at which shortname, badge, storage and bank unlock |
| `badges` | Badge definitions: display text, cost, grantable |
| `leaderboard` | Page size and cache refresh interval |
| `invite` | Invite expiry |
| `commands` | Command and subcommand labels and aliases |
| `default-roles` | Roles given to a new guild, and their permissions |

`messages.yml` holds every player-facing string in
[MiniMessage](https://docs.advntr.dev/minimessage/format.html) format. A message may be a
plain string or a map with `chat`, `sound`, `actionbar` and `title` parts.

`guis.yml` defines the layout of every menu — slot indices, item materials, names and
lores — plus the text of the input dialogs.

### Storage backends

| Backend | Best for |
|---|---|
| `JSON` | Single server, small networks. Default. One file per guild. |
| `SQLITE` | Single server wanting a real database. WAL mode enabled. |
| `MYSQL` | Multiple servers sharing one guild database. Pooled through HikariCP. |

### Role permissions

Roles are ordered by index; index `0` is always the Leader and always has every
permission. A member may only act on members of a strictly higher index.

Available keys: `MEMBER_MANAGEMENT`, `INVITE`, `KICK`, `ROLE_MANAGEMENT`, `GUILD_SETTINGS`,
`ICON_CHANGE`, `SHORTNAME_CHANGE`, `BADGE_MANAGE`, `STORAGE_ACCESS`, `BANK_VIEW`,
`BANK_DEPOSIT`, `BANK_WITHDRAW`, `DISBANDMENT`.

---

## PlaceholderAPI

Registered automatically when PlaceholderAPI is installed.

| Placeholder | Value |
|---|---|
| `%guildroyale_guild_name%` | Guild name, or empty |
| `%guildroyale_guild_shortname%` | Guild tag |
| `%guildroyale_guild_level%` | Guild level |
| `%guildroyale_guild_xp%` | Guild XP |
| `%guildroyale_guild_members%` | Member count |
| `%guildroyale_role%` | The player's role name |
| `%guildroyale_badge%` | Active badge display text |
| `%guildroyale_top_name_<n>%` | Name of the n-th ranked guild |
| `%guildroyale_top_level_<n>%` | Level of the n-th ranked guild |
| `%guildroyale_top_xp_<n>%` | XP of the n-th ranked guild |

`top_*` placeholders read from the leaderboard cache. Per-player placeholders query the
repository and block, so avoid them in scoreboard refresh loops on a SQL backend.

---

## Developer API

Add the API module and use the services registered by the plugin:

```java
GuildRoyalePlugin plugin = (GuildRoyalePlugin) Bukkit.getPluginManager().getPlugin("GuildRoyale");
GuildService guilds = plugin.getGuildService();

guilds.getGuildByMember(player.getUniqueId())
        .thenAccept(optional -> optional.ifPresent(guild ->
                plugin.getLogger().info(player.getName() + " is in " + guild.getName())));
```

Every service method returns a `CompletableFuture`. Callbacks run on a repository thread,
so hop back to the main thread before touching players or the world.

### Events

All events live in `me.usainsrht.guildroyale.core.event` and are fired on the main thread:
`GuildCreatedEvent`, `GuildDisbandedEvent`, `GuildLevelUpEvent`, `GuildXpGainedEvent`,
`GuildMemberJoinEvent`, `GuildMemberLeaveEvent`, `GuildMemberKickedEvent`,
`GuildRoleChangedEvent`.

---

## License

Not yet licensed. Until a license is added, all rights are reserved by the author.
