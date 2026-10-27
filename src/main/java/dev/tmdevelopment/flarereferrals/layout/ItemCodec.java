package dev.tmdevelopment.flarereferrals.layout;

import dev.tmdevelopment.flarereferrals.util.Platform;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Items of a menu &lt;-&gt; decorations of menus/&lt;menu&gt;.yml, used by the chest builder and the editor.
 *
 * Lore lines starting with [ are actions or options, not text: [close], [open] top, [left] [player] spawn,
 * [permission] node, [admin-only], [update], [button] collect. A line that must start with [ is written \[.
 */
public final class ItemCodec {

    /** Actions a decoration can run when clicked. */
    public static final List<String> ACTIONS = dev.tmdevelopment.flarereferrals.util.J8.list("close", "open", "back", "refresh",
            "player", "console", "chat", "message", "broadcast", "actionbar", "title", "sound", "delay", "server");
    /** Click types that can be put in front of an action: [left] [open] top. */
    public static final List<String> CLICKS = dev.tmdevelopment.flarereferrals.util.J8.list("left", "right", "shift-left", "shift-right");

    private static final Pattern CODES = Pattern.compile("(?i)§[0-9A-FK-ORX]");
    private static final Pattern LEADING_CODES = Pattern.compile("^(?:\\s*(?:&#[0-9a-fA-F]{6}|&[0-9a-fk-orA-FK-OR]))+");

    private static NamespacedKey keyDef;
    private static NamespacedKey keySig;
    private static NamespacedKey keyRows;
    private static NamespacedKey keyButton;
    private static String namespace = "flarereferrals";

    private ItemCodec() {
    }

    static void init(Plugin plugin) {
        if (keyDef != null) return;
        keyDef = new NamespacedKey(plugin, "builder_def");
        keySig = new NamespacedKey(plugin, "builder_sig");
        keyRows = new NamespacedKey(plugin, "builder_rows");
        keyButton = new NamespacedKey(plugin, "builder_button");
        namespace = keyDef.getNamespace();
    }

    /** What an item of the chest is. */
    public static final class Read {
        /** Plugin button placed here ([button] id), or null. */
        public String button;
        /** Look of a button written by hand (material, name, lore of the item), or null = the look of the plugin. */
        public MenuLayout.Decoration look;
        /** Button written by hand: [admin-only] in its lore (null = put in the chest by the plugin). */
        public Boolean buttonAdminOnly;
        /** Otherwise the decoration. */
        public MenuLayout.Decoration deco;
        /** Rows of the menu when the item was put in the chest by "layout chest" (0 = not from there). */
        public int rows;
        /** [xyz] lines that are not a known action. */
        public final List<String> unknown = new ArrayList<>();
    }

    // ------------------------------------------------------------------ reading

    public static Read read(ItemStack it) {
        Read r = new Read();
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            PersistentDataContainer pdc = m.getPersistentDataContainer();
            Integer rows = keyRows == null ? null : pdc.get(keyRows, PersistentDataType.INTEGER);
            if (rows != null) r.rows = rows;
            String button = keyButton == null ? null : pdc.get(keyButton, PersistentDataType.STRING);
            if (button != null) {
                r.button = button;
                return r;
            }
            // put in the chest by "layout chest" and not changed since: the exact definition of the file
            String def = keyDef == null ? null : pdc.get(keyDef, PersistentDataType.STRING);
            String sig = pdc.get(keySig, PersistentDataType.STRING);
            if (def != null && sig != null && sig.equals(signature(it))) {
                MenuLayout.Decoration d = decode(def);
                if (d != null) {
                    r.deco = d;
                    return r;
                }
            }
        }
        List<String> visible = new ArrayList<>();
        MenuLayout.Decoration opts = new MenuLayout.Decoration();
        if (m != null && m.hasLore() && m.getLore() != null) {
            for (String line : m.getLore()) {
                String editable = Platform.toEditable(line);
                String bare = LEADING_CODES.matcher(editable).replaceFirst("").trim();
                if (bare.startsWith("\\[")) {
                    // \[ = a lore line that really starts with [
                    visible.add(line.replaceFirst("\\\\\\[", "["));
                    continue;
                }
                if (!bare.startsWith("[") || bare.indexOf(']') < 0) {
                    visible.add(line);
                    continue;
                }
                String tag = bare.substring(1, bare.indexOf(']')).trim().toLowerCase(Locale.ROOT);
                String rest = bare.substring(bare.indexOf(']') + 1).trim();
                if (tag.equals("button")) {
                    r.button = rest.toLowerCase(Locale.ROOT);
                } else if (tag.equals("permission")) {
                    opts.permission = rest;
                } else if (tag.equals("admin-only")) {
                    opts.adminOnly = true;
                } else if (tag.equals("update")) {
                    opts.update = true;
                } else {
                    if (!validAction(bare)) r.unknown.add(bare);
                    opts.actions.add(bare);
                }
            }
        }
        if (r.button != null) {
            r.buttonAdminOnly = opts.adminOnly;
            // a button made by hand with its own name: the button takes the look of the item
            if (m != null && m.hasDisplayName()) r.look = describe(it, visible);
            return r;
        }
        MenuLayout.Decoration d = needsFull(it) ? full(it, visible) : describe(it, visible);
        d.permission = opts.permission;
        d.adminOnly = opts.adminOnly;
        d.update = opts.update;
        d.actions = opts.actions;
        r.deco = d;
        return r;
    }

    /** "[left] [open] top" / "[close]": is it an action that exists? */
    public static boolean validAction(String action) {
        String a = action.trim();
        String tag = tag(a);
        if (tag != null && CLICKS.contains(tag)) {
            a = a.substring(a.indexOf(']') + 1).trim();
            tag = tag(a);
        }
        return tag != null && ACTIONS.contains(tag);
    }

    /** "[open] top" -> "open"; null when the text doesn't start with [tag]. */
    public static String tag(String s) {
        if (s == null || !s.startsWith("[") || s.indexOf(']') < 0) return null;
        return s.substring(1, s.indexOf(']')).trim().toLowerCase(Locale.ROOT);
    }

    /** Readable form: material (heads keep their texture), name, lore, glow, amount, custom model data. */
    public static MenuLayout.Decoration describe(ItemStack it, List<String> lore) {
        MenuLayout.Decoration d = new MenuLayout.Decoration();
        ItemMeta m = it.getItemMeta();
        String material = it.getType().name();
        String plainName = m != null && m.hasDisplayName() ? plain(m.getDisplayName()) : "";
        if (m instanceof SkullMeta) {
            // a head named {player} shows the head of the player who opens the menu
            if (plainName.trim().equalsIgnoreCase("{player}") || plainName.trim().equalsIgnoreCase("%player%")) {
                material = "head:{player}";
            } else {
                String head = dev.tmdevelopment.flarereferrals.util.Compat.describeHead((SkullMeta) m);
                if (head != null) material = head;
            }
        }
        d.material = material;
        // no name: glass panes stay without a name (borders), the other items keep their Minecraft name
        if (m != null && m.hasDisplayName()) d.name = Platform.toEditable(m.getDisplayName());
        else d.name = it.getType().name().endsWith("GLASS_PANE") ? " " : null;
        d.lore = new ArrayList<>();
        if (lore != null) for (String c : lore) d.lore.add(Platform.toEditable(c));
        if (m != null) {
            d.glow = dev.tmdevelopment.flarereferrals.util.Compat.hasGlow(m);
            try {
                if (m.hasCustomModelData()) d.customModelData = m.getCustomModelData();
            } catch (Throwable ignored) {
            }
        }
        d.amount = Math.max(1, it.getAmount());
        return d;
    }

    /** Full item (enchantments, colors, NBT...) with the given lore and without the builder data. */
    private static MenuLayout.Decoration full(ItemStack it, List<String> lore) {
        MenuLayout.Decoration d = new MenuLayout.Decoration();
        ItemStack c = it.clone();
        ItemMeta m = c.getItemMeta();
        if (m != null) {
            m.setLore(lore == null || lore.isEmpty() ? null : lore);
            removeOwnKeys(m.getPersistentDataContainer());
            c.setItemMeta(m);
        }
        d.item = c;
        return d;
    }

    /** Readable or full: what the in-game editor and the builder save for an item. */
    public static MenuLayout.Decoration of(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        List<String> lore = m != null && m.hasLore() && m.getLore() != null ? m.getLore() : new ArrayList<String>();
        return needsFull(it) ? full(it, lore) : describe(it, lore);
    }

    /**
     * True when the readable form (material, name, lore, glow, amount, model data, head texture) would lose
     * something of the item: enchantments, leather color, potion, banner, damage, data of other plugins...
     */
    public static boolean needsFull(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        try {
            ItemMeta m = it.getItemMeta();
            if (m == null) return false;
            m.setDisplayName(null);
            m.setLore(null);
            if (complexModelData(m)) return true;
            try {
                m.setCustomModelData(null);
            } catch (Throwable ignored) {
            }
            // the glow of the plugin (one hidden level 1 enchantment or the glint override) is not lost
            if (m.hasEnchants() && m.getEnchants().size() == 1 && m.hasItemFlag(ItemFlag.HIDE_ENCHANTS)
                    && m.getEnchants().values().iterator().next() == 1) {
                for (Enchantment e : new ArrayList<>(m.getEnchants().keySet())) m.removeEnchant(e);
            }
            try {
                ItemMeta.class.getMethod("setEnchantmentGlintOverride", Boolean.class).invoke(m, (Object) null);
            } catch (Throwable ignored) {
            }
            for (ItemFlag f : ItemFlag.values()) m.removeItemFlags(f);
            removeOwnKeys(m.getPersistentDataContainer());
            if (m instanceof SkullMeta && dev.tmdevelopment.flarereferrals.util.Compat.describeHead((SkullMeta) m) != null) {
                clearHead((SkullMeta) m);
            }
            return !Bukkit.getServer().getItemFactory().equals(m, null);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 1.21.4+: custom model data can have several numbers, texts, flags and colors. The readable form keeps
     * only one whole number, so anything else is saved with the full item.
     */
    private static boolean complexModelData(ItemMeta m) {
        try {
            try {
                Object has = ItemMeta.class.getMethod("hasCustomModelDataComponent").invoke(m);
                if (!Boolean.TRUE.equals(has)) return false;
            } catch (NoSuchMethodException old) {
                // 1.21.4 has only the getter (an empty component when there is no data)
            }
            Object c = ItemMeta.class.getMethod("getCustomModelDataComponent").invoke(m);
            Class<?> type = Class.forName("org.bukkit.inventory.meta.components.CustomModelDataComponent");
            List<?> floats = (List<?>) type.getMethod("getFloats").invoke(c);
            List<?> strings = (List<?>) type.getMethod("getStrings").invoke(c);
            List<?> flags = (List<?>) type.getMethod("getFlags").invoke(c);
            List<?> colors = (List<?>) type.getMethod("getColors").invoke(c);
            if (!strings.isEmpty() || !flags.isEmpty() || !colors.isEmpty() || floats.size() > 1) return true;
            if (floats.size() == 1) {
                float f = ((Number) floats.get(0)).floatValue();
                return f != (float) Math.floor(f);
            }
            return false;
        } catch (Throwable t) {
            return false; // before 1.21.4: one number only
        }
    }

    private static void clearHead(SkullMeta m) {
        try {
            SkullMeta.class.getMethod("setOwnerProfile", Class.forName("org.bukkit.profile.PlayerProfile")).invoke(m, (Object) null);
            return;
        } catch (Throwable ignored) {
        }
        try {
            m.setOwningPlayer(null);
        } catch (Throwable ignored) {
        }
    }

    private static void removeOwnKeys(PersistentDataContainer pdc) {
        try {
            for (NamespacedKey k : new ArrayList<>(pdc.getKeys())) {
                if (k.getNamespace().equals(namespace)) pdc.remove(k);
            }
        } catch (Throwable ignored) {
        }
    }

    /** A lore text line (&amp; codes) that starts with [ gets a \ so it is not read as an action: "&amp;7\[VIP]". */
    public static String escape(String line) {
        if (line == null) return "";
        java.util.regex.Matcher m = LEADING_CODES.matcher(line);
        int end = m.find() ? m.end() : 0;
        String rest = line.substring(end);
        if (!rest.trim().startsWith("[")) return line;
        int lead = rest.indexOf('[');
        return line.substring(0, end) + rest.substring(0, lead) + "\\" + rest.substring(lead);
    }

    /** Same as escape, for a lore line as it is on the item (§ codes). */
    public static String escapeLegacy(String legacy) {
        if (legacy == null || !plain(legacy).trim().startsWith("[")) return legacy;
        int i = legacy.indexOf('[');
        return legacy.substring(0, i) + "\\" + legacy.substring(i);
    }

    /** Text without color codes. */
    public static String plain(String legacy) {
        return legacy == null ? "" : CODES.matcher(legacy).replaceAll("");
    }

    // ------------------------------------------------------------------ writing (layout chest)

    /** Marks an item put in the chest: the decoration it comes from and the rows of the menu. */
    public static ItemStack tagDecoration(ItemStack item, MenuLayout.Decoration d, int rows) {
        ItemStack c = item.clone();
        ItemMeta m = c.getItemMeta();
        if (m == null) return c;
        m.getPersistentDataContainer().set(keyRows, PersistentDataType.INTEGER, rows);
        m.getPersistentDataContainer().set(keyDef, PersistentDataType.STRING, encode(d));
        c.setItemMeta(m);
        // the signature is of the item as it is now: if it is changed in the chest, it is read again from its lore
        m = c.getItemMeta();
        m.getPersistentDataContainer().set(keySig, PersistentDataType.STRING, signature(c));
        c.setItemMeta(m);
        return c;
    }

    /** Marks a plugin button put in the chest. */
    public static ItemStack tagButton(ItemStack item, String id, int rows) {
        ItemStack c = item.clone();
        ItemMeta m = c.getItemMeta();
        if (m == null) return c;
        m.getPersistentDataContainer().set(keyRows, PersistentDataType.INTEGER, rows);
        m.getPersistentDataContainer().set(keyButton, PersistentDataType.STRING, id);
        c.setItemMeta(m);
        return c;
    }

    /** Lines added to the lore in the chest so the admin sees the options and actions of an item. */
    public static List<String> optionLines(MenuLayout.Decoration d) {
        List<String> out = new ArrayList<>();
        if (d.permission != null && !d.permission.isEmpty()) out.add("[permission] " + d.permission);
        if (d.adminOnly) out.add("[admin-only]");
        if (d.update) out.add("[update]");
        for (String a : d.actions) {
            // a lore line that is text but starts with [ must keep its \
            out.add(a);
        }
        return out;
    }

    static String encode(MenuLayout.Decoration d) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("d", MenuLayout.write(d));
        return y.saveToString();
    }

    static MenuLayout.Decoration decode(String s) {
        try {
            YamlConfiguration y = new YamlConfiguration();
            y.loadFromString(s);
            ConfigurationSection sec = y.getConfigurationSection("d");
            if (sec == null) return null;
            Map<String, Object> map = sec.getValues(false);
            return MenuLayout.read(map);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String signature(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        StringBuilder sb = new StringBuilder(it.getType().name()).append('|').append(it.getAmount());
        if (m != null) {
            sb.append('|').append(m.hasDisplayName() ? m.getDisplayName() : "");
            sb.append('|').append(m.hasLore() ? String.valueOf(m.getLore()) : "");
            sb.append('|').append(m.getEnchants());
        }
        return Integer.toHexString(sb.toString().hashCode()) + ":" + sb.length();
    }

    /** Key used to merge equal decorations in one entry with many slots. */
    static String key(MenuLayout.Decoration d) {
        if (d.item != null) return "item|" + encode(d);
        return d.material + "|" + d.name + "|" + d.lore + "|" + d.glow + "|" + d.amount + "|" + d.customModelData
                + "|" + d.actions + "|" + d.adminOnly + "|" + d.permission + "|" + d.update;
    }

    static boolean isAir(ItemStack it) {
        return it == null || it.getType() == Material.AIR || it.getType().isAir();
    }
}
