package dev.tmdevelopment.flarereferrals.config;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Every message / menu text lives in lang/&lt;language&gt;.yml.
 * A message can be a single line or a list of lines. Set it to "" or [] to disable it.
 */
public final class Lang {

    private static final String[] BUNDLED = {"en"};

    private final FlareReferrals plugin;
    private YamlConfiguration cfg = new YamlConfiguration();
    /** True once a lang file was read without mistakes (a later file with a mistake keeps those texts). */
    private boolean loadedOnce;

    public Lang(FlareReferrals plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File dir = new File(plugin.getDataFolder(), "lang");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create lang folder");
        }
        for (String b : BUNDLED) {
            if (!new File(dir, b + ".yml").exists()) plugin.saveResource("lang/" + b + ".yml", false);
        }
        String language = plugin.getConfig().getString("language", "en");
        File file = new File(dir, language + ".yml");
        if (!file.exists()) {
            plugin.getLogger().warning("Language file lang/" + language + ".yml not found, using en.yml");
            file = new File(dir, "en.yml");
        }
        dev.tmdevelopment.flarereferrals.util.Headers.sync(plugin, file, "lang/en.yml");
        YamlConfiguration loaded = dev.tmdevelopment.flarereferrals.util.SafeYaml.load(plugin, file, "lang/" + file.getName());
        // a file with a mistake is never saved: the last working texts (or the defaults) are used
        if (loaded == null && loadedOnce) return;
        boolean broken = loaded == null;
        if (!broken) loadedOnce = true;
        cfg = broken ? new YamlConfiguration() : loaded;
        try (InputStream in = plugin.getResource("lang/en.yml")) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                cfg.setDefaults(defaults);
                if (broken) return;
                boolean changed = false;
                // menu texts changed in a newer version: back up the file, then refresh menu + prompt texts.
                // This replaces edited texts too: changed default texts go in text-updates.yml instead.
                // Test builds of 1.0.21 used 13-15, so a future bump must be 16 or higher.
                int fileVersion = cfg.isSet("lang-version") ? cfg.getInt("lang-version") : 1;
                int newVersion = defaults.getInt("lang-version");
                if (fileVersion < newVersion) {
                    File backup = new File(file.getParentFile(), file.getName() + ".v" + fileVersion + ".bak");
                    java.nio.file.Files.copy(file.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    cfg.set("gui", null);
                    cfg.set("input", null);
                    cfg.set("help", null);
                    cfg.set("info", null);
                    cfg.set("usage", null);
                    cfg.set("lang-version", newVersion);
                    changed = true;
                    plugin.getLogger().info("Menu texts updated to the new version (old file saved as " + backup.getName() + ").");
                }
                // menu texts moved to the menu files: lang/en.yml keeps only the chat
                if (cfg.isSet("gui")) {
                    File backup = new File(file.getParentFile(), file.getName() + ".before-menus.bak");
                    if (!backup.exists()) {
                        java.nio.file.Files.copy(file.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    cfg.set("gui", null);
                    changed = true;
                    plugin.getLogger().info("Menu texts moved from lang/" + file.getName() + " to the files in menus/ (old file: " + backup.getName() + ").");
                }
                // old default messages -> new ones (edited messages are kept)
                if (dev.tmdevelopment.flarereferrals.util.TextUpdates.renameCommand(cfg)) changed = true;
                if (dev.tmdevelopment.flarereferrals.util.TextUpdates.apply(plugin, cfg, defaults, "lang/en.yml")) changed = true;
                // write messages added by newer versions into the file, keeping the edited ones
                for (String key : defaults.getKeys(true)) {
                    if (defaults.isConfigurationSection(key)) continue;
                    if (!cfg.isSet(key)) {
                        dev.tmdevelopment.flarereferrals.util.Comments.add(cfg, defaults, key);
                        changed = true;
                    }
                }
                if (changed) {
                    cfg.save(file);
                    plugin.getLogger().info("Added new messages to lang/" + file.getName());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load default language: " + e.getMessage());
        }
    }

    public String prefix() {
        return cfg.getString("prefix", "");
    }

    /** Raw single string (first line if the key is a list). */
    /** Chat texts come from lang/en.yml, menu texts ("gui.*") from the menu files. */
    private Object value(String path) {
        if (path.startsWith("gui.") && plugin.menuTexts() != null) return plugin.menuTexts().get(path);
        return cfg.get(path);
    }

    private String pre(String s) {
        return s.replace("%prefix%", prefix()).replace("{prefix}", prefix());
    }

    public String raw(String path) {
        Object v = value(path);
        if (v instanceof java.util.List<?>) { java.util.List<?> l = (java.util.List<?>) v; return l.isEmpty() ? "" : pre(String.valueOf(l.get(0))); }
        return v == null ? "<red>Missing text: " + path : pre(String.valueOf(v));
    }

    /** Raw lines. A missing key returns a single "missing" line, an empty string returns no lines. */
    public List<String> lines(String path) {
        List<String> out = new ArrayList<>();
        Object v = value(path);
        if (v instanceof java.util.List<?>) {
            java.util.List<?> l = (java.util.List<?>) v;
            for (Object o : l) out.add(pre(String.valueOf(o)));
        } else if (v == null) {
            out.add("<red>Missing text: " + path);
        } else if (!String.valueOf(v).isEmpty()) {
            out.add(pre(String.valueOf(v)));
        }
        return out;
    }

    public void send(CommandSender to, String path, String... replacements) {
        Player p = to instanceof Player ? (Player) to : null;
        for (String line : lines(path)) {
            dev.tmdevelopment.flarereferrals.util.Platform.send(to, Text.parse(Text.replace(line, replacements), p));
        }
    }

    /** Sends already-resolved raw lines (used for reward messages stored in rewards.yml). */
    public void sendRaw(CommandSender to, List<String> lines, String... replacements) {
        Player p = to instanceof Player ? (Player) to : null;
        for (String line : lines) {
            if (line == null || line.isEmpty()) continue;
            dev.tmdevelopment.flarereferrals.util.Platform.send(to, Text.parse(Text.replace(line.replace("%prefix%", prefix()).replace("{prefix}", prefix()), replacements), p));
        }
    }

    /** Item-name style component (no italic). */
    public Component item(String path, Player p, String... replacements) {
        return Text.item(Text.replace(raw(path), replacements), p);
    }

    /** Item-lore style components (no italic). */
    public List<Component> lore(String path, Player p, String... replacements) {
        return Text.items(Text.replace(lines(path), replacements), p);
    }

    public Component title(String path, Player p, String... replacements) {
        return Text.parse(Text.replace(raw(path), replacements), p);
    }
}
