package dev.tmdevelopment.flarereferrals.layout;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.util.Platform;
import dev.tmdevelopment.flarereferrals.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Click actions of the menu items (menus/&lt;menu&gt;.yml "actions", or lore lines of the chest builder):
 * [close] [open] &lt;menu&gt; [back] [refresh] [player] &lt;cmd&gt; [console] &lt;cmd&gt; [chat] &lt;text&gt; [message] &lt;text&gt;
 * [broadcast] &lt;text&gt; [actionbar] &lt;text&gt; [title] title;subtitle;in;stay;out [sound] &lt;sound&gt; [volume] [pitch]
 * [delay] &lt;ticks&gt; [server] &lt;name&gt;. [left] / [right] / [shift-left] / [shift-right] in front = only that click.
 */
public final class MenuActions {

    private MenuActions() {
    }

    public static void run(FlareReferrals plugin, LayoutMenu menu, Player viewer, List<String> actions, ClickType click,
                           String... placeholders) {
        run(plugin, menu, viewer, new ArrayList<>(actions), 0, click, placeholders, false);
    }

    private static void run(FlareReferrals plugin, LayoutMenu menu, Player viewer, List<String> actions, int from,
                            ClickType click, String[] placeholders, boolean delayed) {
        // after a [delay] the menu actions run only if the player still has this menu open
        boolean menuOpen = !delayed || (menu != null && menu.isOpen());
        for (int i = from; i < actions.size(); i++) {
            if (!viewer.isOnline()) return;
            String a = actions.get(i) == null ? "" : actions.get(i).trim();
            String tag = ItemCodec.tag(a);
            if (tag != null && ItemCodec.CLICKS.contains(tag)) {
                if (!matches(tag, click)) continue;
                a = a.substring(a.indexOf(']') + 1).trim();
                tag = ItemCodec.tag(a);
            }
            if (tag == null) continue;
            String arg = Text.replace(a.substring(a.indexOf(']') + 1).trim(), placeholders);
            if (!menuOpen && (tag.equals("close") || tag.equals("open") || tag.equals("back") || tag.equals("refresh"))) continue;
            switch (tag) {
                case "delay": {
                    long ticks;
                    try {
                        ticks = Math.max(1, Long.parseLong(arg.split(" ")[0]));
                    } catch (NumberFormatException e) {
                        ticks = 20;
                    }
                    final int next = i + 1;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> run(plugin, menu, viewer, actions, next, click, placeholders, true), ticks);
                    return;
                }
                case "close":
                    viewer.closeInventory();
                    break;
                case "open":
                    menu.openOther(arg.toLowerCase(Locale.ROOT));
                    break;
                case "back":
                    menu.goBack();
                    break;
                case "refresh":
                    menu.refresh();
                    break;
                case "player":
                    viewer.performCommand(command(arg, viewer));
                    break;
                case "console":
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command(arg, viewer));
                    break;
                case "chat":
                    viewer.chat(Text.papi(viewer, arg));
                    break;
                case "message":
                    Platform.send(viewer, Text.parse(arg.replace("{prefix}", plugin.lang().prefix()), viewer));
                    break;
                case "broadcast":
                    Platform.broadcast(Text.parse(arg.replace("{prefix}", plugin.lang().prefix()), viewer));
                    break;
                case "actionbar":
                    Platform.actionBar(viewer, Text.parse(arg, viewer));
                    break;
                case "title": {
                    String[] p = arg.split(";", -1);
                    int in = num(p, 2, 10), stay = num(p, 3, 70), out = num(p, 4, 20);
                    Platform.title(viewer, Text.parse(p[0], viewer), Text.parse(p.length > 1 ? p[1] : "", viewer), in, stay, out);
                    break;
                }
                case "sound":
                    sound(viewer, arg);
                    break;
                case "server":
                    connect(plugin, viewer, arg);
                    break;
                default:
                    break;
            }
        }
    }

    private static boolean matches(String tag, ClickType click) {
        if (click == null) return true;
        switch (tag) {
            case "left": return click == ClickType.LEFT;
            case "right": return click == ClickType.RIGHT;
            case "shift-left": return click == ClickType.SHIFT_LEFT;
            case "shift-right": return click == ClickType.SHIFT_RIGHT;
            default: return true;
        }
    }

    private static String command(String arg, Player viewer) {
        String cmd = Text.papi(viewer, arg.replace("{player}", viewer.getName()));
        return cmd.startsWith("/") ? cmd.substring(1) : cmd;
    }

    private static int num(String[] parts, int index, int def) {
        if (parts.length <= index) return def;
        try {
            return Integer.parseInt(parts[index].trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** [sound] entity.player.levelup 1 1.5 (also the old names: ENTITY_PLAYER_LEVELUP). */
    static void sound(Player p, String arg) {
        String[] parts = arg.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return;
        float volume = parts.length > 1 ? flt(parts[1], 1f) : 1f;
        float pitch = parts.length > 2 ? flt(parts[2], 1f) : 1f;
        String name = parts[0];
        if (name.indexOf('.') < 0 && name.indexOf(':') < 0) {
            // enum style name: Sound.X (an enum before 1.21.3, constants after)
            try {
                Object sound = org.bukkit.Sound.class.getField(name.toUpperCase(Locale.ROOT)).get(null);
                p.getClass().getMethod("playSound", org.bukkit.Location.class, org.bukkit.Sound.class, float.class, float.class)
                        .invoke(p, p.getLocation(), sound, volume, pitch);
                return;
            } catch (Throwable ignored) {
            }
        }
        try {
            p.playSound(p.getLocation(), name.toLowerCase(Locale.ROOT), volume, pitch);
        } catch (Exception ignored) {
        }
    }

    private static float flt(String s, float def) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** [server] survival: sends the player to another server of the BungeeCord / Velocity network. */
    private static void connect(FlareReferrals plugin, Player p, String server) {
        if (server.isEmpty()) return;
        try {
            if (!Bukkit.getMessenger().isOutgoingChannelRegistered(plugin, "BungeeCord")) {
                Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(server.split(" ")[0]);
            p.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().warning("[server] " + server + ": " + e.getMessage());
        }
    }
}
