package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.SerializableItemStack;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.adapter.ItemStackAdapter;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.feature.GuildFeature;
import me.usainsrht.guildroyale.core.gui.AbstractGui;
import me.usainsrht.guildroyale.core.gui.GuiItems;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import me.usainsrht.guildroyale.core.service.GuildServiceImpl;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Guild settings: shortname, icon, and disband.
 */
public final class GuildSettingsGui extends AbstractGui {

    private final Guild guild;
    private final GuildMember viewer;
    private final GuiManager guiManager;
    private final int shortnameSlot;
    private final int iconSlot;
    private final int disbandSlot;
    private final int backSlot;

    public GuildSettingsGui(Guild guild, GuildMember viewer, GuiManager guiManager) {
        super(size(), title(guild));
        this.guild = guild;
        this.viewer = viewer;
        this.guiManager = guiManager;

        GuiConfig gui = GuiItems.config();
        this.shortnameSlot = gui != null ? gui.slot("settings.slots.shortname", 20) : 20;
        this.iconSlot = gui != null ? gui.slot("settings.slots.icon", 22) : 22;
        this.disbandSlot = gui != null ? gui.slot("settings.slots.disband", 24) : 24;
        this.backSlot = gui != null ? gui.slot("settings.slots.back", 49) : 49;
    }

    private static int size() {
        GuiConfig gui = GuiItems.config();
        return gui != null ? gui.size("settings.size", 54) : 54;
    }

    private static net.kyori.adventure.text.Component title(Guild guild) {
        GuiConfig gui = GuiItems.config();
        if (gui == null) {
            return net.kyori.adventure.text.Component.text("Guild Settings");
        }
        return gui.title("settings.title", Placeholder.unparsed("guild", guild.getName()));
    }

    @Override
    protected void build() {
        fillBorder(GuiItems.filler());

        setSlot(shortnameSlot, GuiItems.featureItem(guild, GuildFeature.SHORTNAME,
                "settings-shortname", "settings-shortname-locked"));

        ItemStack iconItem = GuiItems.get("settings-icon");
        ItemStack customIcon = ItemStackAdapter.fromSerializable(guild.getIcon());
        if (!customIcon.getType().isAir()) {
            var meta = iconItem.getItemMeta();
            if (meta != null) {
                customIcon.setItemMeta(meta);
                iconItem = customIcon;
            }
        }
        setSlot(iconSlot, iconItem);

        setSlot(disbandSlot, GuiItems.get("settings-disband"));
        setSlot(backSlot, navBackItem());
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return true;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        int slot = event.getRawSlot();

        if (slot == backSlot) {
            navigateBack(player);
        } else if (slot == shortnameSlot) {
            openShortname(player, plugin);
        } else if (slot == iconSlot) {
            changeIcon(player, plugin, event.getCursor());
        } else if (slot == disbandSlot) {
            if (plugin != null) {
                plugin.getMessages().send(player, "gui-disband-hint");
            }
        }
        return true;
    }

    private void openShortname(Player player, GuildRoyalePlugin plugin) {
        if (plugin == null) return;
        if (!GuiItems.isFeatureUnlocked(guild, GuildFeature.SHORTNAME)) {
            return;
        }
        GuildServiceImpl service = (GuildServiceImpl) plugin.getGuildService();
        GuildFeature feature = GuildFeature.SHORTNAME;
        player.closeInventory();
        plugin.getDialogManager().openShortnameDialog(player, shortname ->
                plugin.getScheduler().runAsync(() ->
                        plugin.getGuildService().setShortname(guild.getId(), player.getUniqueId(), shortname)
                                .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                    switch (result) {
                                        case ActionResult.Success s ->
                                                plugin.getMessages().send(player, "shortname-changed",
                                                        Placeholder.unparsed("guild", shortname));
                                        case ActionResult.Failure f -> {
                                            if ("feature-locked".equals(f.reason())) {
                                                plugin.getMessages().send(player, "feature-locked",
                                                        Placeholder.unparsed("feature", "shortname"),
                                                        Placeholder.unparsed("level", String.valueOf(
                                                                service.featureGate().unlockLevel(feature))));
                                            } else {
                                                plugin.getMessages().send(player, f.reason());
                                            }
                                        }
                                    }
                                }))
                )
        );
    }

    private void changeIcon(Player player, GuildRoyalePlugin plugin, ItemStack cursorItem) {
        if (plugin == null) return;
        if (cursorItem == null || cursorItem.getType().isAir()) {
            plugin.getMessages().send(player, "icon-invalid");
            return;
        }
        SerializableItemStack icon = ItemStackAdapter.toSerializableItemType(cursorItem);
        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().setIcon(guild.getId(), player.getUniqueId(), icon)
                        .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                            switch (result) {
                                case ActionResult.Success s -> {
                                    plugin.getMessages().send(player, "icon-updated");
                                    reopen(player, plugin);
                                }
                                case ActionResult.Failure f ->
                                        plugin.getMessages().send(player, f.reason());
                            }
                        }))
        );
    }

    private void reopen(Player player, GuildRoyalePlugin plugin) {
        plugin.getGuildService().getGuild(guild.getId()).thenAccept(opt ->
                plugin.getScheduler().runForEntity(player, () -> {
                    if (opt.isPresent()) {
                        new GuildSettingsGui(opt.get(), viewer, guiManager)
                                .returnTo(this)
                                .open(player);
                    }
                })
        );
    }
}
