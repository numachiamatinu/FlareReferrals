package dev.tmdevelopment.flarereferrals.gui;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.util.ItemBuilder;
import dev.tmdevelopment.flarereferrals.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Edits a list of lines (commands, messages, broadcast). type = "commands" | "messages" | "broadcast".
 * Left-click edit, right-click remove, shift-click move up.
 */
public final class StringListMenu extends Menu {

    private static final int MAX = 36;
    private static final int RAW_WIDTH = 38;

    private final List<String> lines;
    private final String type;
    private final Runnable onChange;
    private final Menu parent;

    public StringListMenu(FlareReferrals plugin, Player viewer, List<String> lines, String type,
                          Runnable onChange, Menu parent) {
        super(plugin, viewer);
        this.lines = lines;
        this.type = type;
        this.onChange = onChange;
        this.parent = parent;
    }

    @Override
    protected Component title() {
        return lang().title("gui.lines." + type + ".title", viewer, "%amount%", String.valueOf(lines.size()));
    }

    @Override protected int rows() { return 6; }

    private boolean isCommand() {
        return type.equals("commands");
    }

    @Override
    protected void build() {
        Material mat = isCommand() ? Material.COMMAND_BLOCK : (type.equals("broadcast") ? Material.BELL : Material.PAPER);

        for (int i = 0; i < lines.size() && i < MAX; i++) {
            final int index = i;
            String line = lines.get(i);

            // name = what the player will see (messages) or the command itself
            Component name = isCommand()
                    ? Text.raw("/" + line, 200).get(0)
                    : Text.item(Text.replace(line.replace("%prefix%", lang().prefix()).replace("{prefix}", lang().prefix()),
                    "%player%", viewer.getName(), "%invites%", "3", "%milestone%", "3",
                    "%inviter%", viewer.getName(), "%invited%", viewer.getName()), viewer);

            List<Component> lore = new ArrayList<>(lang().lore("gui.lines.entry.header", viewer,
                    "%line%", String.valueOf(i + 1)));
            lore.addAll(Text.raw(line, RAW_WIDTH));
            lore.addAll(lang().lore("gui.lines.entry.actions", viewer));

            set(9 + i, new ItemBuilder(mat).name(name).lore(lore).amount(i + 1).hideFlags().build(), e -> {
                if (e.isShiftClick()) {
                    if (index > 0) {
                        lines.set(index, lines.get(index - 1));
                        lines.set(index - 1, line);
                        onChange.run();
                        refresh();
                    }
                } else if (e.isRightClick()) {
                    lines.remove(index);
                    onChange.run();
                    refresh();
                } else {
                    plugin.chatInput().ask(viewer, "input.line-edit", line, text -> {
                        if (index < lines.size()) lines.set(index, text);
                        onChange.run();
                        open();
                    }, this::open, "%line%", String.valueOf(index + 1));
                }
            });
        }

        if (lines.isEmpty()) {
            set(22, button(Material.STRUCTURE_VOID, "gui.lines.empty"));
        }

        set(0, button(Material.BOOK, "gui.lines." + type + ".help"));
        set(4, button(Material.LIME_DYE, "gui.lines.add"), e -> {
            if (lines.size() >= MAX) {
                lang().send(viewer, "admin.list-full", "%max%", String.valueOf(MAX));
                return;
            }
            plugin.chatInput().ask(viewer, "input.line-add", isCommand() ? "" : "{prefix}", text -> {
                lines.add(text.startsWith("/") && isCommand() ? text.substring(1) : text);
                onChange.run();
                open();
            }, this::open);
        });
        if (!lines.isEmpty()) {
            set(8, button(Material.LAVA_BUCKET, "gui.lines.clear"), e -> {
                if (!e.isShiftClick()) return;
                lines.clear();
                onChange.run();
                refresh();
            });
        }
        backButton(49, parent);
        fillRow(0);
        fillRow(5);
    }
}
