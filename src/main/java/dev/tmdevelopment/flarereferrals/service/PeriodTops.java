package dev.tmdevelopment.flarereferrals.service;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.data.PlayerData;
import dev.tmdevelopment.flarereferrals.data.RewardEntry;
import dev.tmdevelopment.flarereferrals.reward.TopReward;
import dev.tmdevelopment.flarereferrals.util.Platform;
import dev.tmdevelopment.flarereferrals.util.Text;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Weekly and monthly top (config.yml period-top): the invites made this week / month, and the rewards of
 * rewards.yml > period-top given to the best inviters when the week / month ends.
 * An invite counts in the period in which it counted (after the playtime check, if there is one).
 */
public final class PeriodTops {

    /** One line of a top. */
    public static final class Entry {
        public final UUID uuid;
        public final String name;
        public final int invites;

        Entry(UUID uuid, String name, int invites) {
            this.uuid = uuid;
            this.name = name;
            this.invites = invites;
        }
    }

    public static final String WEEKLY = "weekly", MONTHLY = "monthly";

    private final FlareReferrals plugin;
    private final File file;
    /** Start of the period seen last (to notice when a new one begins). */
    private final Map<String, Long> seen = new HashMap<>();
    private final Map<String, List<Entry>> cache = new HashMap<>();
    private final Map<String, Long> cacheAt = new HashMap<>();
    private String badZone;

    public PeriodTops(FlareReferrals plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/periods.yml");
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    public static boolean valid(String period) {
        return WEEKLY.equals(period) || MONTHLY.equals(period);
    }

    // ---------------------------------------------------------------- time

    /** config.yml period-top.timezone ("" = the time zone of the server). */
    public ZoneId zone() {
        String z = cfg().getString("period-top.timezone", "");
        if (z == null || z.trim().isEmpty()) return ZoneId.systemDefault();
        try {
            return ZoneId.of(z.trim());
        } catch (Exception e) {
            if (!z.equals(badZone)) {
                badZone = z;
                plugin.getLogger().warning("period-top.timezone '" + z + "' is not a time zone (e.g. Europe/Rome, UTC): using the server one.");
            }
            return ZoneId.systemDefault();
        }
    }

    private DayOfWeek firstDay() {
        try {
            return DayOfWeek.valueOf(cfg().getString("period-top.week-starts", "MONDAY").trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return DayOfWeek.MONDAY;
        }
    }

    /** Start of the week / month that contains {@code at}. */
    public long start(String period, long at) {
        return startOf(period, at, zone(), firstDay());
    }

    /** End of the period that starts at {@code start}. */
    public long end(String period, long start) {
        return endOf(period, start, zone());
    }

    public static long startOf(String period, long at, ZoneId zone, DayOfWeek firstDay) {
        LocalDate day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate();
        LocalDate first = WEEKLY.equals(period) ? day.with(TemporalAdjusters.previousOrSame(firstDay)) : day.withDayOfMonth(1);
        return first.atStartOfDay(zone).toInstant().toEpochMilli();
    }

    public static long endOf(String period, long start, ZoneId zone) {
        LocalDate first = Instant.ofEpochMilli(start).atZone(zone).toLocalDate();
        LocalDate next = WEEKLY.equals(period) ? first.plusWeeks(1) : first.plusMonths(1);
        return next.atStartOfDay(zone).toInstant().toEpochMilli();
    }

    /** Start of today (stats). */
    public long today() {
        ZoneId zone = zone();
        return Instant.now().atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli();
    }

    public long millisLeft(String period) {
        long now = System.currentTimeMillis();
        return Math.max(0, end(period, start(period, now)) - now);
    }

    // ---------------------------------------------------------------- counts

    /** The top of a time range: invites that count, written in [from, to). */
    public List<Entry> ranking(long from, long to) {
        Map<UUID, int[]> counts = new HashMap<>();
        for (PlayerData d : plugin.data().snapshotView()) {
            if (d.referredBy() == null || d.pendingInvite()) continue;
            long at = d.countedAt();
            if (at < from || at >= to) continue;
            counts.computeIfAbsent(d.referredBy(), k -> new int[1])[0]++;
        }
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<UUID, int[]> e : counts.entrySet()) {
            PlayerData who = plugin.data().get(e.getKey());
            out.add(new Entry(e.getKey(), who == null ? e.getKey().toString().substring(0, 8) : who.name(), e.getValue()[0]));
        }
        out.sort((a, b) -> a.invites != b.invites ? Integer.compare(b.invites, a.invites) : a.name.compareToIgnoreCase(b.name));
        return out;
    }

    /** A line of the all-time top. */
    public Entry entry(PlayerData d) {
        return new Entry(d.uuid(), d.name(), d.invites());
    }

    /** Invites that count, written in [from, to), of everyone. */
    public int total(long from, long to) {
        int n = 0;
        for (PlayerData d : plugin.data().snapshotView()) {
            if (d.referredBy() == null || d.pendingInvite()) continue;
            long at = d.countedAt();
            if (at >= from && at < to) n++;
        }
        return n;
    }

    /** The top of the running week / month, counted again at most every 10 seconds. */
    public synchronized List<Entry> current(String period) {
        long now = System.currentTimeMillis();
        Long at = cacheAt.get(period);
        List<Entry> list = cache.get(period);
        if (list == null || at == null || now - at > 10_000L) {
            long from = start(period, now);
            list = ranking(from, end(period, from));
            cache.put(period, list);
            cacheAt.put(period, now);
        }
        return list;
    }

    public int invites(String period, UUID uuid) {
        for (Entry e : current(period)) if (e.uuid.equals(uuid)) return e.invites;
        return 0;
    }

    /** 1 = first, 0 = not in the top (no invites in this period). */
    public int position(String period, UUID uuid) {
        List<Entry> list = current(period);
        for (int i = 0; i < list.size(); i++) if (list.get(i).uuid.equals(uuid)) return i + 1;
        return 0;
    }

    // ---------------------------------------------------------------- end of a period

    public void load() {
        YamlConfiguration y = file.isFile() ? dev.tmdevelopment.flarereferrals.util.SafeYaml.load(plugin, file, "data/periods.yml") : null;
        for (String p : new String[]{WEEKLY, MONTHLY}) {
            long v = y == null ? 0 : y.getLong(p, 0);
            if (v > 0) seen.put(p, v);
        }
    }

    private void save() {
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            YamlConfiguration y = new YamlConfiguration();
            for (Map.Entry<String, Long> e : seen.entrySet()) y.set(e.getKey(), e.getValue());
            dev.tmdevelopment.flarereferrals.util.J8.write(file.toPath(),
                    "# Start of the last week / month seen (weekly and monthly top). Don't edit.\n" + y.saveToString(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save data/periods.yml: " + e.getMessage());
        }
    }

    /** Every minute: a new week / month started? Then the one that ended gets its rewards. */
    public void minute() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (String period : new String[]{WEEKLY, MONTHLY}) {
            long cur = start(period, now);
            Long last = seen.get(period);
            if (last == null || last <= 0) {
                // first start with this version: nothing ended yet
                seen.put(period, cur);
                changed = true;
                continue;
            }
            if (last == cur) continue;
            long lastEnd = end(period, last);
            seen.put(period, cur);
            changed = true;
            // the time zone / first day of the week changed meanwhile: the period seen last is not over yet
            if (now < lastEnd) continue;
            // only the period seen last (a server that was off for weeks doesn't give old weeks)
            finish(period, last, lastEnd, now);
        }
        if (changed) save();
    }

    /** "2026-11-02": first day of the period, the same on every server of a network (also with other time zones). */
    private String label(long from) {
        return Instant.ofEpochMilli(from).atZone(zone()).toLocalDate().toString();
    }

    private void finish(String period, long from, long to, long now) {
        synchronized (this) {
            cache.remove(period);
        }
        if (!cfg().getBoolean("period-top.enabled", false)) return;
        List<Entry> ranking = ranking(from, to);
        String word = plugin.lang().raw("period." + period + ".word");
        // told only when it just ended (not when a server starts days later)
        boolean tell = now - to < 15 * 60_000L && cfg().getBoolean("period-top.announce", true) && !ranking.isEmpty();
        java.util.SortedMap<Integer, TopReward> rewards = cfg().getBoolean("period-top." + period + "-rewards", true)
                ? plugin.rewards().top(period) : new java.util.TreeMap<Integer, TopReward>();
        if (rewards.isEmpty() || ranking.isEmpty()) {
            if (tell) announce(period, ranking);
            return;
        }
        String day = label(from);
        // what each winner gets, decided here on the main thread
        List<Object[]> plan = new ArrayList<>();
        for (Map.Entry<Integer, TopReward> r : rewards.entrySet()) {
            int pos = r.getKey();
            if (pos > ranking.size()) break;
            Entry e = ranking.get(pos - 1);
            TopReward set = r.getValue();
            plan.add(new Object[]{e, set, "top:" + period + ":" + day + ":" + pos,
                    set.id() + "|" + e.name + "|||" + e.invites, plugin.referrals().stateFor(set), pos});
        }
        // the claim and the rewards in one go: on a network only one server gives them, and a database error
        // leaves nothing half done (the next minute tries again)
        plugin.data().run(ops -> {
            if (!ops.claim("top-" + period + "-" + day)) return new ArrayList<RewardEntry>();
            List<RewardEntry> made = new ArrayList<>();
            for (Object[] p : plan) {
                RewardEntry re = ops.addReward(((Entry) p[0]).uuid, (String) p[2], (String) p[3], (RewardEntry.State) p[4]);
                made.add(re);
            }
            return made;
        }, made -> {
            if (made == null) {
                plugin.getLogger().warning("The " + word + " top rewards could not be saved (database error): trying again in a minute.");
                seen.put(period, from);
                save();
                return;
            }
            if (tell) announce(period, ranking);
            for (int i = 0; i < made.size(); i++) {
                RewardEntry re = made.get(i);
                if (re == null) continue;
                Entry e = (Entry) plan.get(i)[0];
                plugin.referrals().created(e.uuid, re, (TopReward) plan.get(i)[1]);
                dev.tmdevelopment.flarereferrals.util.ReferralLog.log("TOP", e.name + " was #" + plan.get(i)[5] + " of the " + word
                        + " (" + e.invites + " invites): reward created");
            }
        });
    }

    private void announce(String period, List<Entry> ranking) {
        int lines = Math.max(1, Math.min(10, cfg().getInt("period-top.announce-lines", 3)));
        String[] ph = {"%period%", plugin.lang().raw("period." + period + ".word"),
                "%period-name%", plugin.lang().raw("period." + period + ".name")};
        for (String line : plugin.lang().lines("period.ended")) {
            if (!line.contains("{line}") && !line.contains("%line%")) {
                Platform.broadcast(Text.parse(Text.replace(line, ph), null));
                continue;
            }
            for (int i = 0; i < Math.min(lines, ranking.size()); i++) {
                Entry e = ranking.get(i);
                String one = Text.replace(plugin.lang().raw("period.ended-line"), "%position%", String.valueOf(i + 1),
                        "%player%", e.name, "%invites%", String.valueOf(e.invites));
                Platform.broadcast(Text.parse(Text.replace(Text.replace(line, "%line%", one), ph), null));
            }
        }
    }

    /** Rewards waiting (in the collect menu or for the next join) of everyone: stats. */
    public int[] waiting() {
        int claim = 0, pending = 0;
        for (PlayerData d : plugin.data().snapshotView()) {
            for (RewardEntry e : d.rewards()) {
                if (e.state() == RewardEntry.State.CLAIMABLE) claim++;
                else if (e.state() == RewardEntry.State.PENDING) pending++;
            }
        }
        return new int[]{claim, pending};
    }
}
