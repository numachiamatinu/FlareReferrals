package dev.tmdevelopment.flarereferrals.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the files of the plugin. A file with a mistake (a missing quote, a tab, a wrong indent...) is never
 * overwritten: the plugin says the file and the line of the mistake, works with the default (or the last
 * working) values and leaves the file as it is until it is fixed.
 */
public final class SafeYaml {

    private static final Pattern LINE = Pattern.compile("line (\\d+), column (\\d+)");
    /** File (as shown, e.g. "menus/main.yml") -> what is wrong. Filled again at every load / reload. */
    private static final Map<String, String[]> PROBLEMS = new LinkedHashMap<>();

    private SafeYaml() {
    }

    /** Called before a reload: the files are checked again. */
    public static synchronized void clear() {
        PROBLEMS.clear();
    }

    /**
     * @param shown name of the file in messages, e.g. "menus/main.yml"
     * @return the file, an empty configuration when it doesn't exist, or null when it has a mistake
     */
    public static YamlConfiguration load(Plugin plugin, File file, String shown) {
        YamlConfiguration y = new YamlConfiguration();
        if (!file.exists()) return y;
        try {
            y.loadFromString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            synchronized (SafeYaml.class) {
                PROBLEMS.remove(shown);
            }
            return y;
        } catch (Exception e) {
            String[] p = describe(e.getMessage());
            synchronized (SafeYaml.class) {
                PROBLEMS.put(shown, p);
            }
            plugin.getLogger().severe(shown + " has a mistake around line " + p[0] + ", column " + p[1] + ": " + p[2]
                    + ". The file was NOT changed: FlareReferrals uses the default (or the last working) values until you fix it,"
                    + " then use /referrals reload.");
            return null;
        }
    }

    /** The file was replaced by a working one (e.g. a menu reset to the default): it can be saved again. */
    public static synchronized void forget(String shown) {
        PROBLEMS.remove(shown);
    }

    /** True when the file had a mistake at the last load: it must not be saved. */
    public static synchronized boolean broken(String shown) {
        return PROBLEMS.containsKey(shown);
    }

    /** [file, line, column, problem] of every file with a mistake. */
    public static synchronized List<String[]> problems() {
        List<String[]> out = new ArrayList<>();
        for (Map.Entry<String, String[]> e : PROBLEMS.entrySet()) {
            out.add(new String[]{e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]});
        }
        return out;
    }

    /** [line, column, problem] from the message of SnakeYAML. */
    static String[] describe(String message) {
        if (message == null) return new String[]{"?", "?", "the file can't be read"};
        String[] lines = message.replace("\r", "").split("\n");
        String line = "?", column = "?", problem = null;
        for (int i = lines.length - 1; i >= 0; i--) {
            Matcher m = LINE.matcher(lines[i]);
            if (!m.find()) continue;
            line = m.group(1);
            column = m.group(2);
            // the reason is the closest line above that is not indented ("mapping values are not allowed here")
            for (int k = i - 1; k >= 0; k--) {
                String t = lines[k];
                if (!t.isEmpty() && !Character.isWhitespace(t.charAt(0))) {
                    problem = t.trim();
                    break;
                }
            }
            break;
        }
        if (problem == null) problem = lines[0].trim();
        if (problem.endsWith(":")) problem = problem.substring(0, problem.length() - 1);
        // the most common mistake has a clearer name
        if (problem.contains("'\\t'") || problem.toLowerCase(java.util.Locale.ROOT).contains("tab")) {
            problem = "a TAB is used: YAML wants spaces";
        }
        return new String[]{line, column, problem};
    }
}
