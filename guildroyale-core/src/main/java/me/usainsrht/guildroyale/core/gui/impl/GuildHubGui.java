package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.command.subcommand.CreateSubcommand;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.gui.AbstractGui;
import me.usainsrht.guildroyale.core.gui.CreateEligibility;
import me.usainsrht.guildroyale.core.gui.GuiItems;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Landing GUI for players who are not in a guild — opened by bare {@code /guild}.
 * Shows a how-to book and a create button that reflects eligibility.
 */
public final class GuildHubGui extends AbstractGui {

    private final GuiManager guiManager;
    private final Player viewer;
    private final CreateEligibility.Status status;
    private final int infoSlot;
    private final int createSlot;
    private final int leaderboardSlot;

    public GuildHubGui(Player viewer, boolean alreadyInGuild, GuiManager guiManager) {
        super(size(viewer), title(viewer));
        this.viewer = viewer;
        this.guiManager = guiManager;
        this.status = CreateEligibility.evaluate(viewer, alreadyInGuild);

        GuiConfig gui = GuiItems.config();
        this.infoSlot = gui != null ? gui.slot("hub.slots.info", 11) : 11;
        this.createSlot = gui != null ? gui.slot("hub.slots.create", 15) : 15;
        this.leaderboardSlot = gui != null ? gui.slot("hub.slots.leaderboard", 13) : 13;
    }

    private static int size(Player viewer) {
        GuiConfig gui = GuiItems.config();
        return gui != null ? gui.size("hub.size", 27) : 27;
    }

    private static net.kyori.adventure.text.Component title(Player viewer) {
        GuiConfig gui = GuiItems.config();
        if (gui == null) {
            return net.kyori.adventure.text.Component.text("GuildRoyale");
        }
        return gui.title("hub.title");
    }

    @Override
    protected void build() {
        fillBorder(GuiItems.filler());

        ItemStack book = GuiItems.withExtraLore("hub-info-book", CreateEligibility.requirementLore(viewer));
        setSlot(infoSlot, book);

        ItemStack create = GuiItems.withExtraLore(status.itemKey(),
                status == CreateEligibility.Status.ALREADY_IN_GUILD
                        ? null
                        : CreateEligibility.requirementLore(viewer));
        setSlot(createSlot, create);

        setSlot(leaderboardSlot, GuiItems.get("hub-leaderboard"));
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return true;
        }
        int slot = event.getRawSlot();
        if (slot == createSlot) {
            if (!status.canCreate()) {
                GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
                if (plugin != null) {
                    String key = switch (status) {
                        case ALREADY_IN_GUILD -> "already-in-guild";
                        case NO_PERMISSION -> "guild-creation-no-permission";
                        case INSUFFICIENT_FUNDS -> "guild-creation-insufficient-funds";
                        case MISSING_ITEMS -> "guild-creation-missing-items";
                        default -> "unknown-error";
                    };
                    plugin.getMessages().send(player, key, CreateEligibility.costResolver(player));
                }
                return true;
            }
            player.closeInventory();
            CreateSubcommand.openCreateFlow(player);
            return true;
        }
        if (slot == leaderboardSlot) {
            GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
            if (plugin == null) return true;
            int pageSize = plugin.getConfigManager().getLeaderboardPageSize();
            plugin.getScheduler().runAsync(() ->
                    plugin.getLeaderboardService().getGlobalLeaderboard(0, pageSize).thenAccept(guilds ->
                            plugin.getScheduler().runForEntity(player, () -> {
                                LeaderboardGui gui = new LeaderboardGui(guiManager, plugin.getLeaderboardService(), 0);
                                gui.setGuilds(guilds);
                                gui.open(player);
                            })
                    )
            );
            return true;
        }
        return true;
    }
}
