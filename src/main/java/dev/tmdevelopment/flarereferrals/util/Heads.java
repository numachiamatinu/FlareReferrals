package dev.tmdevelopment.flarereferrals.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates player heads from what an admin types in chat:
 * - a player name                         (Notch)
 * - a texture value from minecraft-heads  (eyJ0ZXh0dXJlcyI6...)
 * - a texture id / URL                    (http://textures.minecraft.net/texture/&lt;id&gt; or just &lt;id&gt;)
 * - a whole /give command copied from minecraft-heads.com (the value inside is used)
 */
public final class Heads {

    private static final Pattern BASE64 = Pattern.compile("(eyJ[A-Za-z0-9+/=]{20,})");
    private static final Pattern TEXTURE_ID = Pattern.compile("(?:texture/)?([0-9a-fA-F]{50,70})");
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private Heads() {}

    /** Calls back on the main thread with the head, or null if the input is not valid. */
    public static void create(Plugin plugin, String input, Consumer<ItemStack> callback) {
        String in = input == null ? "" : input.trim();

        Matcher b = BASE64.matcher(in);
        if (b.find()) {
            callback.accept(fromTexture(b.group(1)));
            return;
        }
        Matcher t = TEXTURE_ID.matcher(in);
        if (t.find() && !NAME.matcher(in).matches()) {
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/"
                    + t.group(1).toLowerCase() + "\"}}}";
            callback.accept(fromTexture(Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8))));
            return;
        }
        if (NAME.matcher(in).matches()) {
            // the server looks up the skin of the player name (can take a moment the first time)
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                org.bukkit.OfflinePlayer op;
                try {
                    op = Compat.knownPlayer(in);
                    if (op == null) op = offlineByName(in);
                } catch (Throwable ex) {
                    op = null;
                }
                final org.bukkit.OfflinePlayer found = op;
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(found == null ? null : named(found)));
            });
            return;
        }
        callback.accept(null);
    }

    private static final java.util.Map<String, ItemStack> NAME_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> LOADING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Builds a head right now (used by menu layouts):
     * texture value / id -> textured head; player name -> cached head (loaded in background the first time).
     * Returns null when the input is not a head.
     */
    public static ItemStack now(Plugin plugin, String input) {
        String in = input == null ? "" : input.trim();
        Matcher b = BASE64.matcher(in);
        if (b.find()) return fromTexture(b.group(1));
        Matcher t = TEXTURE_ID.matcher(in);
        if (t.find() && !NAME.matcher(in).matches()) {
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/"
                    + t.group(1).toLowerCase() + "\"}}}";
            return fromTexture(Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
        }
        if (!NAME.matcher(in).matches()) return null;
        String key = in.toLowerCase();
        ItemStack cached = NAME_CACHE.get(key);
        if (cached != null) return cached.clone();
        if (LOADING.add(key)) {
            create(plugin, in, head -> {
                LOADING.remove(key);
                if (head != null) NAME_CACHE.put(key, head);
            });
        }
        return new ItemStack(Material.PLAYER_HEAD);
    }

    /** Head chosen by name in a file or in the editor (e.g. head:Notch): the skin is looked up by the server. */
    private static ItemStack named(org.bukkit.OfflinePlayer player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta) {
            ((SkullMeta) meta).setOwningPlayer(player);
            head.setItemMeta(meta);
        }
        return head;
    }

    /** Head of a player of this server (skin loaded by the server). */
    public static ItemStack of(org.bukkit.OfflinePlayer player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta && player != null) {
            setOwner((SkullMeta) meta, player);
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * Sets the owner of a head. Offline-mode players (name-based UUID, no proxy in front) have no Mojang account:
     * asking Mojang for them only fails with a warning in the console. For them the skin already loaded on the
     * server is used (for example by SkinsRestorer), otherwise the head stays plain.
     */
    public static void setOwner(SkullMeta meta, org.bukkit.OfflinePlayer player) {
        if (player.getUniqueId().version() != 3) {
            meta.setOwningPlayer(player);
            return;
        }
        org.bukkit.entity.Player online = player.getPlayer();
        if (online == null) return;
        try {
            // Paper: the profile of the online player, with the skin if the server has one
            Object profile = online.getClass().getMethod("getPlayerProfile").invoke(online);
            java.util.Collection<?> props = (java.util.Collection<?>) profile.getClass().getMethod("getProperties").invoke(profile);
            boolean skin = false;
            for (Object p : props) {
                if ("textures".equals(p.getClass().getMethod("getName").invoke(p))) skin = true;
            }
            if (!skin) return;
            for (java.lang.reflect.Method m : meta.getClass().getMethods()) {
                if (m.getName().equals("setPlayerProfile") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isInstance(profile)) {
                    m.invoke(meta, profile);
                    return;
                }
            }
        } catch (Throwable ignored) {
            // Spigot: plain head
        }
    }

    @SuppressWarnings("deprecation")
    private static org.bukkit.OfflinePlayer offlineByName(String name) {
        return Bukkit.getOfflinePlayer(name);
    }

    private static ItemStack fromTexture(String base64) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta) {
            Compat.setTexture((SkullMeta) meta, base64);
            head.setItemMeta(meta);
        }
        return head;
    }
}
