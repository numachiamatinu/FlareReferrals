package dev.tmdevelopment.flarereferrals.util;

import dev.tmdevelopment.flarereferrals.hook.PapiHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts legacy (&a), hex (&#RRGGBB) and MiniMessage text into Adventure components.
 */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern HEX = Pattern.compile("[&§]#([A-Fa-f0-9]{6})");
    private static final Map<Character, String> LEGACY = new HashMap<>();
    private static boolean papi = false;

    static {
        LEGACY.put('0', "<black>");
        LEGACY.put('1', "<dark_blue>");
        LEGACY.put('2', "<dark_green>");
        LEGACY.put('3', "<dark_aqua>");
        LEGACY.put('4', "<dark_red>");
        LEGACY.put('5', "<dark_purple>");
        LEGACY.put('6', "<gold>");
        LEGACY.put('7', "<gray>");
        LEGACY.put('8', "<dark_gray>");
        LEGACY.put('9', "<blue>");
        LEGACY.put('a', "<green>");
        LEGACY.put('b', "<aqua>");
        LEGACY.put('c', "<red>");
        LEGACY.put('d', "<light_purple>");
        LEGACY.put('e', "<yellow>");
        LEGACY.put('f', "<white>");
        LEGACY.put('k', "<obfuscated>");
        LEGACY.put('l', "<bold>");
        LEGACY.put('m', "<strikethrough>");
        LEGACY.put('n', "<underlined>");
        LEGACY.put('o', "<italic>");
        LEGACY.put('r', "<reset>");
    }

    private Text() {}

    public static void setPapi(boolean enabled) {
        papi = enabled;
    }

    public static String legacyToMini(String input) {
        if (input == null || input.isEmpty()) return "";
        Matcher m = HEX.matcher(input);
        String s = m.replaceAll("<#$1>");
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < s.length()) {
                String tag = LEGACY.get(Character.toLowerCase(s.charAt(i + 1)));
                if (tag != null) {
                    out.append(tag);
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    public static Component parse(String input, Player player) {
        if (input == null) return Component.empty();
        String s = input;
        if (papi && player != null && s.indexOf('%') >= 0) {
            s = PapiHook.apply(player, s);
        }
        if (s.indexOf('1') >= 0) s = singular(s);
        try {
            return MM.deserialize(legacyToMini(s));
        } catch (Exception ex) {
            return Component.text(s);
        }
    }

    // "1 invites" -> "1 invite", also days / hours / minutes / seconds and "1 more minutes"
    // (colors between the number and the word are allowed: "&e1 &7invites"; a hex color ending in 1 is not a number)
    private static final String COL = "(?:&[0-9a-fk-orA-FK-OR]|&#[0-9a-fA-F]{6}|<[^<>]{1,40}>)";
    private static final java.util.regex.Pattern ONE_INVITES = java.util.regex.Pattern.compile(
            "(?<![0-9.,])(?<!&#[0-9a-fA-F]{5})1((?:\\s|" + COL + ")*\\s" + COL + "*(?:more\\s+" + COL + "*)?)"
                    + "([Ii]nvite|[Dd]ay|[Hh]our|[Mm]inute|[Ss]econd)s\\b");

    static String singular(String s) {
        return ONE_INVITES.matcher(s).replaceAll("1$1$2");
    }

    /** Same as parse but without the default italic of item names / lore. */
    public static Component item(String input, Player player) {
        return parse(input, player).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> items(List<String> lines, Player player) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) out.add(item(line, player));
        return out;
    }

    /**
     * Replaces pairs: replace(s, "%a%", "1", "%b%", "2").
     * Every "%name%" key also replaces "{name}" (the recommended style, it never clashes with PlaceholderAPI).
     */
    public static String replace(String s, String... pairs) {
        if (s == null) return "";
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String k = pairs[i];
            String v = pairs[i + 1] == null ? "" : pairs[i + 1];
            s = s.replace(k, v);
            if (k.length() > 2 && k.charAt(0) == '%' && k.charAt(k.length() - 1) == '%') {
                s = s.replace("{" + k.substring(1, k.length() - 1) + "}", v);
            }
        }
        return s;
    }

    public static List<String> replace(List<String> lines, String... pairs) {
        List<String> out = new ArrayList<>(lines.size());
        for (String l : lines) out.add(replace(l, pairs));
        return out;
    }

    /** Legacy § text (item names...) -> component. */
    public static Component fromLegacy(String legacy) {
        if (legacy == null) return Component.empty();
        String s = legacy.startsWith("§r") ? legacy.substring(2) : legacy;
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.builder()
                .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build().deserialize(s);
    }

    public static String formatAmount(double amount) {
        if (amount == Math.rint(amount) && Math.abs(amount) < 1e15) {
            return String.format(java.util.Locale.US, "%,d", (long) amount);
        }
        return String.format(java.util.Locale.US, "%,.2f", amount);
    }

    /**
     * Shows a raw line exactly as typed (codes visible, nothing parsed), split in chunks so long lines
     * don't run off the screen.
     */
    public static List<Component> raw(String raw, int width) {
        List<Component> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            out.add(Component.text("-", NamedTextColor.DARK_GRAY).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            return out;
        }
        for (int i = 0; i < raw.length(); i += width) {
            String part = raw.substring(i, Math.min(raw.length(), i + width));
            out.add(Component.text(part, NamedTextColor.GRAY).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        return out;
    }
}
