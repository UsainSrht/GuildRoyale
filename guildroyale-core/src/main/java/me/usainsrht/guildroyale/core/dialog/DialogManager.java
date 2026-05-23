package me.usainsrht.guildroyale.core.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Creates and opens Paper Dialog API dialogs for text input.
 *
 * <p>All dialogs use the {@link DialogAction#customClick} pattern so the callback
 * fires server-side when the player submits the form — no
 * {@code PlayerDialogResponseEvent} needed.
 *
 * <p>Dialogs are opened directly via {@code player.openDialog(dialog)}.
 */
@SuppressWarnings("UnstableApiUsage")
public final class DialogManager {

    private static final ClickCallback.Options SINGLE_USE = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(5))
            .build();

    // ── Guild creation ────────────────────────────────────────────────────────

    /**
     * Opens a two-field dialog for guild name and shortname.
     * {@code callback} receives {@code String[2]} = {name, shortname}.
     */
    public void openGuildCreateDialog(Player player, Consumer<String[]> callback) {
        DialogBase base = DialogBase.create(
                Component.text("Create a Guild"),
                null,
                true,
                false,
                DialogBase.DialogAfterAction.CLOSE,
                List.of(DialogBody.plainMessage(Component.text(
                        "Choose a name (3-32 chars) and a shortname (2-6 alphanumeric chars)."))),
                List.of(
                        DialogInput.text("guild_name", 200,
                                Component.text("Guild Name"), true, "", 32, null),
                        DialogInput.text("guild_shortname", 200,
                                Component.text("Shortname (2-6 chars)"), true, "", 6, null)
                )
        );

        DialogAction action = DialogAction.customClick(
                (DialogActionCallback) (response, audience) -> {
                    String name = response.getText("guild_name").trim();
                    String shortname = response.getText("guild_shortname").trim();
                    if (!name.isEmpty() && !shortname.isEmpty()) {
                        callback.accept(new String[]{name, shortname});
                    }
                },
                SINGLE_USE
        );

        openSingleActionDialog(player, base, Component.text("Create Guild"), action);
    }

    // ── Shortname change ──────────────────────────────────────────────────────

    public void openShortnameDialog(Player player, Consumer<String> callback) {
        DialogBase base = DialogBase.create(
                Component.text("Change Shortname"),
                null,
                true,
                false,
                DialogBase.DialogAfterAction.CLOSE,
                List.of(DialogBody.plainMessage(Component.text("Enter a new shortname (2-6 alphanumeric characters)."))),
                List.of(DialogInput.text("shortname", 200,
                        Component.text("New Shortname"), true, "", 6, null))
        );

        DialogAction action = DialogAction.customClick(
                (DialogActionCallback) (response, audience) -> {
                    String value = response.getText("shortname").trim();
                    if (!value.isEmpty()) callback.accept(value);
                },
                SINGLE_USE
        );

        openSingleActionDialog(player, base, Component.text("Change"), action);
    }

    // ── Role name ─────────────────────────────────────────────────────────────

    public void openRoleNameDialog(Player player, String prompt, Consumer<String> callback) {
        DialogBase base = DialogBase.create(
                Component.text("Role Name"),
                null,
                true,
                false,
                DialogBase.DialogAfterAction.CLOSE,
                List.of(DialogBody.plainMessage(Component.text(prompt))),
                List.of(DialogInput.text("role_name", 200,
                        Component.text("Role Name"), true, "", 20, null))
        );

        DialogAction action = DialogAction.customClick(
                (DialogActionCallback) (response, audience) -> {
                    String value = response.getText("role_name").trim();
                    if (!value.isEmpty()) callback.accept(value);
                },
                SINGLE_USE
        );

        openSingleActionDialog(player, base, Component.text("Confirm"), action);
    }

    // ── Generic ───────────────────────────────────────────────────────────────

    private static void openSingleActionDialog(Player player, DialogBase base,
                                                Component submitLabel, DialogAction action) {
        ActionButton submitBtn = ActionButton.create(submitLabel, null, 200, action);
        DialogType type = DialogType.multiAction(List.of(submitBtn), null, 1);
        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(type));
        player.showDialog(dialog);
    }
}
