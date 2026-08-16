package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.adapter.ItemStackAdapter;
import me.usainsrht.guildroyale.core.command.subcommand.BankSubcommand;
import me.usainsrht.guildroyale.core.command.subcommand.StorageSubcommand;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.feature.GuildFeature;
import me.usainsrht.guildroyale.core.gui.AbstractGui;
import me.usainsrht.guildroyale.core.gui.GuiItems;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import me.usainsrht.guildroyale.core.message.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Hub GUI for players who are already in a guild.
 * Guild stats are shown as lore on the info icon (no separate info GUI).
 */
public final class GuildMainGui extends AbstractGui {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final Guild guild;
    private final GuildMember viewer;
    private final GuiManager guiManager;
    private final int infoSlot;
    private final int membersSlot;
    private final int rolesSlot;
    private final int leaderboardSlot;
    private final int storageSlot;
    private final int bankSlot;
    private final int badgesSlot;
    private final int permissionsSlot;
    private final int settingsSlot;
    private final int iconSlot;

    public GuildMainGui(Guild guild, GuildMember viewer, GuiManager guiManager) {
        super(size(), title(guild));
        this.guild = guild;
        this.viewer = viewer;
        this.guiManager = guiManager;

        GuiConfig gui = GuiItems.config();
        this.iconSlot = gui != null ? gui.slot("main.slots.icon", 13) : 13;
        this.infoSlot = gui != null ? gui.slot("main.slots.info", 20) : 20;
        this.membersSlot = gui != null ? gui.slot("main.slots.members", 22) : 22;
        this.rolesSlot = gui != null ? gui.slot("main.slots.roles", 24) : 24;
        this.leaderboardSlot = gui != null ? gui.slot("main.slots.leaderboard", 29) : 29;
        this.storageSlot = gui != null ? gui.slot("main.slots.storage", 31) : 31;
        this.bankSlot = gui != null ? gui.slot("main.slots.bank", 33) : 33;
        this.badgesSlot = gui != null ? gui.slot("main.slots.badges", 35) : 35;
        this.permissionsSlot = gui != null ? gui.slot("main.slots.permissions", 38) : 38;
        this.settingsSlot = gui != null ? gui.slot("main.slots.settings", 42) : 42;
    }

    private static int size() {
        GuiConfig gui = GuiItems.config();
        return gui != null ? gui.size("main.size", 54) : 54;
    }

    private static Component title(Guild guild) {
        GuiConfig gui = GuiItems.config();
        if (gui == null) {
            return Component.text("Guild Menu");
        }
        return gui.title("main.title", Placeholder.unparsed("guild", guild.getName()));
    }

    @Override
    protected void build() {
        fillBorder(GuiItems.filler());

        ItemStack icon = ItemStackAdapter.fromSerializable(guild.getIcon());
        if (!icon.getType().isAir()) {
            setSlot(iconSlot, icon);
        }

        setSlot(infoSlot, buildInfoItem());
        setSlot(membersSlot, GuiItems.get("main-members"));
        setSlot(rolesSlot, GuiItems.get("main-roles"));
        setSlot(leaderboardSlot, GuiItems.get("main-leaderboard"));
        setSlot(storageSlot, GuiItems.featureItem(guild, GuildFeature.STORAGE,
                "main-storage", "main-storage-locked"));
        setSlot(bankSlot, GuiItems.featureItem(guild, GuildFeature.BANK,
                "main-bank", "main-bank-locked"));
        setSlot(badgesSlot, GuiItems.featureItem(guild, GuildFeature.BADGE,
                "main-badges", "main-badges-locked"));
        setSlot(permissionsSlot, GuiItems.get("main-permissions"));
        setSlot(settingsSlot, GuiItems.get("main-settings"));
    }

    private ItemStack buildInfoItem() {
        Optional<GuildMember> leaderOpt = guild.getMembers().stream()
                .filter(m -> m.getRole().getIndex() == 0).findFirst();
        String leaderName = leaderOpt.map(m -> Bukkit.getOfflinePlayer(m.getPlayerId()).getName())
                .filter(s -> s != null).orElse("Unknown");

        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        var guildRes = plugin != null
                ? plugin.getTagService().guildResolver(guild, null)
                : Placeholder.unparsed("guild", guild.getName());
        var leaderRes = (plugin != null && leaderOpt.isPresent())
                ? plugin.getTagService().memberResolver(leaderOpt.get(), guild, null)
                : Placeholder.unparsed("leader", leaderName);

        ItemStack infoItem = GuiItems.get("main-info",
                guildRes,
                leaderRes,
                Placeholder.unparsed("shortname", guild.getShortname()),
                Placeholder.unparsed("level", String.valueOf(guild.getLevel())),
                Placeholder.unparsed("xp", String.valueOf(guild.getXp())),
                Placeholder.unparsed("members", String.valueOf(guild.getMemberCount())),
                Placeholder.unparsed("founded", DATE_FMT.format(guild.getCreatedAt())));

        if (guild.getActiveBadgeId() != null) {
            appendBadgeLine(infoItem);
        }
        return infoItem;
    }

    private void appendBadgeLine(ItemStack infoItem) {
        GuiConfig gui = GuiItems.config();
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        String display = plugin != null
                ? plugin.getConfigManager().getBadgeDisplay(guild.getActiveBadgeId())
                        .orElse(guild.getActiveBadgeId())
                : guild.getActiveBadgeId();

        String template = gui != null
                ? gui.string("main.badge-line", " <gray>Badge: <badge> ")
                : " <gray>Badge: <badge> ";

        ItemMeta meta = infoItem.getItemMeta();
        if (meta == null) return;
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        Component badgeLine = Text.parse(template, Placeholder.component("badge", Text.parse(display)))
                .decoration(TextDecoration.ITALIC, false);
        if (!lore.isEmpty() && PlainTextComponentSerializer.plainText().serialize(lore.get(lore.size() - 1)).isEmpty()) {
            lore.add(lore.size() - 1, badgeLine);
        } else {
            lore.add(badgeLine);
            lore.add(Component.empty());
        }
        meta.lore(lore);
        infoItem.setItemMeta(meta);
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return true;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        int slot = event.getRawSlot();

        if (slot == infoSlot || slot == iconSlot) {
            // Info is lore-only; icon is display-only.
            return true;
        } else if (slot == membersSlot) {
            new GuildMembersGui(guild, viewer, guiManager, 0)
                    .returnTo(p -> new GuildMainGui(guild, viewer, guiManager).open(p))
                    .open(player);
        } else if (slot == rolesSlot) {
            new RoleManagementGui(guild, viewer, guiManager)
                    .returnTo(p -> new GuildMainGui(guild, viewer, guiManager).open(p))
                    .open(player);
        } else if (slot == leaderboardSlot) {
            if (plugin == null) return true;
            int pageSize = plugin.getConfigManager().getLeaderboardPageSize();
            plugin.getScheduler().runAsync(() ->
                    plugin.getLeaderboardService().getGlobalLeaderboard(0, pageSize).thenAccept(guilds ->
                            plugin.getScheduler().runForEntity(player, () -> {
                                LeaderboardGui gui = new LeaderboardGui(guiManager, plugin.getLeaderboardService(), 0);
                                gui.returnTo(p -> new GuildMainGui(guild, viewer, guiManager).open(p));
                                gui.setGuilds(guilds);
                                gui.open(player);
                            })
                    )
            );
        } else if (slot == storageSlot) {
            if (!GuiItems.isFeatureUnlocked(guild, GuildFeature.STORAGE)) {
                return true;
            }
            player.closeInventory();
            StorageSubcommand.openStorage(player, p -> new GuildMainGui(guild, viewer, guiManager).open(p));
        } else if (slot == bankSlot) {
            if (!GuiItems.isFeatureUnlocked(guild, GuildFeature.BANK)) {
                return true;
            }
            BankSubcommand.showBalance(player);
        } else if (slot == badgesSlot) {
            if (!GuiItems.isFeatureUnlocked(guild, GuildFeature.BADGE)) {
                return true;
            }
            new BadgesGui(guild, viewer, guiManager)
                    .returnTo(p -> new GuildMainGui(guild, viewer, guiManager).open(p))
                    .open(player);
        } else if (slot == permissionsSlot) {
            new PermissionsGui(guild, viewer, guiManager)
                    .returnTo(p -> new GuildMainGui(guild, viewer, guiManager).open(p))
                    .open(player);
        } else if (slot == settingsSlot) {
            new GuildSettingsGui(guild, viewer, guiManager)
                    .returnTo(p -> new GuildMainGui(guild, viewer, guiManager).open(p))
                    .open(player);
        }
        return true;
    }
}
