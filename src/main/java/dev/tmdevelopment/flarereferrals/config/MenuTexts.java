package dev.tmdevelopment.flarereferrals.config;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.util.Headers;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Texts of the menus. They live in the menu files, not in lang/en.yml (which is only for chat):
 *   player menus  -> menus/main.yml, rewards.yml, top.yml, preview.yml   (section "texts")
 *   admin menus   -> menus/admin.yml
 * Code keeps asking for "gui.xxx" keys; this class finds them in the right file.
 */
public final class MenuTexts {

    private final FlareReferrals plugin;
    private final Map<String, YamlConfiguration> jarCache = new HashMap<>();
    private YamlConfiguration admin;

    public MenuTexts(FlareReferrals plugin) {
        this.plugin = plugin;
    }

    public void load() {
        jarCache.clear();
        File dir = new File(plugin.getDataFolder(), "menus");
        if (!dir.exists() && !dir.mkdirs()) plugin.getLogger().warning("Could not create the menus folder");
        File f = new File(dir, "admin.yml");
        if (!f.exists()) plugin.saveResource("menus/admin.yml", false);
        Headers.sync(plugin, f, "menus/admin.yml");
        admin = YamlConfiguration.loadConfiguration(f);
        YamlConfiguration def = jar("menus/admin.yml");
        if (def == null) return;
        // old default texts -> new ones (edited texts are kept)
        boolean changed = dev.tmdevelopment.flarereferrals.util.TextUpdates.renameCommand(admin);
        changed |= dev.tmdevelopment.flarereferrals.util.TextUpdates.apply(plugin, admin, def, "menus/admin.yml");
        admin.setDefaults(def);
        // texts added by newer versions are written into the file, edited ones are kept
        for (String key : def.getKeys(true)) {
            if (def.isConfigurationSection(key) || admin.isSet(key)) continue;
            dev.tmdevelopment.flarereferrals.util.Comments.add(admin, def, key);
            changed = true;
        }
        if (changed) {
            try {
                admin.save(f);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not update menus/admin.yml: " + e.getMessage());
            }
        }
    }

    /** Value (String or List) of a "gui.xxx" key, or null. */
    public Object get(String guiPath) {
        String rest = guiPath.startsWith("gui.") ? guiPath.substring(4) : guiPath;
        int dot = rest.indexOf('.');
        String first = dot < 0 ? rest : rest.substring(0, dot);
        String tail = dot < 0 ? "" : rest.substring(dot + 1);
        String menu = ("player".equals(first) ? "main" : "referral-rewards".equals(first) ? "rewards" : "top".equals(first) ? "top" : "preview".equals(first) ? "preview" : "collect".equals(first) ? "collect" : null);
        if (menu != null && !tail.isEmpty()) {
            if (tail.equals("title")) {
                // the title is the "title" option of the menu file; empty = original title
                YamlConfiguration d = jar("menus/" + menu + ".yml");
                return d == null ? null : d.getString("title");
            }
            Object v = plugin.layouts() == null ? null : plugin.layouts().get(menu).texts().get(tail);
            if (v != null) return v;
            YamlConfiguration d = jar("menus/" + menu + ".yml");
            if (d == null) return null;
            if (d.isSet("texts." + tail)) return d.get("texts." + tail);
            // texts of buttons are in "elements" (name / lore)
            String el = tail.startsWith("entry.") ? "entries" + tail.substring(5) : tail;
            return d.get("elements." + el);
        }
        return admin == null ? null : admin.get(rest);
    }

    private YamlConfiguration jar(String resource) {
        return jarCache.computeIfAbsent(resource, r -> {
            try (InputStream in = plugin.getResource(r)) {
                return in == null ? null : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (Exception e) {
                return null;
            }
        });
    }
}
