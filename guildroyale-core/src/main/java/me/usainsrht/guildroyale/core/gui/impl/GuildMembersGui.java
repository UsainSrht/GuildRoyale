package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.gui.GuiItems;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import me.usainsrht.guildroyale.core.gui.StandardListGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

/**
 * Paginated member list using {@link StandardListGui}.
 * Clicking a member head opens {@link MemberActionGui}.
 */
public final class GuildMembersGui extends StandardListGui<GuildMember> {

    private final Guild guild;
    private final GuildMember viewer;
    private final GuiManager guiManager;

    public GuildMembersGui(Guild guild, GuildMember viewer, GuiManager guiManager, int page) {
        super(size(), title(guild, page), getSortedMembers(guild), page, "members");
        this.guild = guild;
        this.viewer = viewer;
        this.guiManager = guiManager;
    }

    private static List<GuildMember> getSortedMembers(Guild guild) {
        return guild.getMembers().stream()
                .sorted((a, b) -> Integer.compare(a.getRole().getIndex(), b.getRole().getIndex()))
                .toList();
    }

    private static int size() {
        GuiConfig gui = GuiItems.config();
        return gui != null ? gui.size("members.size", 54) : 54;
    }

    private static Component title(Guild guild, int page) {
        GuiConfig gui = GuiItems.config();
        int innerCount = StandardListGui.calculateInnerSlots(size() / 9).size();
        int maxPage = Math.max(1, (int) Math.ceil(guild.getMemberCount() / (double) innerCount));
        int safePage = Math.max(0, Math.min(page, maxPage - 1)) + 1;
        if (gui == null) {
            return formatStandardTitle("Members");
        }
        return gui.listTitle("members", safePage, maxPage,
                Placeholder.unparsed("guild", guild.getName()),
                Placeholder.unparsed("page", String.valueOf(safePage)),
                Placeholder.unparsed("max_page", String.valueOf(maxPage)));
    }

    @Override
    protected ItemStack renderItem(GuildMember member, int index) {
        var offlinePlayer = Bukkit.getOfflinePlayer(member.getPlayerId());
        String name = offlinePlayer.getName() != null
                ? offlinePlayer.getName()
                : member.getPlayerId().toString().substring(0, 8);

        var plugin = me.usainsrht.guildroyale.core.GuildRoyalePlugin.getInstance();
        var resolvers = plugin != null
                ? plugin.getTagService().memberResolver(member, guild, null)
                : Placeholder.unparsed("player", name);

        ItemStack head = GuiItems.get("members-entry",
                resolvers,
                Placeholder.unparsed("contribution", String.valueOf(member.getContribution())));
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(offlinePlayer);
            head.setItemMeta(meta);
        }
        return head;
    }

    @Override
    protected void onItemClick(InventoryClickEvent event, GuildMember member, int index) {
        if (event.getWhoClicked() instanceof Player player) {
            new MemberActionGui(guild, member, viewer, guiManager)
                    .returnTo(p -> new GuildMembersGui(guild, viewer, guiManager, page)
                            .returnTo(this)
                            .open(p))
                    .open(player);
        }
    }

    @Override
    protected void onPreviousPage(Player player) {
        new GuildMembersGui(guild, viewer, guiManager, page - 1).returnTo(this).open(player);
    }

    @Override
    protected void onNextPage(Player player) {
        new GuildMembersGui(guild, viewer, guiManager, page + 1).returnTo(this).open(player);
    }
}
