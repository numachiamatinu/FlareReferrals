package dev.tmdevelopment.flarereferrals.service;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.data.PlayerData;
import dev.tmdevelopment.flarereferrals.reward.Display;
import dev.tmdevelopment.flarereferrals.util.Perms;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asks new players who invited them (config.yml > invite-hint, off by default): in chat, title, action bar,
 * boss bar and with a sound, as the admin chooses. Players stop getting it once they entered who invited them,
 * or when they can't do it anymore (referral.max-days-after-first-join).
 */
public final class InviteHint {

    private static final class State {
        long next;
        int shown;
    }

    private final FlareReferrals plugin;
    private final Map<UUID, State> waiting = new ConcurrentHashMap<>();
    private BukkitTask task;

    public InviteHint(FlareReferrals plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    public boolean enabled() {
        return cfg().getBoolean("invite-hint.enabled", false);
    }

    /** /referrals reload: turned on or off. */
    public void reload() {
        if (!enabled()) {
            waiting.clear();
            if (task != null) task.cancel();
            task = null;
            return;
        }
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        waiting.clear();
    }

    /** @param firstJoin true on the first join to this server */
    public void join(Player p, boolean firstJoin) {
        if (!enabled()) return;
        if (cfg().getBoolean("invite-hint.only-first-join", false) && !firstJoin) return;
        if (Perms.has(p, "admin")) return; // staff never get it
        if (!isNew(p)) return;
        State s = new State();
        s.next = System.currentTimeMillis() + Math.max(1, cfg().getInt("invite-hint.delay-seconds", 15)) * 1000L;
        waiting.put(p.getUniqueId(), s);
        reload();
    }

    public void quit(Player p) {
        waiting.remove(p.getUniqueId());
    }

    /**
     * 0 = he can enter who invited him now, 1 = not yet (playtime), 2 = never (already done, time over, no permission).
     */
    public int canEnter(Player p) {
        if (!cfg().getBoolean("referral.enabled", true) || !Perms.has(p, "use")) return 2;
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d != null && d.referredBy() != null) return 2;
        if (Perms.has(p, "bypass")) return 0;
        int maxDays = cfg().getInt("referral.max-days-after-first-join", 0);
        long first = plugin.referrals().firstSeen(p, d);
        if (maxDays > 0 && first > 0 && System.currentTimeMillis() - first > maxDays * 86_400_000L) return 2;
        int min = cfg().getInt("referral.min-playtime-minutes", 0);
        if (min > 0 && cfg().getBoolean("invite-hint.wait-for-playtime", true) && plugin.referrals().playtimeMinutes(p, d) < min) return 1;
        return 0;
    }

    /** Joined the server for the first time in the last invite-hint.only-new-days days (0 = everyone). */
    private boolean isNew(Player p) {
        int days = cfg().getInt("invite-hint.only-new-days", 7);
        if (days <= 0) return true;
        long first = plugin.referrals().firstSeen(p, plugin.data().get(p.getUniqueId()));
        return first <= 0 || System.currentTimeMillis() - first <= days * 86_400_000L;
    }

    /** Days left to enter who invited him ("" when there is no limit). */
    public String daysLeft(Player p) {
        int maxDays = cfg().getInt("referral.max-days-after-first-join", 0);
        if (maxDays <= 0) return "";
        long first = plugin.referrals().firstSeen(p, plugin.data().get(p.getUniqueId()));
        if (first <= 0) return String.valueOf(maxDays);
        long left = maxDays * 86_400_000L - (System.currentTimeMillis() - first);
        return String.valueOf(Math.max(1, (left + 86_399_999L) / 86_400_000L));
    }

    private void tick() {
        if (waiting.isEmpty()) return;
        long now = System.currentTimeMillis();
        int repeat = cfg().getInt("invite-hint.repeat-minutes", 10);
        int max = cfg().getInt("invite-hint.max-per-join", 3);
        for (Iterator<Map.Entry<UUID, State>> it = waiting.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, State> en = it.next();
            State s = en.getValue();
            if (now < s.next) continue;
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            int can = canEnter(p);
            if (can == 2) {
                it.remove();
                continue;
            }
            if (can == 1) {
                s.next = now + 60_000L; // checked again in a minute
                continue;
            }
            show(p);
            s.shown++;
            if (repeat <= 0 || (max > 0 && s.shown >= max)) it.remove();
            else s.next = now + repeat * 60_000L;
        }
    }

    /** The reminder, where the admin wants it (chat, title, action bar, boss bar, sound). */
    public void show(Player p) {
        FileConfiguration c = cfg();
        String[] ph = {"%player%", p.getName(), "%days%", daysLeft(p)};
        if (c.getBoolean("invite-hint.show.chat", true)) {
            plugin.lang().send(p, daysLeft(p).isEmpty() ? "hint.chat" : "hint.chat-days", ph);
        }
        Display d = new Display();
        if (c.getBoolean("invite-hint.show.title", false)) {
            d.title(plugin.lang().raw("hint.title"));
            d.subtitle(plugin.lang().raw("hint.subtitle"));
            d.times(c.getInt("invite-hint.title.fade-in", 10), c.getInt("invite-hint.title.stay", 70), c.getInt("invite-hint.title.fade-out", 20));
        }
        if (c.getBoolean("invite-hint.show.actionbar", false)) d.actionbar(plugin.lang().raw("hint.actionbar"));
        if (c.getBoolean("invite-hint.show.bossbar", false)) {
            d.bossbar(plugin.lang().raw("hint.bossbar"));
            d.bossbarColor(c.getString("invite-hint.bossbar.color", "PINK"));
            d.bossbarStyle(c.getString("invite-hint.bossbar.style", "PROGRESS"));
            d.bossbarSeconds(c.getInt("invite-hint.bossbar.seconds", 10));
        }
        ScreenEffects.show(plugin, p, d, ph);
        String sound = c.getString("invite-hint.show.sound", "");
        if (sound != null && !sound.trim().isEmpty() && c.getBoolean("sounds.enabled", true)) {
            try {
                p.playSound(p.getLocation(), sound.toLowerCase(java.util.Locale.ROOT), 1f, 1f);
            } catch (Exception ignored) {
                // unknown sound: nothing
            }
        }
    }
}
