package dev.tmdevelopment.flarereferrals.util;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Things that are done differently on each server software / version (1.16.5 → latest).
 * Every newer API is used through reflection, with a fallback that works on old versions.
 */
public final class Compat {

    private static final Pattern URL_IN_JSON = Pattern.compile("\"url\"\\s*:\\s*\"(https?://[^\"]+)\"");
    private static CommandMap commandMap;

    private Compat() {
    }

    // ------------------------------------------------------------------ commands

    /** The server command map (Paper has Bukkit.getCommandMap(), Spigot only through CraftServer). */
    public static CommandMap commandMap() {
        if (commandMap != null) return commandMap;
        try {
            Method m = Bukkit.getServer().getClass().getMethod("getCommandMap");
            commandMap = (CommandMap) m.invoke(Bukkit.getServer());
        } catch (Throwable t) {
            throw new IllegalStateException("Can't reach the command map of this server", t);
        }
        return commandMap;
    }

    /** Sends the new command list to the game (needed on Spigot for commands added after startup). */
    public static void syncCommands() {
        try {
            Method m = Bukkit.getServer().getClass().getDeclaredMethod("syncCommands");
            m.setAccessible(true);
            m.invoke(Bukkit.getServer());
        } catch (Throwable ignored) {
        }
    }

    /** Removes a command registered by us from the known commands (all names and aliases, also "prefix:name"). */
    @SuppressWarnings("unchecked")
    public static void removeKnown(Command command, String prefix) {
        try {
            CommandMap map = commandMap();
            Map<String, Command> known = null;
            try {
                Method m = CommandMap.class.getMethod("getKnownCommands"); // Paper (and Spigot 1.20+)
                known = (Map<String, Command>) m.invoke(map);
            } catch (NoSuchMethodException e) {
                Class<?> c = map.getClass();
                while (c != null && known == null) {
                    try {
                        Field f = c.getDeclaredField("knownCommands");
                        f.setAccessible(true);
                        known = (Map<String, Command>) f.get(map);
                    } catch (NoSuchFieldException ignored) {
                        c = c.getSuperclass();
                    }
                }
            }
            if (known != null) {
                // remove by key: some servers give a map whose iterator can't remove
                java.util.List<String> keys = new java.util.ArrayList<>();
                for (Map.Entry<String, Command> en : known.entrySet()) if (en.getValue() == command) keys.add(en.getKey());
                String name = command.getName().toLowerCase(java.util.Locale.ROOT);
                if (known.get(name) == command) keys.add(name);
                if (prefix != null && known.get(prefix + ":" + name) == command) keys.add(prefix + ":" + name);
                for (String k : keys) known.remove(k);
            }
            command.unregister(map);
        } catch (Throwable ignored) {
        }
    }

    public static void removeKnown(Command command) {
        removeKnown(command, null);
    }

    // ------------------------------------------------------------------ players

    /** Player who already joined, by name (no web lookup, never blocks). */
    public static OfflinePlayer knownPlayer(String name) {
        if (name == null) return null;
        OfflinePlayer online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)) return op;
        }
        return null;
    }

    /** Holder of the menu a player has open (InventoryView is a class before 1.21 and an interface after). */
    public static Object topHolder(org.bukkit.entity.Player p) {
        try {
            // methods of the public API types: the server classes behind them may not be accessible by reflection
            Object view = Class.forName("org.bukkit.entity.HumanEntity").getMethod("getOpenInventory").invoke(p);
            Object top = Class.forName("org.bukkit.inventory.InventoryView").getMethod("getTopInventory").invoke(view);
            return top == null ? null : ((org.bukkit.inventory.Inventory) top).getHolder();
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ items

    /** Enchantment glint without enchantments in the lore. */
    public static void glow(ItemMeta meta) {
        try {
            Method m = ItemMeta.class.getMethod("setEnchantmentGlintOverride", Boolean.class); // 1.20.5+
            m.invoke(meta, Boolean.TRUE);
            return;
        } catch (Throwable ignored) {
        }
        Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
        if (e == null) e = Enchantment.getByKey(NamespacedKey.minecraft("luck_of_the_sea"));
        if (e != null) {
            meta.addEnchant(e, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
    }

    public static boolean hasGlow(ItemMeta meta) {
        if (meta.hasEnchants()) return true;
        try {
            Method has = ItemMeta.class.getMethod("hasEnchantmentGlintOverride");
            if (!(Boolean) has.invoke(meta)) return false;
            Object v = ItemMeta.class.getMethod("getEnchantmentGlintOverride").invoke(meta);
            return Boolean.TRUE.equals(v);
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ heads

    /** Puts a texture (base64 value from minecraft-heads) on a head. */
    public static void setTexture(SkullMeta meta, String base64) {
        UUID id = UUID.nameUUIDFromBytes(base64.getBytes(StandardCharsets.UTF_8));
        // 1.18.1+ : Bukkit PlayerProfile API (Spigot and Paper)
        try {
            String url = textureUrl(base64);
            if (url != null) {
                Method create = Bukkit.class.getMethod("createPlayerProfile", UUID.class, String.class);
                Object profile = create.invoke(null, id, "FlareHead");
                Class<?> pp = Class.forName("org.bukkit.profile.PlayerProfile");
                Class<?> pt = Class.forName("org.bukkit.profile.PlayerTextures");
                Object textures = pp.getMethod("getTextures").invoke(profile);
                pt.getMethod("setSkin", URL.class).invoke(textures, new URL(url));
                pp.getMethod("setTextures", pt).invoke(profile, textures);
                Method set = SkullMeta.class.getMethod("setOwnerProfile", pp);
                set.invoke(meta, profile);
                return;
            }
        } catch (Throwable ignored) {
        }
        // older: Mojang GameProfile inside CraftMetaSkull
        try {
            Class<?> gp = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> prop = Class.forName("com.mojang.authlib.properties.Property");
            Object profile = gp.getConstructor(UUID.class, String.class).newInstance(id, "FlareHead");
            Object props = gp.getMethod("getProperties").invoke(profile);
            Object property = prop.getConstructor(String.class, String.class).newInstance("textures", base64);
            props.getClass().getMethod("put", Object.class, Object.class).invoke(props, "textures", property);
            try {
                Method set = meta.getClass().getDeclaredMethod("setProfile", gp);
                set.setAccessible(true);
                set.invoke(meta, profile);
            } catch (NoSuchMethodException e) {
                Field f = meta.getClass().getDeclaredField("profile");
                f.setAccessible(true);
                f.set(meta, profile);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Texture of a head as "texture:<value or id>", "head:<name>" or null. */
    public static String describeHead(SkullMeta meta) {
        // 1.18.1+
        try {
            Class<?> pp = Class.forName("org.bukkit.profile.PlayerProfile");
            Class<?> pt = Class.forName("org.bukkit.profile.PlayerTextures");
            Object profile = SkullMeta.class.getMethod("getOwnerProfile").invoke(meta);
            if (profile != null) {
                Object textures = pp.getMethod("getTextures").invoke(profile);
                Object skin = pt.getMethod("getSkin").invoke(textures);
                if (skin != null) {
                    String u = skin.toString();
                    return "texture:" + u.substring(u.lastIndexOf('/') + 1);
                }
                Object name = pp.getMethod("getName").invoke(profile);
                if (name != null) return "head:" + name;
            }
        } catch (Throwable ignored) {
        }
        // older: GameProfile field
        try {
            Field f = meta.getClass().getDeclaredField("profile");
            f.setAccessible(true);
            Object profile = f.get(meta);
            if (profile != null) {
                Object props = profile.getClass().getMethod("getProperties").invoke(profile);
                Collection<?> list = (Collection<?>) props.getClass().getMethod("get", Object.class).invoke(props, "textures");
                for (Object p : list) {
                    Object value;
                    try {
                        value = p.getClass().getMethod("getValue").invoke(p);
                    } catch (NoSuchMethodException e) {
                        value = p.getClass().getMethod("value").invoke(p);
                    }
                    if (value != null) return "texture:" + value;
                }
                Object name = profile.getClass().getMethod("getName").invoke(profile);
                if (name != null) return "head:" + name;
            }
        } catch (Throwable ignored) {
        }
        OfflinePlayer owner = meta.getOwningPlayer();
        return owner != null && owner.getName() != null ? "head:" + owner.getName() : null;
    }

    private static String textureUrl(String base64) {
        try {
            String json = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            Matcher m = URL_IN_JSON.matcher(json);
            return m.find() ? m.group(1) : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
