package dev.tmdevelopment.flarereferrals.update;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import org.bukkit.Bukkit;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the latest public release from GitHub (read only, no key, nothing about the server is sent).
 * Stable versions only see stable releases; a beta also sees newer betas.
 * The answer is kept for 6 hours. Turned off with update-check: false in config.yml.
 */
public final class UpdateChecker {

    public static final String REPO = "numachiamatinu/FlareReferrals";
    private static final long KEEP_MS = 6L * 60 * 60 * 1000;
    private static final long RETRY_MS = 10L * 60 * 1000;
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");

    public enum Result { LATEST, UPDATE, FAILED }

    private final FlareReferrals plugin;
    private final String current;
    private volatile Result result;
    private volatile String latest;
    private volatile String latestTag; // the tag as written on GitHub (e.g. v1.0.22), used for the link
    private volatile long checkedAt;
    private boolean running;
    private final List<Runnable> waiting = new ArrayList<>();

    public UpdateChecker(FlareReferrals plugin) {
        this.plugin = plugin;
        this.current = strip(plugin.getDescription().getVersion());
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("update-check", true);
    }

    public String current() { return current; }
    public String latest() { return latest; }
    public Result result() { return result; }

    public String downloadUrl() {
        return latestTag == null ? "https://github.com/" + REPO + "/releases"
                : "https://github.com/" + REPO + "/releases/tag/" + latestTag;
    }

    /** True when there is an answer younger than 6 hours (a failed check is retried after 10 minutes). */
    public boolean fresh() {
        if (result == null) return false;
        long age = System.currentTimeMillis() - checkedAt;
        return age < (result == Result.FAILED ? RETRY_MS : KEEP_MS);
    }

    /** At startup: one line in the console when a new version is out. */
    public void startup() {
        if (!enabled()) return;
        check(() -> {
            if (result == Result.UPDATE) {
                plugin.getLogger().info("New version " + latest + " is out (you have " + current + "): " + downloadUrl());
            }
        });
    }

    /**
     * Uses the saved answer when it is fresh, otherwise asks GitHub in the background.
     * done runs on the main thread.
     */
    public void check(Runnable done) {
        if (fresh()) {
            if (done != null) done.run();
            return;
        }
        synchronized (this) {
            if (done != null) waiting.add(done);
            if (running) return;
            running = true;
        }
        Thread t = new Thread(() -> {
            String found = null;
            boolean ok;
            try {
                found = fetch();
                ok = found != null;
            } catch (Throwable ex) {
                ok = false;
            }
            final String tag = found;
            final boolean success = ok;
            Runnable apply = () -> {
                List<Runnable> list;
                synchronized (this) {
                    if (success) {
                        latestTag = tag;
                        latest = strip(tag);
                        result = newer(latest, current) ? Result.UPDATE : Result.LATEST;
                        checkedAt = System.currentTimeMillis();
                    } else {
                        result = Result.FAILED;
                        checkedAt = System.currentTimeMillis();
                    }
                    running = false;
                    list = new ArrayList<>(waiting);
                    waiting.clear();
                }
                for (Runnable r : list) {
                    try { r.run(); } catch (RuntimeException ignored) { }
                }
            };
            try {
                if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, apply);
            } catch (RuntimeException ignored) {
                // plugin turned off while checking
            }
        }, "FlareReferrals-UpdateCheck");
        t.setDaemon(true);
        t.start();
    }

    /** Tag of the newest release this server should see, as written on GitHub. */
    private String fetch() throws Exception {
        boolean beta = current.indexOf('-') >= 0;
        String url = "https://api.github.com/repos/" + REPO + (beta ? "/releases?per_page=1" : "/releases/latest");
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(5000);
        con.setReadTimeout(10000);
        con.setRequestProperty("Accept", "application/vnd.github+json");
        con.setRequestProperty("User-Agent", "FlareReferrals/" + current);
        try {
            if (con.getResponseCode() != 200) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = con.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0 && out.size() < 512 * 1024) out.write(buf, 0, n);
            }
            Matcher m = TAG.matcher(new String(out.toByteArray(), StandardCharsets.UTF_8));
            return m.find() ? m.group(1).trim() : null;
        } finally {
            con.disconnect();
        }
    }

    private static String strip(String v) {
        v = v == null ? "" : v.trim();
        return v.startsWith("v") || v.startsWith("V") ? v.substring(1) : v;
    }

    /** 1.0.22 > 1.0.21, 1.1.0 > 1.1.0-beta2 > 1.1.0-beta1. */
    static boolean newer(String a, String b) {
        return compare(a, b) > 0;
    }

    static int compare(String a, String b) {
        String[] pa = split(a), pb = split(b);
        int[] na = numbers(pa[0]), nb = numbers(pb[0]);
        for (int i = 0; i < Math.max(na.length, nb.length); i++) {
            int x = i < na.length ? na[i] : 0, y = i < nb.length ? nb[i] : 0;
            if (x != y) return x < y ? -1 : 1;
        }
        if (pa[1].isEmpty() && pb[1].isEmpty()) return 0;
        if (pa[1].isEmpty()) return 1;   // stable after its betas
        if (pb[1].isEmpty()) return -1;
        int x = trailing(pa[1]), y = trailing(pb[1]);
        return Integer.compare(x, y);
    }

    private static String[] split(String v) {
        int dash = v.indexOf('-');
        return dash < 0 ? new String[]{v, ""} : new String[]{v.substring(0, dash), v.substring(dash + 1)};
    }

    private static int[] numbers(String s) {
        String[] parts = s.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { out[i] = Integer.parseInt(parts[i].replaceAll("\\D", "")); } catch (NumberFormatException e) { out[i] = 0; }
        }
        return out;
    }

    private static int trailing(String s) {
        Matcher m = Pattern.compile("(\\d+)$").matcher(s);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }
}
