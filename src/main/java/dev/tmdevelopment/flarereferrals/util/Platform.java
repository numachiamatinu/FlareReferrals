package dev.tmdevelopment.flarereferrals.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything that goes out to the server goes through here, using only the Spigot API:
 * works the same on Spigot, Paper, Purpur... from 1.16.5 to the latest version.
 * Texts are built with MiniMessage (bundled), then sent as Bungee chat components (chat, action bar)
 * or as legacy text with hex colors (item names, lore, titles, boss bars, menu titles).
 */
public final class Platform {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    /** Console: no hex (old consoles print the codes), hex colors become the nearest normal color. */
    private static final LegacyComponentSerializer CONSOLE = LegacyComponentSerializer.builder().character('§').build();

    private Platform() {
    }

    // ------------------------------------------------------------------ text

    /** Legacy text with § codes and hex colors (§x§r§r§g§g§b§b). */
    public static String legacy(Component c) {
        if (c == null) return "";
        return LEGACY.serialize(c);
    }

    /** Item name / lore line: §r first, so the game doesn't make it italic. */
    public static String item(Component c) {
        return "§r" + legacy(c);
    }

    public static List<String> items(List<Component> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (Component c : lines) out.add(item(c));
        return out;
    }

    /** Legacy § text (from an item) -> editable text with &amp; codes and &amp;#hex. */
    public static String toEditable(String legacy) {
        if (legacy == null) return "";
        String s = legacy;
        if (s.startsWith("§r")) s = s.substring(2);
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 13 < s.length() && (s.charAt(i + 1) == 'x' || s.charAt(i + 1) == 'X')) {
                StringBuilder hex = new StringBuilder();
                boolean ok = true;
                for (int k = 0; k < 6; k++) {
                    int pos = i + 2 + k * 2;
                    if (s.charAt(pos) != '§') { ok = false; break; }
                    hex.append(s.charAt(pos + 1));
                }
                if (ok) {
                    out.append("&#").append(hex);
                    i += 13;
                    continue;
                }
            }
            out.append(c == '§' ? '&' : c);
        }
        return out.toString();
    }

    public static String plain(Component c) {
        return c == null ? "" : PlainTextComponentSerializer.plainText().serialize(c);
    }

    // ------------------------------------------------------------------ sending

    public static void send(CommandSender to, Component message) {
        if (to == null || message == null) return;
        if (to instanceof Player) {
            ((Player) to).spigot().sendMessage(bungee(message));
        } else {
            to.sendMessage(CONSOLE.serialize(message));
        }
    }

    public static void broadcast(Component message) {
        for (Player p : Bukkit.getOnlinePlayers()) send(p, message);
        send(Bukkit.getConsoleSender(), message);
    }

    public static void actionBar(Player p, Component message) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, bungee(message));
    }

    public static void title(Player p, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        p.sendTitle(legacy(title), legacy(subtitle), fadeIn, stay, fadeOut);
    }

    // ------------------------------------------------------------------ Adventure -> Bungee

    public static BaseComponent[] bungee(Component c) {
        return new BaseComponent[]{convert(c)};
    }

    private static BaseComponent convert(Component c) {
        String text;
        if (c instanceof TextComponent) {
            text = ((TextComponent) c).content();
        } else {
            // other kinds (translatable, keybind...) are shown as plain text
            text = plain(c.children(new ArrayList<Component>()));
        }
        net.md_5.bungee.api.chat.TextComponent out = new net.md_5.bungee.api.chat.TextComponent(text);
        Style st = c.style();
        TextColor color = st.color();
        if (color != null) {
            NamedTextColor named = NamedTextColor.namedColor(color.value());
            ChatColor cc = ChatColor.of(named != null ? named.toString() : color.asHexString());
            out.setColor(cc);
        }
        out.setBold(state(st.decoration(TextDecoration.BOLD)));
        out.setItalic(state(st.decoration(TextDecoration.ITALIC)));
        out.setUnderlined(state(st.decoration(TextDecoration.UNDERLINED)));
        out.setStrikethrough(state(st.decoration(TextDecoration.STRIKETHROUGH)));
        out.setObfuscated(state(st.decoration(TextDecoration.OBFUSCATED)));
        ClickEvent click = st.clickEvent();
        if (click != null) {
            net.md_5.bungee.api.chat.ClickEvent.Action a = clickAction(click.action());
            if (a != null) out.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(a, click.value()));
        }
        HoverEvent<?> hover = st.hoverEvent();
        if (hover != null && hover.action() == HoverEvent.Action.SHOW_TEXT && hover.value() instanceof Component) {
            out.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.hover.content.Text(bungee((Component) hover.value()))));
        }
        if (c instanceof TextComponent) {
            for (Component child : c.children()) out.addExtra(convert(child));
        }
        return out;
    }

    private static Boolean state(TextDecoration.State s) {
        if (s == TextDecoration.State.TRUE) return Boolean.TRUE;
        if (s == TextDecoration.State.FALSE) return Boolean.FALSE;
        return null;
    }

    private static net.md_5.bungee.api.chat.ClickEvent.Action clickAction(ClickEvent.Action a) {
        if (a == ClickEvent.Action.RUN_COMMAND) return net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND;
        if (a == ClickEvent.Action.SUGGEST_COMMAND) return net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND;
        if (a == ClickEvent.Action.OPEN_URL) return net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL;
        if (a == ClickEvent.Action.COPY_TO_CLIPBOARD) return net.md_5.bungee.api.chat.ClickEvent.Action.COPY_TO_CLIPBOARD;
        if (a == ClickEvent.Action.CHANGE_PAGE) return net.md_5.bungee.api.chat.ClickEvent.Action.CHANGE_PAGE;
        return null;
    }
}
