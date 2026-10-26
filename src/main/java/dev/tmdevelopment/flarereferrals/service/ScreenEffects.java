package dev.tmdevelopment.flarereferrals.service;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import net.kyori.adventure.text.Component;
import dev.tmdevelopment.flarereferrals.reward.Display;
import dev.tmdevelopment.flarereferrals.util.Platform;
import dev.tmdevelopment.flarereferrals.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** Shows the title, action bar and boss bar of a reward to one player. */
public final class ScreenEffects {

    private ScreenEffects() {}

    /** Boss bars on screen right now (removed on disable, /reload included). */
    private static final java.util.Set<BossBar> BARS = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<BossBar, Boolean>());

    public static void clear() {
        for (BossBar bar : new java.util.ArrayList<>(BARS)) {
            try { bar.removeAll(); } catch (RuntimeException ignored) { }
        }
        BARS.clear();
    }

    public static void show(FlareReferrals plugin, Player p, Display d, String... placeholders) {
        if (d == null || !d.any()) return;
        String prefix = plugin.lang().prefix();

        if (d.hasTitle()) {
            Platform.title(p, comp(d.title(), p, prefix, placeholders), comp(d.subtitle(), p, prefix, placeholders),
                    d.fadeIn(), d.stay(), d.fadeOut());
        }
        if (d.hasActionbar()) {
            Platform.actionBar(p, comp(d.actionbar(), p, prefix, placeholders));
        }
        if (d.hasBossbar()) {
            final BossBar bar = Bukkit.createBossBar(Platform.legacy(comp(d.bossbar(), p, prefix, placeholders)),
                    color(d.bossbarColor()), style(d.bossbarStyle()));
            bar.setProgress(1.0);
            bar.addPlayer(p);
            BARS.add(bar);
            final int total = Math.max(1, d.bossbarSeconds() * 20);
            final int[] left = {total};
            final BukkitTask[] task = new BukkitTask[1];
            task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                left[0] -= 2;
                if (left[0] <= 0 || !p.isOnline()) {
                    bar.removeAll();
                    BARS.remove(bar);
                    if (task[0] != null) task[0].cancel();
                    return;
                }
                bar.setProgress(Math.max(0.0, Math.min(1.0, left[0] / (double) total)));
            }, 2L, 2L);
        }
    }

    private static BarColor color(String name) {
        try {
            return BarColor.valueOf(name == null ? "" : name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BarColor.PINK;
        }
    }

    /** Accepts the Bukkit names (SOLID, SEGMENTED_10) and the older ones of this plugin (PROGRESS, NOTCHED_10). */
    private static BarStyle style(String name) {
        String n = name == null ? "" : name.toUpperCase(java.util.Locale.ROOT);
        if (n.equals("PROGRESS")) return BarStyle.SOLID;
        if (n.startsWith("NOTCHED_")) n = "SEGMENTED_" + n.substring(8);
        try {
            return BarStyle.valueOf(n);
        } catch (IllegalArgumentException e) {
            return BarStyle.SOLID;
        }
    }

    private static Component comp(String raw, Player p, String prefix, String... placeholders) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        String s = raw.replace("%prefix%", prefix).replace("{prefix}", prefix);
        return Text.parse(Text.replace(s, placeholders), p);
    }
}
