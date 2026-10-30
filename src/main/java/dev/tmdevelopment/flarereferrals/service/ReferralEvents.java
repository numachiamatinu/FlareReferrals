package dev.tmdevelopment.flarereferrals.service;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.util.Perms;
import dev.tmdevelopment.flarereferrals.util.Platform;
import dev.tmdevelopment.flarereferrals.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reward events: /referrals event start 2 1h = the rewards earned with an invite in the next hour are doubled.
 * Off until the admin sets events.enabled: true. The event survives restarts and, on a network, runs on every server.
 * A reward keeps the multiplier of the moment it was earned, also when it is collected later.
 */
public final class ReferralEvents {

    private static final Pattern TIME = Pattern.compile("(\\d+)\\s*([dhms])");

    private final FlareReferrals plugin;
    private final File file;
    private double multiplier = 1.0;
    private long start;
    private long end;
    private String by = "";
    private String reason = "";
    private BossBar bar;
    private BukkitTask ticker;
    private long lastRemind;
    private int seconds;

    public ReferralEvents(FlareReferrals plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/event.yml");
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    /** events.enabled in config.yml: without it nobody can start an event and nothing is shown. */
    public boolean enabled() {
        return cfg().getBoolean("events.enabled", false);
    }

    public boolean active() {
        return enabled() && multiplier > 1.0 && System.currentTimeMillis() < end;
    }

    /** Current multiplier (1 = no event). */
    public double multiplier() {
        return active() ? multiplier : 1.0;
    }

    public long millisLeft() {
        return active() ? Math.max(0, end - System.currentTimeMillis()) : 0;
    }

    public String reason() {
        return reason;
    }

    /** Can start and stop events: flarereferrals.event (admins have it). */
    public boolean canManage(CommandSender s) {
        return Perms.has(s, "event") || Perms.has(s, "admin");
    }

    /** /referrals event exists for this sender (players see it only when events are on). */
    public boolean visibleTo(CommandSender s) {
        return canManage(s) || enabled();
    }

    /**
     * Part added to the rewards of an invite: "|x2" when the invite was made during the event, "" otherwise.
     * An invite that counts later (referral.verify-after-minutes) keeps the event of the moment it was made.
     * @param type inviter, welcome or milestones (events.rewards in config.yml)
     * @param inviteTime when the player wrote /referrals &lt;name&gt;
     */
    public String bonus(String type, long inviteTime) {
        if (!enabled() || multiplier <= 1.0 || inviteTime < start || inviteTime >= end) return "";
        if (!cfg().getBoolean("events.rewards." + type, true)) return "";
        return "|x" + format(multiplier);
    }

    // ---------------------------------------------------------------- start / load

    /** At startup: the event saved before the restart (on a network: the one of the database). */
    public void load() {
        YamlConfiguration y = dev.tmdevelopment.flarereferrals.util.SafeYaml.load(plugin, file, "data/event.yml");
        if (y != null) apply(y.getString("event", ""), false);
        if (plugin.data().network()) {
            plugin.data().run(ops -> ops.meta("event"), v -> {
                if (v != null) apply(v, false);
            });
        }
    }

    /** /referrals reload: events.enabled or the look of the boss bar may have changed. */
    public void reload() {
        if (bar != null) {
            bar.removeAll();
            bar = null;
        }
        if (active()) startTicker();
        else stopTicker();
    }

    /** "2;start;end;by;reason" from the file, the database or another server. */
    private void apply(String data, boolean announce) {
        double m = 1.0;
        long s = 0, e = 0;
        String who = "", why = "";
        if (data != null && !data.isEmpty()) {
            String[] p = data.split(";", 5);
            try {
                m = Double.parseDouble(p[0]);
                s = Long.parseLong(p[1]);
                e = Long.parseLong(p[2]);
            } catch (RuntimeException ex) {
                m = 1.0;
            }
            who = p.length > 3 ? p[3] : "";
            why = p.length > 4 ? p[4] : "";
        }
        boolean wasActive = active();
        double before = multiplier;
        multiplier = m;
        start = s;
        end = e;
        by = who;
        reason = why;
        save();
        if (active()) {
            startTicker();
            if (announce) announceStart();
        } else {
            stopTicker();
            if (announce && wasActive) announceEnd(before);
        }
    }

    private String encode() {
        if (multiplier <= 1.0) return "";
        return format(multiplier) + ";" + start + ";" + end + ";" + by + ";" + reason.replace(";", ",");
    }

    private void save() {
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            YamlConfiguration y = new YamlConfiguration();
            y.set("event", encode());
            dev.tmdevelopment.flarereferrals.util.J8.write(file.toPath(),
                    "# Last reward event (/referrals event). Don't edit: use /referrals event start / stop.\n"
                            + "# It stays here after the end: invites made during it that count later (verify-after-minutes) keep the bonus.\n" + y.saveToString(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save data/event.yml: " + e.getMessage());
        }
    }

    /** Another server started or stopped an event. */
    public void remote(String data) {
        apply(data, true);
    }

    // ---------------------------------------------------------------- command

    /** /referrals event [start <multiplier> <time> [reason] | stop] */
    public void command(CommandSender s, String label, String[] args) {
        if (!visibleTo(s)) {
            plugin.lang().send(s, "general.unknown-command", "%label%", label);
            return;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (action.equals("start") || action.equals("stop")) {
            if (!canManage(s)) {
                plugin.lang().send(s, "general.no-permission");
                return;
            }
            if (!enabled()) {
                plugin.lang().send(s, "event.admin.disabled");
                return;
            }
        }
        if (action.equals("start")) {
            if (args.length < 4) {
                plugin.lang().send(s, "event.admin.usage", "%label%", label);
                return;
            }
            double max = Math.max(1.1, cfg().getDouble("events.max-multiplier", 10));
            double m = parseMultiplier(args[2]);
            if (m <= 1.0 || m > max) {
                plugin.lang().send(s, "event.admin.bad-multiplier", "%input%", Text.safe(args[2]), "%max%", format(max));
                return;
            }
            long millis = parseTime(args[3]);
            if (millis <= 0) {
                plugin.lang().send(s, "event.admin.bad-time", "%input%", Text.safe(args[3]));
                return;
            }
            if (active()) {
                plugin.lang().send(s, "event.admin.replaced", "%multiplier%", format(multiplier), "%time%", duration(millisLeft()));
            }
            StringBuilder why = new StringBuilder();
            for (int i = 4; i < args.length; i++) why.append(i > 4 ? " " : "").append(args[i]);
            long now = System.currentTimeMillis();
            String data = format(m) + ";" + now + ";" + (now + millis) + ";" + s.getName() + ";"
                    + cut(why.toString().replace(";", ","), 120);
            publish(data);
            apply(data, true);
            plugin.lang().send(s, "event.admin.started", "%multiplier%", format(m), "%time%", duration(millis));
            dev.tmdevelopment.flarereferrals.util.ReferralLog.log("EVENT", s.getName() + " started x" + format(m) + " for " + duration(millis)
                    + (why.length() > 0 ? " (" + why + ")" : ""));
            return;
        }
        if (action.equals("stop")) {
            if (!active()) {
                plugin.lang().send(s, "event.admin.not-running");
                return;
            }
            // the event ends now (invites made until now keep their bonus, also if they count later)
            String data = format(multiplier) + ";" + start + ";" + System.currentTimeMillis() + ";" + by + ";" + reason.replace(";", ",");
            publish(data);
            apply(data, true);
            plugin.lang().send(s, "event.admin.stopped");
            dev.tmdevelopment.flarereferrals.util.ReferralLog.log("EVENT", s.getName() + " stopped the event");
            return;
        }
        // status
        if (active()) {
            plugin.lang().send(s, "event.status", ph());
            if (!reason.isEmpty()) plugin.lang().send(s, "event.reason", ph());
        } else {
            plugin.lang().send(s, "event.none");
        }
        if (canManage(s)) plugin.lang().send(s, "event.admin.usage", "%label%", label);
    }

    /** Network: the event is saved in the database and the other servers are told. */
    private void publish(String data) {
        if (!plugin.data().network()) return;
        plugin.data().run(ops -> {
            ops.meta("event", data);
            ops.event("event", null, data);
            return Boolean.TRUE;
        }, null);
    }

    // ---------------------------------------------------------------- on screen

    private String[] ph() {
        return new String[]{"%multiplier%", format(multiplier), "%time%", duration(millisLeft()),
                "%reason%", reason, "%by%", by};
    }

    private void announceStart() {
        FileConfiguration c = cfg();
        String[] ph = ph();
        for (Player p : Bukkit.getOnlinePlayers()) {
            String[] mine = concat(ph, "%player%", p.getName());
            if (c.getBoolean("events.show.chat", true)) {
                plugin.lang().send(p, "event.started", mine);
                if (!reason.isEmpty()) plugin.lang().send(p, "event.reason", mine);
            }
            if (c.getBoolean("events.show.title", true)) {
                dev.tmdevelopment.flarereferrals.reward.Display d = new dev.tmdevelopment.flarereferrals.reward.Display();
                d.title(plugin.lang().raw("event.title"));
                d.subtitle(plugin.lang().raw("event.subtitle"));
                d.times(c.getInt("events.title.fade-in", 10), c.getInt("events.title.stay", 70), c.getInt("events.title.fade-out", 20));
                ScreenEffects.show(plugin, p, d, mine);
            }
            sound(p);
        }
        lastRemind = System.currentTimeMillis();
    }

    private void announceEnd(double was) {
        if (!cfg().getBoolean("events.show.chat", true)) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.lang().send(p, "event.ended", "%multiplier%", format(was), "%player%", p.getName());
        }
    }

    private void sound(Player p) {
        String key = cfg().getString("events.show.sound", "");
        if (key == null || key.trim().isEmpty() || !cfg().getBoolean("sounds.enabled", true)) return;
        try {
            p.playSound(p.getLocation(), key.toLowerCase(Locale.ROOT), 1f, 1f);
        } catch (Exception ignored) {
            // unknown sound: nothing
        }
    }

    /** A player joined: he sees the boss bar and, if set, a message about the event. */
    public void join(Player p) {
        if (!active()) return;
        if (bar != null) bar.addPlayer(p);
        if (cfg().getBoolean("events.show.join-message", true)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && active()) plugin.lang().send(p, "event.join", concat(ph(), "%player%", p.getName()));
            }, 60L);
        }
    }

    private void startTicker() {
        if (ticker == null) ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        tick();
    }

    private void stopTicker() {
        if (ticker != null) ticker.cancel();
        ticker = null;
        if (bar != null) bar.removeAll();
        bar = null;
    }

    /** Every second while an event runs: boss bar, action bar, reminder, end. */
    private void tick() {
        if (!active()) {
            // time is over (the event stays saved: invites made during it keep their bonus)
            boolean ended = multiplier > 1.0 && enabled() && ticker != null;
            stopTicker();
            if (ended) announceEnd(multiplier);
            return;
        }
        FileConfiguration c = cfg();
        String[] ph = ph();
        if (c.getBoolean("events.show.bossbar", true)) {
            String title = Platform.legacy(Text.parse(Text.replace(plugin.lang().raw("event.bossbar"), ph), null));
            if (bar == null) {
                bar = Bukkit.createBossBar(title, ScreenEffects.color(c.getString("events.bossbar.color", "YELLOW")),
                        ScreenEffects.style(c.getString("events.bossbar.style", "PROGRESS")));
                for (Player p : Bukkit.getOnlinePlayers()) bar.addPlayer(p);
            } else {
                bar.setTitle(title);
            }
            long total = Math.max(1, end - start);
            bar.setProgress(Math.max(0.0, Math.min(1.0, millisLeft() / (double) total)));
        } else if (bar != null) {
            bar.removeAll();
            bar = null;
        }
        seconds++;
        if (c.getBoolean("events.show.actionbar", false) && seconds % 2 == 0) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                Platform.actionBar(p, Text.parse(Text.replace(plugin.lang().raw("event.actionbar"), concat(ph, "%player%", p.getName())), p));
            }
        }
        int every = c.getInt("events.remind-minutes", 0);
        if (every > 0 && System.currentTimeMillis() - lastRemind >= every * 60_000L) {
            lastRemind = System.currentTimeMillis();
            for (Player p : Bukkit.getOnlinePlayers()) plugin.lang().send(p, "event.remind", concat(ph, "%player%", p.getName()));
        }
    }

    public void shutdown() {
        stopTicker();
    }

    // ---------------------------------------------------------------- formats

    /** "2", "x2", "1.5" -> number, 0 when it is not one. */
    static double parseMultiplier(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT).replace(',', '.');
        if (t.startsWith("x")) t = t.substring(1);
        try {
            double v = Double.parseDouble(t);
            return Double.isNaN(v) || Double.isInfinite(v) ? 0 : v;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** "x2" of a reward -> 2, 1 when it is not one. */
    public static double parse(String part) {
        if (part == null || !part.startsWith("x")) return 1.0;
        double v = parseMultiplier(part);
        return v > 1.0 && v <= 1000 ? v : 1.0;
    }

    /** "30m", "2h", "1d", "1h30m", "90" (minutes) -> milliseconds, 0 when it is not a time. */
    static long parseTime(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        if (t.length() > 24) return 0;
        long cap = 365L * 86_400_000L;
        if (t.matches("\\d{1,9}")) return Math.min(Long.parseLong(t) * 60_000L, cap);
        if (t.matches("\\d+")) return 0;
        Matcher m = TIME.matcher(t);
        long total = 0;
        int matched = 0;
        while (m.find()) {
            if (m.group(1).length() > 9) return 0;
            long n = Long.parseLong(m.group(1));
            switch (m.group(2)) {
                case "d": total += n * 86_400_000L; break;
                case "h": total += n * 3_600_000L; break;
                case "m": total += n * 60_000L; break;
                default: total += n * 1000L;
            }
            matched += m.group().length();
        }
        if (matched != t.replace(" ", "").length() && matched != t.length()) return 0;
        return Math.min(total, cap);
    }

    /** 2.0 -> "2", 1.5 -> "1.5". */
    public static String format(double m) {
        if (m == Math.rint(m)) return String.valueOf((long) m);
        String s = String.format(Locale.US, "%.2f", m);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    /** 5430000 -> "1h 30m" (units from lang/en.yml > event.units). */
    public String duration(long millis) {
        long secs = Math.max(0, millis / 1000);
        long d = secs / 86400, h = secs % 86400 / 3600, m = secs % 3600 / 60, s = secs % 60;
        StringBuilder out = new StringBuilder();
        if (d > 0) out.append(d).append(plugin.lang().raw("event.units.days")).append(' ');
        if (h > 0) out.append(h).append(plugin.lang().raw("event.units.hours")).append(' ');
        if (m > 0 && d == 0) out.append(m).append(plugin.lang().raw("event.units.minutes")).append(' ');
        if (d == 0 && h == 0 && (s > 0 || out.length() == 0)) out.append(s).append(plugin.lang().raw("event.units.seconds"));
        return out.toString().trim();
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String[] concat(String[] a, String... b) {
        String[] out = new String[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
