package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.core.gui.AbstractGui;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

/**
 * Actions available for a specific guild member: view profile, kick, promote, demote.
 * Actions are rendered only if the viewer has the required permissions.
 */
public final class MemberActionGui extends AbstractGui {

    private final Guild guild;
    private final GuildMember target;
    private final GuildMember viewer;
    private final GuiManager guiManager;

    public MemberActionGui(Guild guild, GuildMember target, GuildMember viewer, GuiManager guiManager) {
        super(27, "Member: " + Bukkit.getOfflinePlayer(target.getPlayerId()).getName());
        this.guild = guild;
        this.target = target;
        this.viewer = viewer;
        this.guiManager = guiManager;
    }

    @Override
    protected void build() {
        // Target head at centre top
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(target.getPlayerId()));
        String tName = Bukkit.getOfflinePlayer(target.getPlayerId()).getName();
        skullMeta.displayName(Component.text(tName != null ? tName : "Unknown", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        skullMeta.lore(List.of(
                Component.text("§7Role: §f" + target.getRole().getName()).decoration(TextDecoration.ITALIC, false),
                Component.text("§7Contribution: §f" + target.getContribution() + " XP").decoration(TextDecoration.ITALIC, false)
        ));
        head.setItemMeta(skullMeta);
        setSlot(4, head);

        boolean canActOn = viewer.getRole().getIndex() < target.getRole().getIndex();

        if (canActOn) {
            setSlot(11, makeButton(Material.BARRIER, "Kick", NamedTextColor.RED,
                    List.of("§7Remove this member from the guild")));
            setSlot(13, makeButton(Material.ARROW, "Promote", NamedTextColor.GREEN,
                    List.of("§7Move to a higher role")));
            setSlot(15, makeButton(Material.ARROW, "Demote", NamedTextColor.YELLOW,
                    List.of("§7Move to a lower role")));
        }

        setSlot(22, makeButton(Material.ARROW, "Back", NamedTextColor.GRAY, List.of()));
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        // Click handling is delegated to commands/services — GUI just closes on action
        // Full implementation would call MemberService async
        if (!(event.getWhoClicked() instanceof Player player)) return true;
        if (event.getRawSlot() == 22) {
            new GuildMembersGui(guild, viewer, guiManager, 0).open(player);
        }
        return true;
    }

    private static ItemStack makeButton(Material mat, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(l -> Component.text(l).decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }
}
