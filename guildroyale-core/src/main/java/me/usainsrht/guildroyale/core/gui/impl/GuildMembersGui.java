package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.gui.AbstractGui;
import me.usainsrht.guildroyale.core.gui.GuiItems;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

/**
 * Paginated member list. Each member head is clickable to open {@link MemberActionGui}.
 */
public final class GuildMembersGui extends AbstractGui {

    private final Guild guild;
    private final GuildMember viewer;
    private final GuiManager guiManager;
    private final int page;
    private final int pageSize;
    private final int previousSlot;
    private final int nextSlot;
    private final int closeSlot;
    private final List<GuildMember> members;

    public GuildMembersGui(Guild guild, GuildMember viewer, GuiManager guiManager, int page) {
        super(size(), title(guild, page));
        this.guild = guild;
        this.viewer = viewer;
        this.guiManager = guiManager;
        this.members = guild.getMembers().stream()
                .sorted((a, b) -> Integer.compare(a.getRole().getIndex(), b.getRole().getIndex()))
                .toList();

        GuiConfig gui = GuiItems.config();
        this.pageSize = gui != null ? gui.pageSize("members.page-size", 36) : 36;
        this.previousSlot = gui != null ? gui.slot("members.slots.previous", 45) : 45;
        this.nextSlot = gui != null ? gui.slot("members.slots.next", 53) : 53;
        this.closeSlot = gui != null ? gui.slot("members.slots.close", 49) : 49;
        int maxPage = Math.max(1, (int) Math.ceil(members.size() / (double) pageSize));
        this.page = Math.max(0, Math.min(page, maxPage - 1));
    }

    private static int size() {
        GuiConfig gui = GuiItems.config();
        return gui != null ? gui.size("members.size", 54) : 54;
    }

    private static Component title(Guild guild, int page) {
        GuiConfig gui = GuiItems.config();
        int pageSize = gui != null ? gui.pageSize("members.page-size", 36) : 36;
        int memberCount = guild.getMemberCount();
        int maxPage = Math.max(1, (int) Math.ceil(memberCount / (double) pageSize));
        int safePage = Math.max(0, Math.min(page, maxPage - 1)) + 1;
        if (gui == null) {
            return Component.text("Members");
        }
        return gui.title("members.title",
                Placeholder.unparsed("guild", guild.getName()),
                Placeholder.unparsed("page", String.valueOf(safePage)),
                Placeholder.unparsed("max_page", String.valueOf(maxPage)));
    }

    @Override
    protected void build() {
        int start = page * pageSize;
        for (int i = 0; i < pageSize && (start + i) < members.size(); i++) {
            GuildMember member = members.get(start + i);
            var offlinePlayer = Bukkit.getOfflinePlayer(member.getPlayerId());
            String name = offlinePlayer.getName() != null
                    ? offlinePlayer.getName()
                    : member.getPlayerId().toString().substring(0, 8);

            ItemStack head = GuiItems.get("members-entry",
                    Placeholder.unparsed("player", name),
                    Placeholder.unparsed("role", member.getRole().getName()),
                    Placeholder.unparsed("contribution", String.valueOf(member.getContribution())));
            if (head.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(offlinePlayer);
                head.setItemMeta(meta);
            }
            setSlot(i, head);
        }

        if (page > 0) {
            setSlot(previousSlot, GuiItems.get("gui-previous"));
        }
        if ((page + 1) * pageSize < members.size()) {
            setSlot(nextSlot, GuiItems.get("gui-next"));
        }
        setSlot(closeSlot, GuiItems.get("gui-close"));
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (!(event.getWhoClicked() instanceof Player player)) return true;

        if (slot == previousSlot && page > 0) {
            new GuildMembersGui(guild, viewer, guiManager, page - 1).open(player);
        } else if (slot == nextSlot && (page + 1) * pageSize < members.size()) {
            new GuildMembersGui(guild, viewer, guiManager, page + 1).open(player);
        } else if (slot == closeSlot) {
            player.closeInventory();
        } else if (slot < pageSize) {
            int idx = page * pageSize + slot;
            if (idx < members.size()) {
                new MemberActionGui(guild, members.get(idx), viewer, guiManager).open(player);
            }
        }
        return true;
    }
}
