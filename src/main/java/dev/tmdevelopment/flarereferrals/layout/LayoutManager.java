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

    private final FlareReferrals plugin;
    private final File dir;
    private final Map<String, MenuLayout> layouts = new LinkedHashMap<>();

    public LayoutManager(FlareReferrals plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "menus");
    }

    public void load() {
        if (!dir.exists() && !dir.mkdirs()) plugin.getLogger().warning("Could not create the menus folder");
        layouts.clear();
        for (String id : IDS) {
            File f = new File(dir, id + ".yml");
            if (!f.exists()) plugin.saveResource("menus/" + id + ".yml", false);
            dev.tmdevelopment.flarereferrals.util.Headers.sync(plugin, f, "menus/" + id + ".yml");
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
            YamlConfiguration def = defaults(id);
            // old command name first, then old default texts -> new ones (edited texts are kept)
            boolean updated = dev.tmdevelopment.flarereferrals.util.TextUpdates.renameCommand(yml);
            updated |= dev.tmdevelopment.flarereferrals.util.TextUpdates.apply(plugin, yml, def, "menus/" + id + ".yml");
            MenuLayout layout = new MenuLayout(id);
            layout.load(yml, def);
            layouts.put(id, layout);
            boolean completed = completeFile(layout, def);
            if (completed || updated) {
                save(id);
                if (completed) plugin.getLogger().info("menus/" + id + ".yml: added material / name / lore of every item so you can edit them.");
                else plugin.getLogger().info("menus/" + id + ".yml: texts you never edited updated to the new version.");
            }
        }
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
            if (old.item == null) continue;
            MenuLayout.Decoration readable = LayoutMenu.describe(old.item);
            readable.slots = old.slots;
            readable.actions = old.actions;
            layout.decorations().set(i, readable);
            changed = true;
        }
        return changed;
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

    public void save(String id) {
        MenuLayout layout = get(id);
        YamlConfiguration yml = new YamlConfiguration();
        layout.save(yml);
        String header = dev.tmdevelopment.flarereferrals.util.Headers.of(plugin, "menus/" + id + ".yml");
        try {
            dev.tmdevelopment.flarereferrals.util.J8.write(new File(dir, id + ".yml").toPath(), header + yml.saveToString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save menus/" + id + ".yml: " + e.getMessage());
        }
    }

    /** Restores the default layout of a menu (old file kept as .bak). */
    public void reset(String id) {
        File f = new File(dir, id + ".yml");
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
