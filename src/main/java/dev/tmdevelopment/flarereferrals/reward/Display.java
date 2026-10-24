package dev.tmdevelopment.flarereferrals.reward;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * On-screen effects shown to the player who receives a reward set: title + subtitle, action bar, boss bar.
 * An empty text means "off".
 */
public final class Display {

    public static final String[] BOSSBAR_COLORS = {"PINK", "BLUE", "RED", "GREEN", "YELLOW", "PURPLE", "WHITE"};
    public static final String[] BOSSBAR_STYLES = {"PROGRESS", "NOTCHED_6", "NOTCHED_10", "NOTCHED_12", "NOTCHED_20"};

    private String title = "";
    private String subtitle = "";
    private int fadeIn = 10;
    private int stay = 60;
    private int fadeOut = 20;

    private String actionbar = "";

    private String bossbar = "";
    private String bossbarColor = "PINK";
    private String bossbarStyle = "PROGRESS";
    private int bossbarSeconds = 5;

    public void load(ConfigurationSection s) {
        if (s == null) return;
        ConfigurationSection t = s.getConfigurationSection("title");
        if (t != null) {
            title = t.getString("title", "");
            subtitle = t.getString("subtitle", "");
            fadeIn = Math.max(0, t.getInt("fade-in", 10));
            stay = Math.max(1, t.getInt("stay", 60));
            fadeOut = Math.max(0, t.getInt("fade-out", 20));
        }
        actionbar = s.getString("actionbar", "");
        ConfigurationSection b = s.getConfigurationSection("bossbar");
        if (b != null) {
            bossbar = b.getString("text", "");
            bossbarColor = valid(b.getString("color", "PINK"), BOSSBAR_COLORS, "PINK");
            bossbarStyle = valid(b.getString("style", "PROGRESS"), BOSSBAR_STYLES, "PROGRESS");
            bossbarSeconds = Math.max(1, Math.min(60, b.getInt("seconds", 5)));
        }
    }

    public void save(ConfigurationSection s) {
        s.set("title.title", title);
        s.set("title.subtitle", subtitle);
        s.set("title.fade-in", fadeIn);
        s.set("title.stay", stay);
        s.set("title.fade-out", fadeOut);
        s.set("actionbar", actionbar);
        s.set("bossbar.text", bossbar);
        s.set("bossbar.color", bossbarColor);
        s.set("bossbar.style", bossbarStyle);
        s.set("bossbar.seconds", bossbarSeconds);
    }

    private static String valid(String v, String[] options, String def) {
        if (v == null) return def;
        String up = v.toUpperCase(Locale.ROOT);
        for (String o : options) if (o.equals(up)) return o;
        return def;
    }

    public static String next(String current, String[] options, boolean backwards) {
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(current)) {
                int n = backwards ? (i - 1 + options.length) % options.length : (i + 1) % options.length;
                return options[n];
            }
        }
        return options[0];
    }

    public boolean hasTitle() { return !title.isEmpty() || !subtitle.isEmpty(); }
    public boolean hasActionbar() { return !actionbar.isEmpty(); }
    public boolean hasBossbar() { return !bossbar.isEmpty(); }
    public boolean any() { return hasTitle() || hasActionbar() || hasBossbar(); }

    public String title() { return title; }
    public void title(String v) { title = v == null ? "" : v; }
    public String subtitle() { return subtitle; }
    public void subtitle(String v) { subtitle = v == null ? "" : v; }
    public int fadeIn() { return fadeIn; }
    public int stay() { return stay; }
    public int fadeOut() { return fadeOut; }
    public void times(int in, int st, int out) {
        fadeIn = Math.max(0, in);
        stay = Math.max(1, st);
        fadeOut = Math.max(0, out);
    }
    public String actionbar() { return actionbar; }
    public void actionbar(String v) { actionbar = v == null ? "" : v; }
    public String bossbar() { return bossbar; }
    public void bossbar(String v) { bossbar = v == null ? "" : v; }
    public String bossbarColor() { return bossbarColor; }
    public void bossbarColor(String v) { bossbarColor = valid(v, BOSSBAR_COLORS, "PINK"); }
    public String bossbarStyle() { return bossbarStyle; }
    public void bossbarStyle(String v) { bossbarStyle = valid(v, BOSSBAR_STYLES, "PROGRESS"); }
    public int bossbarSeconds() { return bossbarSeconds; }
    public void bossbarSeconds(int v) { bossbarSeconds = Math.max(1, Math.min(60, v)); }
}
