package dev.tmdevelopment.flarereferrals.command;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.data.PlayerData;
import dev.tmdevelopment.flarereferrals.storage.Merge;
import dev.tmdevelopment.flarereferrals.storage.Storage;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

/**
 * /referrals migrate &lt;yaml|sqlite|mysql&gt; [merge] [confirm]
 * Copies all the data of this server into another storage. The first time it only shows what it will do.
 */
final class MigrateCommand {

    private static volatile boolean running;

    private final FlareReferrals plugin;

    MigrateCommand(FlareReferrals plugin) {
        this.plugin = plugin;
    }

    void run(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            plugin.lang().send(sender, "usage.migrate", "%label%", label);
            return;
        }
        String target = args[1].toLowerCase(Locale.ROOT);
        if (!target.equals("yaml") && !target.equals("sqlite") && !target.equals("mysql")) {
            plugin.lang().send(sender, "usage.migrate", "%label%", label);
            return;
        }
        boolean merge = false;
        boolean confirm = false;
        for (int i = 2; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("merge")) merge = true;
            if (args[i].equalsIgnoreCase("confirm")) confirm = true;
        }
        String current = plugin.data().type();
        if (target.equals(current)) {
            plugin.lang().send(sender, "admin.migrate.same", "%type%", target);
            return;
        }
        if (running) {
            plugin.lang().send(sender, "admin.migrate.running");
            return;
        }
        if (plugin.data().frozen()) {
            plugin.lang().send(sender, "admin.migrate.restart");
            return;
        }
        // built here (main thread: it reads config.yml), opened in the background
        Storage target0;
        try {
            target0 = plugin.data().create(target, false, true);
        } catch (Throwable t) {
            plugin.lang().send(sender, "admin.migrate.failed", "%type%", target, "%error%", String.valueOf(t.getMessage()));
            return;
        }
        final Storage built = target0;
        running = true;
        final boolean doMerge = merge;
        final boolean doIt = confirm;
        final String again = "/" + label + " migrate " + target + (merge ? " merge" : "") + " confirm";
        // summary: counted from memory. Real copy: referrals and rewards are paused from now on, so nothing
        // changes in the old storage while it is copied (they stay paused until the restart)
        if (!doIt) {
            copy(sender, label, target, built, plugin.data().snapshot(), false, doMerge, again);
            return;
        }
        plugin.data().freeze();
        plugin.lang().send(sender, "admin.migrate.started", "%type%", target);
        // rewards being given right now finish first (at most 10 seconds), then the copy starts
        final int[] waited = {0};
        final org.bukkit.scheduler.BukkitTask[] wait = new org.bukkit.scheduler.BukkitTask[1];
        wait[0] = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.data().inFlight() > 0 && waited[0]++ < 200) return;
            wait[0].cancel();
            // data.yml: memory is the storage, copied here on the main thread. Database: read again on its thread
            copy(sender, label, target, built, plugin.data().remote() ? null : plugin.data().snapshot(), true, doMerge, again);
        }, 1L, 1L);
    }

    private void copy(CommandSender sender, String label, String target, Storage built, List<PlayerData> preview,
                      boolean doIt, boolean doMerge, String again) {
        plugin.data().background(() -> {
            Storage to = built;
            boolean copied = false;
            try {
                // with a database this runs after the changes queued before the pause, and reads them back
                List<PlayerData> snapshot = preview != null ? preview : plugin.data().currentData();
                int rewards = 0;
                for (PlayerData d : snapshot) rewards += d.rewards().size();
                to.open();
                int existing = to.count();
                if (existing > 0 && !doMerge) {
                    done(sender, "admin.migrate.not-empty", "%type%", target, "%amount%", String.valueOf(existing),
                            "%command%", "/" + label + " migrate " + target + " merge");
                    return;
                }
                if (!doIt) {
                    done(sender, doMerge ? "admin.migrate.summary-merge" : "admin.migrate.summary",
                            "%type%", target, "%players%", String.valueOf(snapshot.size()),
                            "%rewards%", String.valueOf(rewards), "%amount%", String.valueOf(existing),
                            "%command%", again);
                    return;
                }
                if (doMerge && existing > 0) {
                    Merge m = Merge.of(to.loadAll(), snapshot);
                    to.importAll(m.players.values());
                    copied = true;
                    finished(sender, target, "admin.migrate.merged", "%type%", target,
                            "%added%", String.valueOf(m.added), "%joined%", String.valueOf(m.joined),
                            "%conflicts%", String.valueOf(m.conflicts), "%players%", String.valueOf(m.players.size()));
                } else {
                    to.importAll(snapshot);
                    copied = true;
                    finished(sender, target, "admin.migrate.done", "%type%", target,
                            "%players%", String.valueOf(snapshot.size()), "%rewards%", String.valueOf(rewards));
                }
            } catch (Throwable t) {
                plugin.getLogger().severe("Migration to " + target + " failed: " + t.getMessage());
                done(sender, "admin.migrate.failed", "%type%", target, "%error%", String.valueOf(t.getMessage()));
            } finally {
                to.close();
                if (doIt && !copied) {
                    plugin.data().callMain(() -> {
                        plugin.data().unfreeze();
                        running = false;
                    });
                } else {
                    running = false;
                }
            }
        });
    }

    private void done(CommandSender sender, String key, String... ph) {
        plugin.data().callMain(() -> plugin.lang().send(sender, key, ph));
    }

    /** Copy done: this server switches to the new storage at the next restart. */
    private void finished(CommandSender sender, String target, String key, String... ph) {
        plugin.data().callMain(() -> {
            plugin.data().freeze();
            plugin.getConfig().set("storage.type", target);
            plugin.saveConfig();
            plugin.lang().send(sender, key, ph);
            plugin.lang().send(sender, "admin.migrate.restart");
            plugin.getLogger().info("Data copied to " + target + ". storage.type is now " + target
                    + " in config.yml: restart the server to use it.");
        });
    }
}
