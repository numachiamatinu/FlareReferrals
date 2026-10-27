package dev.tmdevelopment.flarereferrals.layout;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Layout of a customizable menu (menus/&lt;id&gt;.yml): title, rows, where every button goes and the decorations.
 *
 * elements.&lt;id&gt;.slots     "4" or "18-44" or "0-8,45-53" or a list. Empty = hidden.
 * elements.&lt;id&gt;.material  optional override: DIAMOND, head:{player}, head:Notch, texture:&lt;value or id&gt;
 * elements.&lt;id&gt;.name/lore optional override of the texts in lang/en.yml
 * decorations[]: slots + (item | material/name/lore/glow) + actions, permission, update, admin-only
 */
public final class MenuLayout {

    public static final class Element {
        public List<Integer> slots = new ArrayList<>();
        public String material;
        public String name;
        public List<String> lore;
        public Boolean glow;
        public Integer customModelData;
        public boolean adminOnly;
        /** false when the file doesn't have "admin-only" yet (older versions). Never saved. */
        public transient boolean hasAdminOnlyKey = true;
    }

    public static final class Decoration {
        public List<Integer> slots = new ArrayList<>();
        public ItemStack item;          // full item (saved by the in-game editor)
        public String material;         // or a simple definition written by hand
        public String name;
        public List<String> lore;
        public boolean glow;
        public int amount = 1;
        public boolean adminOnly;
        public Integer customModelData;
        public List<String> actions = new ArrayList<>();
        /** Only players with this permission see it ("" = everyone). */
        public String permission = "";
        /** Drawn again every second (live placeholders). */
        public boolean update;
        /** Editor only: the decoration this one was copied from. Never saved. */
        public transient Decoration origin;
    }

    private final String id;
    private String title = "";
    private int rows = 6;
    private boolean enabled = true;
    private boolean hadEnabledKey = true;
    private List<String> openCommands = new ArrayList<>();
    private String openPermission = "";
    private boolean hadOpenCommandsKey = true;
    private final Map<String, Element> elements = new LinkedHashMap<>();
    private final List<Decoration> decorations = new ArrayList<>();
    /** Menu texts (section "texts" of the file), flat: "milestone.ready" -> value. */
    private final Map<String, Object> texts = new LinkedHashMap<>();

    public MenuLayout(String id) {
        this.id = id;
    }

    // ---------------------------------------------------------------- load / save

    public void load(ConfigurationSection s, ConfigurationSection defaults) {
        elements.clear();
        decorations.clear();
        texts.clear();
        org.bukkit.configuration.ConfigurationSection ts = s.getConfigurationSection("texts");
        if (ts != null) {
            for (String k : ts.getKeys(true)) if (!ts.isConfigurationSection(k)) texts.put(k, ts.get(k));
        }
        title = s.getString("title", defaults == null ? "" : defaults.getString("title", ""));
        rows = Math.max(1, Math.min(6, s.getInt("rows", defaults == null ? 6 : defaults.getInt("rows", 6))));
        hadEnabledKey = s.isSet("enabled");
        enabled = s.getBoolean("enabled", true);
        hadOpenCommandsKey = s.isSet("open-commands");
        openCommands = new ArrayList<>(hadOpenCommandsKey ? s.getStringList("open-commands")
                : (defaults == null ? java.util.Collections.<String>emptyList() : defaults.getStringList("open-commands")));
        openPermission = s.getString("open-permission", defaults == null ? "" : defaults.getString("open-permission", ""));

        ConfigurationSection els = s.getConfigurationSection("elements");
        ConfigurationSection defEls = defaults == null ? null : defaults.getConfigurationSection("elements");
        if (defEls != null) {
            // keep the order of the defaults, add elements introduced by new versions
            for (String key : defEls.getKeys(false)) {
                ConfigurationSection e = els != null && els.isConfigurationSection(key)
                        ? els.getConfigurationSection(key) : defEls.getConfigurationSection(key);
                elements.put(key, readElement(e));
            }
        }
        if (els != null) {
            for (String key : els.getKeys(false)) {
                if (!elements.containsKey(key)) elements.put(key, readElement(els.getConfigurationSection(key)));
            }
        }

        List<?> decos = s.isSet("decorations") ? s.getList("decorations")
                : (defaults == null ? null : defaults.getList("decorations"));
        if (decos != null) {
            for (Object o : decos) {
                if (o instanceof Map<?, ?>) { Map<?, ?> map = (Map<?, ?>) o; decorations.add(readDecoration(map)); }
            }
        }
    }

    private static Element readElement(ConfigurationSection s) {
        Element e = new Element();
        if (s == null) return e;
        e.slots = parseSlots(s.get("slots"));
        e.material = s.getString("material");
        e.name = s.getString("name");
        e.lore = s.isList("lore") ? s.getStringList("lore") : null;
        if (s.isSet("glow")) e.glow = s.getBoolean("glow");
        if (s.isSet("custom-model-data")) e.customModelData = s.getInt("custom-model-data");
        e.adminOnly = s.getBoolean("admin-only", false);
        e.hasAdminOnlyKey = s.isSet("admin-only");
        return e;
    }

    private static Decoration readDecoration(Map<?, ?> map) {
        Decoration d = new Decoration();
        d.slots = parseSlots(map.get("slots"));
        Object item = map.get("item");
        if (item instanceof ItemStack) { ItemStack it = (ItemStack) item; d.item = it; }
        Object m = map.get("material");
        if (m != null) d.material = String.valueOf(m);
        Object n = map.get("name");
        if (n != null) d.name = String.valueOf(n);
        Object l = map.get("lore");
        if (l instanceof List<?>) {
            List<?> list = (List<?>) l;
            d.lore = new ArrayList<>();
            for (Object x : list) d.lore.add(String.valueOf(x));
        }
        Object g = map.get("glow");
        d.glow = g instanceof Boolean && (Boolean) g;
        Object am = map.get("amount");
        if (am instanceof Number) { Number an = (Number) am; d.amount = Math.max(1, Math.min(64, an.intValue())); }
        Object ao = map.get("admin-only");
        d.adminOnly = ao instanceof Boolean && (Boolean) ao;
        Object cmd = map.get("custom-model-data");
        if (cmd instanceof Number) { Number cn = (Number) cmd; d.customModelData = cn.intValue(); }
        Object a = map.get("actions");
        if (a instanceof List<?>) { List<?> list = (List<?>) a; for (Object x : list) d.actions.add(String.valueOf(x)); }
        Object perm = map.get("permission");
        if (perm != null) d.permission = String.valueOf(perm).trim();
        Object up = map.get("update");
        d.update = up instanceof Boolean && (Boolean) up;
        return d;
    }

    /** One decoration as it is written in the file (without slots). */
    public static Map<String, Object> write(Decoration d) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (d.item != null) {
            map.put("item", d.item);
        } else {
            // readable form: every field is there, ready to be edited by hand
            map.put("material", d.material == null ? "STONE" : d.material);
            if (d.name != null) map.put("name", d.name); // no name = the Minecraft name of the item
            map.put("lore", d.lore == null ? new ArrayList<String>() : d.lore);
            map.put("glow", d.glow);
            if (d.amount > 1) map.put("amount", d.amount);
            if (d.customModelData != null) map.put("custom-model-data", d.customModelData);
        }
        map.put("admin-only", d.adminOnly);
        if (d.permission != null && !d.permission.isEmpty()) map.put("permission", d.permission);
        if (d.update) map.put("update", true);
        map.put("actions", d.actions);
        return map;
    }

    /** Reads a decoration written by {@link #write} (slots, if any, too). */
    public static Decoration read(Map<?, ?> map) {
        return readDecoration(map);
    }

    public void save(ConfigurationSection s) {
        s.set("enabled", enabled);
        s.set("title", title);
        s.set("rows", rows);
        s.set("open-commands", new ArrayList<>(openCommands));
        s.set("open-permission", openPermission == null ? "" : openPermission);
        s.set("elements", null);
        ConfigurationSection els = s.createSection("elements");
        for (Map.Entry<String, Element> en : elements.entrySet()) {
            Element e = en.getValue();
            ConfigurationSection c = els.createSection(en.getKey());
            c.set("slots", formatSlots(e.slots));
            if (e.material != null) c.set("material", e.material);
            if (e.name != null) c.set("name", e.name);
            if (e.lore != null) c.set("lore", e.lore);
            if (e.glow != null) c.set("glow", e.glow);
            if (e.customModelData != null) c.set("custom-model-data", e.customModelData);
            c.set("admin-only", e.adminOnly);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Decoration d : decorations) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("slots", formatSlots(d.slots));
            map.putAll(write(d));
            list.add(map);
        }
        s.set("decorations", list);
        for (Map.Entry<String, Object> t : texts.entrySet()) s.set("texts." + t.getKey(), t.getValue());
    }

    // ---------------------------------------------------------------- slots "0-8,45-53"

    public static List<Integer> parseSlots(Object o) {
        TreeSet<Integer> out = new TreeSet<>();
        if (o == null) return new ArrayList<>();
        if (o instanceof Number) {
            Number n = (Number) o;
            out.add(n.intValue());
        } else if (o instanceof List<?>) {
            List<?> list = (List<?>) o;
            for (Object x : list) out.addAll(parseSlots(x));
        } else {
            for (String part : String.valueOf(o).split(",")) {
                part = part.trim();
                if (part.isEmpty()) continue;
                try {
                    int dash = part.indexOf('-');
                    if (dash > 0) {
                        int a = Integer.parseInt(part.substring(0, dash).trim());
                        int b = Integer.parseInt(part.substring(dash + 1).trim());
                        for (int i = Math.min(a, b); i <= Math.max(a, b); i++) out.add(i);
                    } else {
                        out.add(Integer.parseInt(part));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        out.removeIf(i -> i < 0 || i > 53);
        return new ArrayList<>(out);
    }

    public static String formatSlots(List<Integer> slots) {
        List<Integer> sorted = new ArrayList<>(new TreeSet<>(slots));
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < sorted.size()) {
            int start = sorted.get(i);
            int end = start;
            while (i + 1 < sorted.size() && sorted.get(i + 1) == end + 1) {
                end = sorted.get(++i);
            }
            if (sb.length() > 0) sb.append(',');
            sb.append(start == end ? String.valueOf(start) : start + "-" + end);
            i++;
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- access

    public String id() { return id; }
    public String title() { return title; }
    public void title(String v) { title = v == null ? "" : v; }
    public int rows() { return rows; }
    public boolean enabled() { return enabled; }
    public void enabled(boolean v) { enabled = v; }
    public boolean hadEnabledKey() { return hadEnabledKey; }
    public List<String> openCommands() { return openCommands; }
    public String openPermission() { return openPermission; }
    public void openPermission(String v) { openPermission = v == null ? "" : v.trim(); }
    public boolean hadOpenCommandsKey() { return hadOpenCommandsKey; }
    public void rows(int v) { rows = Math.max(1, Math.min(6, v)); }
    public Map<String, Element> elements() { return elements; }
    public Element element(String id) { return elements.computeIfAbsent(id, k -> new Element()); }
    public List<Decoration> decorations() { return decorations; }
    public Map<String, Object> texts() { return texts; }
}
