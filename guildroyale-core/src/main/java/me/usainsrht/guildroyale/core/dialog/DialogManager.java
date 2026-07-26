package me.usainsrht.guildroyale.core.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * Creates and opens Paper Dialog API dialogs for text input.
 *
 * <p>All dialogs use the {@link DialogAction#customClick} pattern so the callback
 * fires server-side when the player submits the form — no
 * {@code PlayerDialogResponseEvent} needed.
 *
 * <p>Dialogs are opened directly via {@link Player#showDialog(io.papermc.paper.dialog.Dialog)}.
 */
@SuppressWarnings("UnstableApiUsage")
public final class DialogManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final ClickCallback.Options SINGLE_USE = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(5))
            .build();

    /** Reads a text input; returns empty string when the key is missing or null. */
    private static String textOrEmpty(DialogResponseView response, String key) {
        String value = response.getText(key);
        return value == null ? "" : value.trim();
    }

    /**
     * Opens a two-field dialog for guild name and shortname.
     * {@code callback} receives {@code String[2]} = {name, shortname}.
     */
    public void openGuildCreateDialog(Player player, Consumer<String[]> callback) {
        DialogBase base = DialogBase.create(
                text("dialogs.create.title", "<gold><bold>Create a Guild"),
                null,
                true,
                false,
                DialogBase.DialogAfterAction.CLOSE,
                List.of(DialogBody.plainMessage(text(
                        "dialogs.create.body",
                        "<gray>Choose a <white>name</white> (3–32 chars) and a <white>shortname</white> (2–6 alphanumeric)."))),
                List.of(
                        DialogInput.text("guild_name", 200,
                                text("dialogs.create.name-label", "<yellow>Guild Name"), true, "", 32, null),
                        DialogInput.text("guild_shortname", 200,
                                text("dialogs.create.shortname-label", "<yellow>Shortname"), true, "", 6, null)
                )
        );

        DialogAction action = DialogAction.customClick(
                (DialogActionCallback) (response, audience) -> {
                    String name = textOrEmpty(response, "guild_name");
                    String shortname = textOrEmpty(response, "guild_shortname");
                    if (!name.isEmpty() && !shortname.isEmpty()) {
                        callback.accept(new String[]{name, shortname});
                    }
                },
                SINGLE_USE
        );

        openSingleActionDialog(player, base,
                text("dialogs.create.submit", "<green>Create Guild"), action);
    }

    public void openShortnameDialog(Player player, Consumer<String> callback) {
        DialogBase base = DialogBase.create(
                text("dialogs.shortname.title", "<gold>Change Shortname"),
                null,
                true,
                false,
                DialogBase.DialogAfterAction.CLOSE,
                List.of(DialogBody.plainMessage(text(
                        "dialogs.shortname.body",
                        "<gray>Enter a new shortname <dark_gray>(2–6 alphanumeric)."))),
                List.of(DialogInput.text("shortname", 200,
                        text("dialogs.shortname.input-label", "<yellow>New Shortname"), true, "", 6, null))
        );

        DialogAction action = DialogAction.customClick(
                (DialogActionCallback) (response, audience) -> {
                    String value = textOrEmpty(response, "shortname");
                    if (!value.isEmpty()) callback.accept(value);
                },
                SINGLE_USE
        );

        openSingleActionDialog(player, base,
                text("dialogs.shortname.submit", "<green>Change"), action);
    }

    public void openRoleNameDialog(Player player, String prompt, Consumer<String> callback) {
        DialogBase base = DialogBase.create(
                text("dialogs.role-name.title", "<gold>Role Name"),
                null,
                true,
                false,
                DialogBase.DialogAfterAction.CLOSE,
                List.of(DialogBody.plainMessage(text(
                        "dialogs.role-name.body",
                        "<gray><prompt>",
                        Placeholder.unparsed("prompt", prompt)))),
                List.of(DialogInput.text("role_name", 200,
                        text("dialogs.role-name.input-label", "<yellow>Role Name"), true, "", 20, null))
        );

        DialogAction action = DialogAction.customClick(
                (DialogActionCallback) (response, audience) -> {
                    String value = textOrEmpty(response, "role_name");
                    if (!value.isEmpty()) callback.accept(value);
                },
                SINGLE_USE
        );

        openSingleActionDialog(player, base,
                text("dialogs.role-name.submit", "<green>Confirm"), action);
    }

    private static void openSingleActionDialog(Player player, DialogBase base,
                                                Component submitLabel, DialogAction action) {
        ActionButton submitBtn = ActionButton.create(submitLabel, null, 200, action);
        DialogType type = DialogType.multiAction(List.of(submitBtn), null, 1);
        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(type));
        player.showDialog(dialog);
    }

    private static Component text(String path, String def, TagResolver... resolvers) {
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        GuiConfig gui = plugin != null ? plugin.getGuiConfig() : null;
        if (gui == null) {
            return MM.deserialize(def, resolvers);
        }
        return gui.component(path, def, resolvers);
    }
}
