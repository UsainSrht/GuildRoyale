package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.adapter.ItemStackAdapter;
import me.usainsrht.guildroyale.core.command.subcommand.BankSubcommand;
import me.usainsrht.guildroyale.core.command.subcommand.StorageSubcommand;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.gui.AbstractGui;
import me.usainsrht.guildroyale.core.gui.GuiItems;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Hub GUI for players who are already in a guild.
 */
public final class GuildMainGui extends AbstractGui {

    private final Guild guild;
    private final GuildMember viewer;
    private final GuiManager guiManager;
    private final int infoSlot;
    private final int membersSlot;
    private final int rolesSlot;
    private final int leaderboardSlot;
    private final int storageSlot;
    private final int bankSlot;
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
        this.settingsSlot = gui != null ? gui.slot("main.slots.settings", 40) : 40;
    }

    private static int size() {
        GuiConfig gui = GuiItems.config();
        return gui != null ? gui.size("main.size", 54) : 54;
    }

    private static net.kyori.adventure.text.Component title(Guild guild) {
        GuiConfig gui = GuiItems.config();
        if (gui == null) {
            return net.kyori.adventure.text.Component.text("Guild Menu");
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

        setSlot(infoSlot, GuiItems.get("main-info"));
        setSlot(membersSlot, GuiItems.get("main-members"));
        setSlot(rolesSlot, GuiItems.get("main-roles"));
        setSlot(leaderboardSlot, GuiItems.get("main-leaderboard"));
        setSlot(storageSlot, GuiItems.get("main-storage"));
        setSlot(bankSlot, GuiItems.get("main-bank"));
        setSlot(settingsSlot, GuiItems.get("main-settings"));
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return true;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        int slot = event.getRawSlot();

        if (slot == infoSlot) {
            new GuildInfoGui(guild, guiManager)
                    .returnTo(p -> new GuildMainGui(guild, viewer, guiManager).open(p))
                    .open(player);
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
            player.closeInventory();
            StorageSubcommand.openStorage(player);
        } else if (slot == bankSlot) {
            player.closeInventory();
            BankSubcommand.showBalance(player);
        } else if (slot == settingsSlot) {
            new GuildSettingsGui(guild, viewer, guiManager)
                    .returnTo(p -> new GuildMainGui(guild, viewer, guiManager).open(p))
                    .open(player);
        }
        return true;
    }
}
