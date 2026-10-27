package dev.tmdevelopment.flarereferrals.layout;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads / saves menus/&lt;id&gt;.yml. Elements added by new versions are merged in automatically. */
public final class LayoutManager {

    /** Customizable menus, in the order shown in the admin list. */
    public static final List<String> IDS = dev.tmdevelopment.flarereferrals.util.J8.list("main", "rewards", "top", "preview", "collect");
    /** Files of menus/ that are not menus made with the chest builder: the menus of the plugin and admin.yml (texts). */
    public static final List<String> RESERVED = dev.tmdevelopment.flarereferrals.util.J8.list("main", "rewards", "top", "preview", "collect", "admin");

    private final FlareReferrals plugin;
    private final File dir;
    private final Map<String, MenuLayout> layouts = new LinkedHashMap<>();
    /** Menus made with /referrals layout add (menus/&lt;name&gt;.yml), sorted by name. */
    private final java.util.TreeSet<String> custom = new java.util.TreeSet<>();
    /** Goes up every time a menu is replaced from outside its editor (reload, import, reset, remove). */
    private final Map<String, Integer> versions = new java.util.HashMap<>();
    /** Header of the files of those menus. */
    static final String CUSTOM_HEADER = "templates/custom-menu.yml";
    private static final java.util.regex.Pattern NAME = java.util.regex.Pattern.compile("[a-z0-9-]{1,32}");

    public LayoutManager(FlareReferrals plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "menus");
        ItemCodec.init(plugin);
    }

    public int version(String id) {
        return versions.getOrDefault(id, 0);
    }

    /** The menu was replaced: editors opened before don't save over it. */
    public void changed(String id) {
        versions.merge(id, 1, Integer::sum);
    }

    /** Closes the editors of a menu that other admins have open (they save what they did first). */
    public void closeEditors(String id) {
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            dev.tmdevelopment.flarereferrals.gui.Menu m = dev.tmdevelopment.flarereferrals.gui.Menu.openFor(p);
            if (m instanceof LayoutMenu && ((LayoutMenu) m).editing() && id.equals(((LayoutMenu) m).layoutId())) p.closeInventory();
        }
    }

    public void load() {
        if (!dir.exists() && !dir.mkdirs()) plugin.getLogger().warning("Could not create the menus folder");
        for (String id : new java.util.ArrayList<>(layouts.keySet())) changed(id);
        Map<String, MenuLayout> before = new LinkedHashMap<>(layouts);
        java.util.Set<String> customBefore = new java.util.TreeSet<>(custom);
        layouts.clear();
        custom.clear();
        for (String id : IDS) {
            File f = new File(dir, id + ".yml");
            if (!f.exists()) plugin.saveResource("menus/" + id + ".yml", false);
            dev.tmdevelopment.flarereferrals.util.Headers.sync(plugin, f, "menus/" + id + ".yml");
            YamlConfiguration def = defaults(id);
            YamlConfiguration yml = dev.tmdevelopment.flarereferrals.util.SafeYaml.load(plugin, f, "menus/" + id + ".yml");
            if (yml == null) {
                // a file with a mistake is never saved: the last working layout (or the default one) is used
                MenuLayout keep = before.get(id);
                if (keep == null) {
                    keep = new MenuLayout(id);
                    keep.load(def == null ? new YamlConfiguration() : def, def);
                }
                layouts.put(id, keep);
                continue;
            }
            // old command name first, then old default texts -> new ones (edited texts are kept)
            boolean updated = dev.tmdevelopment.flarereferrals.util.TextUpdates.renameCommand(yml);
            updated |= dev.tmdevelopment.flarereferrals.util.TextUpdates.apply(plugin, yml, def, "menus/" + id + ".yml");
            updated |= borderColor(yml, def);
            MenuLayout layout = new MenuLayout(id);
            layout.load(yml, def);
            layouts.put(id, layout);
            boolean completed = completeFile(layout, def);
            if (completed || updated) {
                save(id);
                if (completed) plugin.getLogger().info("menus/" + id + ".yml: added material / name / lore of every item so you can edit them.");
                else plugin.getLogger().info("menus/" + id + ".yml: texts and colors you never edited updated to the new version.");
            }
        }
        loadCustom(before, customBefore);
    }

    /** The menus made with the chest builder: every other menus/&lt;name&gt;.yml. */
    private void loadCustom(Map<String, MenuLayout> before, java.util.Set<String> customBefore) {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        java.util.Arrays.sort(files);
        for (File f : files) {
            String id = f.getName().substring(0, f.getName().length() - 4);
            if (RESERVED.contains(id)) continue;
            if (!validName(id)) {
                plugin.getLogger().warning("menus/" + f.getName() + " is not loaded: menu names use only a-z, 0-9 and -, at most 32 characters.");
                continue;
            }
            dev.tmdevelopment.flarereferrals.util.Headers.sync(plugin, f, CUSTOM_HEADER);
            YamlConfiguration yml = dev.tmdevelopment.flarereferrals.util.SafeYaml.load(plugin, f, "menus/" + f.getName());
            if (yml == null) {
                // mistake in the file: the menu keeps working as it was before the reload (not loaded at startup)
                if (customBefore.contains(id) && before.get(id) != null) {
                    layouts.put(id, before.get(id));
                    custom.add(id);
                }
                continue;
            }
            MenuLayout layout = new MenuLayout(id);
            layout.load(yml, null);
            layouts.put(id, layout);
            custom.add(id);
            if (completeFile(layout, null)) save(id);
        }
    }

    public static boolean validName(String id) {
        return id != null && NAME.matcher(id).matches();
    }

    /** "vip-shop" -> "Vip shop" (title of a new menu). */
    public static String prettyName(String id) {
        String p = id.replace('-', ' ');
        return p.isEmpty() ? p : Character.toUpperCase(p.charAt(0)) + p.substring(1);
    }

    /** True when menus/&lt;id&gt;.yml exists (also a file that couldn't be loaded). */
    public boolean fileExists(String id) {
        return new File(dir, id + ".yml").exists();
    }

    public boolean isCustom(String id) {
        return id != null && custom.contains(id);
    }

    /** True for the menus of the plugin and the ones made with the chest builder. */
    public boolean exists(String id) {
        return id != null && (IDS.contains(id) || custom.contains(id));
    }

    public List<String> customIds() {
        return new java.util.ArrayList<>(custom);
    }

    /** Every menu: the ones of the plugin first. */
    public List<String> allIds() {
        List<String> all = new java.util.ArrayList<>(IDS);
        all.addAll(custom);
        return all;
    }

    /** Adds a menu made with the chest builder and writes its file. */
    public void addCustom(MenuLayout layout) {
        layouts.put(layout.id(), layout);
        custom.add(layout.id());
        save(layout.id());
    }

    /**
     * Copies menus/&lt;id&gt;.yml to backups/ before it is replaced or deleted.
     * @return the name of the copy (backups/...), or null when there was no file
     */
    public String backup(String id) {
        File f = new File(dir, id + ".yml");
        if (!f.exists()) return null;
        File backups = new File(plugin.getDataFolder(), "backups");
        if (!backups.exists() && !backups.mkdirs()) return null;
        String name = "menu-" + id + "-" + new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date()) + ".yml";
        try {
            java.nio.file.Files.copy(f.toPath(), new File(backups, name).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return name;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not copy menus/" + id + ".yml to backups/: " + e.getMessage());
            return null;
        }
    }

    /** Deletes a menu made with the chest builder (a copy goes in backups/). @return the copy, or null */
    public String removeCustom(String id) {
        if (!isCustom(id)) return null;
        closeEditors(id);
        changed(id);
        String copy = backup(id);
        File f = new File(dir, id + ".yml");
        if (copy == null && f.exists()) return null; // never delete without a copy
        if (f.exists() && !f.delete()) {
            plugin.getLogger().warning("Could not delete menus/" + id + ".yml");
            return null;
        }
        custom.remove(id);
        layouts.remove(id);
        return copy == null ? "-" : copy;
    }

    /**
     * Makes every item editable in the file: buttons get material / name / lore from the defaults when missing,
     * items saved as raw data by older versions are rewritten as material / name / lore / glow / actions.
     */
    private boolean completeFile(MenuLayout layout, YamlConfiguration def) {
        boolean changed = !layout.hadOpenCommandsKey() || !layout.hadEnabledKey(); // write the new options
        if (def != null) {
            MenuLayout d = new MenuLayout(layout.id());
            d.load(def, null);
            for (java.util.Map.Entry<String, MenuLayout.Element> en : layout.elements().entrySet()) {
                MenuLayout.Element e = en.getValue();
                MenuLayout.Element de = d.elements().get(en.getKey());
                if (de == null) continue;
                if (e.material == null && de.material != null) { e.material = de.material; changed = true; }
                if (e.name == null && de.name != null) { e.name = de.name; changed = true; }
                if (e.lore == null && de.lore != null) { e.lore = new java.util.ArrayList<>(de.lore); changed = true; }
                if (!e.hasAdminOnlyKey) { e.adminOnly = de.adminOnly; e.hasAdminOnlyKey = true; changed = true; }
            }
        }
        if (def != null && def.isConfigurationSection("texts")) {
            org.bukkit.configuration.ConfigurationSection ts = def.getConfigurationSection("texts");
            for (String k : ts.getKeys(true)) {
                if (ts.isConfigurationSection(k) || layout.texts().containsKey(k)) continue;
                layout.texts().put(k, ts.get(k));
                changed = true;
            }
        }
        for (int i = 0; i < layout.decorations().size(); i++) {
            MenuLayout.Decoration old = layout.decorations().get(i);
            // items with enchantments, colors, NBT... stay complete; the others are written in the readable form
            if (old.item == null || ItemCodec.needsFull(old.item)) continue;
            MenuLayout.Decoration readable = LayoutMenu.describe(old.item);
            readable.slots = old.slots;
            readable.actions = old.actions;
            readable.adminOnly = old.adminOnly;
            readable.permission = old.permission;
            readable.update = old.update;
            layout.decorations().set(i, readable);
            changed = true;
        }
        return changed;
    }

    /**
     * 1.0.22: the default border is red (the plugin color) instead of gray. A border that is still the old
     * default one (gray, same slots, no name, lore or actions) gets the new color; changed borders are not touched.
     */
    private static boolean borderColor(YamlConfiguration yml, YamlConfiguration def) {
        if (def == null || !yml.isList("decorations") || !def.isList("decorations")) return false;
        List<Map<?, ?>> mine = yml.getMapList("decorations");
        List<Map<?, ?>> fresh = def.getMapList("decorations");
        boolean changed = false;
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Map<?, ?> d : mine) {
            Map<String, Object> copy = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> e : d.entrySet()) copy.put(String.valueOf(e.getKey()), e.getValue());
            if ("GRAY_STAINED_GLASS_PANE".equals(String.valueOf(d.get("material"))) && plain(d)) {
                String slots = MenuLayout.formatSlots(MenuLayout.parseSlots(d.get("slots")));
                for (Map<?, ?> f : fresh) {
                    if (!"RED_STAINED_GLASS_PANE".equals(String.valueOf(f.get("material")))) continue;
                    if (!slots.equals(MenuLayout.formatSlots(MenuLayout.parseSlots(f.get("slots"))))) continue;
                    copy.put("material", "RED_STAINED_GLASS_PANE");
                    changed = true;
                    break;
                }
            }
            out.add(copy);
        }
        if (changed) yml.set("decorations", out);
        return changed;
    }

    private static boolean plain(Map<?, ?> d) {
        Object name = d.get("name");
        Object lore = d.get("lore");
        Object actions = d.get("actions");
        return (name == null || String.valueOf(name).trim().isEmpty())
                && (lore == null || (lore instanceof List<?> && ((List<?>) lore).isEmpty()))
                && (actions == null || (actions instanceof List<?> && ((List<?>) actions).isEmpty()))
                && !Boolean.TRUE.equals(d.get("admin-only")) && !Boolean.TRUE.equals(d.get("glow"))
                && d.get("item") == null;
    }

    private YamlConfiguration defaults(String id) {
        try (InputStream in = plugin.getResource("menus/" + id + ".yml")) {
            if (in == null) return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    public MenuLayout get(String id) {
        MenuLayout l = layouts.get(id);
        if (l == null) {
            l = new MenuLayout(id);
            layouts.put(id, l);
        }
        return l;
    }

    /** @return false when the file was not written (deleted menu, file with a mistake, disk error) */
    public boolean save(String id) {
        if (!exists(id)) return false; // deleted meanwhile (e.g. by another admin while the editor was open)
        if (dev.tmdevelopment.flarereferrals.util.SafeYaml.broken("menus/" + id + ".yml")) {
            plugin.getLogger().warning("menus/" + id + ".yml was not saved because it has a mistake: fix it and use /referrals reload.");
            return false;
        }
        MenuLayout layout = get(id);
        YamlConfiguration yml = new YamlConfiguration();
        layout.save(yml);
        if (!isCustom(id)) {
            // the short explanations above the options stay in the file (Minecraft 1.18.1+)
            YamlConfiguration def = defaults(id);
            if (def != null) {
                for (String key : def.getKeys(true)) {
                    if (!key.equals("enabled") && yml.isSet(key)) dev.tmdevelopment.flarereferrals.util.Comments.copy(yml, def, key);
                }
            }
        }
        String header = dev.tmdevelopment.flarereferrals.util.Headers.of(plugin, isCustom(id) ? CUSTOM_HEADER : "menus/" + id + ".yml");
        try {
            dev.tmdevelopment.flarereferrals.util.J8.write(new File(dir, id + ".yml").toPath(), header + yml.saveToString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save menus/" + id + ".yml: " + e.getMessage());
            return false;
        }
        return true;
    }

    /** Restores the default layout of a menu (old file kept as .bak). */
    public void reset(String id) {
        closeEditors(id);
        changed(id);
        File f = new File(dir, id + ".yml");
        dev.tmdevelopment.flarereferrals.util.SafeYaml.forget("menus/" + id + ".yml");
        if (f.exists()) {
            File bak = new File(dir, id + ".yml.bak");
            try {
                java.nio.file.Files.copy(f.toPath(), bak.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
        }
        plugin.saveResource("menus/" + id + ".yml", true);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        MenuLayout layout = new MenuLayout(id);
        layout.load(yml, defaults(id));
        layouts.put(id, layout);
    }
}
