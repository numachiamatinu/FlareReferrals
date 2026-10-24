package dev.tmdevelopment.flarereferrals.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Default texts changed in a new version: a server file that still has the OLD default text
 * gets the new one. Texts edited by the server owner are never touched. List in text-updates.yml (jar).
 */
public final class TextUpdates {

    private TextUpdates() {
    }

    /** @return true when something was changed (the file should be saved). */
    public static boolean apply(JavaPlugin plugin, ConfigurationSection file, ConfigurationSection jarDefaults, String resource) {
        if (file == null || jarDefaults == null) return false;
        YamlConfiguration list;
        try (InputStream in = plugin.getResource("text-updates.yml")) {
            if (in == null) return false;
            list = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
        boolean changed = false;
        for (Map<?, ?> entry : list.getMapList(resource.replace('/', '_').replace('.', '_'))) {
            Object pathObj = entry.get("path");
            if (pathObj == null) continue;
            String path = pathObj.toString();
            Object now = file.get(path);
            Object fresh = jarDefaults.get(path);
            if (now == null || fresh == null) continue;
            if (same(now, entry.get("old"))) {
                file.set(path, fresh);
                changed = true;
            }
        }
        return changed;
    }

    private static final java.util.regex.Pattern OLD_CMD = java.util.regex.Pattern.compile("/freferrals\\b");
    private static final java.util.regex.Pattern OLD_ACTION = java.util.regex.Pattern.compile("(\\[player\\]\\s*)freferrals\\b");

    /** The main command is /referrals again (1.0.18): "/freferrals" written in the server files by 1.0.17 is updated. */
    public static boolean renameCommand(ConfigurationSection sec) {
        if (sec == null) return false;
        boolean changed = false;
        for (String key : sec.getKeys(true)) {
            if (sec.isConfigurationSection(key)) continue;
            Object v = sec.get(key);
            if (v == null) continue;
            Object n = renameObj(v);
            if (!n.equals(v)) {
                sec.set(key, n);
                changed = true;
            }
        }
        return changed;
    }

    private static Object renameObj(Object v) {
        if (v instanceof String) return rename((String) v);
        if (v instanceof List<?>) {
            List<Object> out = new ArrayList<>();
            for (Object x : (List<?>) v) out.add(renameObj(x));
            return out;
        }
        if (v instanceof Map<?, ?>) {
            Map<Object, Object> out = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) out.put(e.getKey(), renameObj(e.getValue()));
            return out;
        }
        return v;
    }

    private static String rename(String s) {
        String r = OLD_CMD.matcher(s).replaceAll("/referrals");
        return OLD_ACTION.matcher(r).replaceAll("$1referrals");
    }

    private static boolean same(Object a, Object b) {
        return normalize(a).equals(normalize(b));
    }

    private static Object normalize(Object o) {
        if (o instanceof List<?>) {
            List<?> l = (List<?>) o;
            List<String> out = new ArrayList<>();
            for (Object x : l) out.add(String.valueOf(x));
            return out;
        }
        return String.valueOf(o);
    }
}
