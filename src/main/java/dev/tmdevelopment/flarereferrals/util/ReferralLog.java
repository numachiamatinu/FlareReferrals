package dev.tmdevelopment.flarereferrals.util;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * logs/referrals.log: every invite (also the blocked ones), reward given and admin change, with date and server.
 * Written on its own thread, so the server never waits for the disk. Off with logs.referrals: false.
 */
public final class ReferralLog {

    private static volatile ExecutorService writer;
    private static volatile File file;
    private static volatile boolean enabled;
    private static volatile String server = "";

    private ReferralLog() {
    }

    public static synchronized void start(Plugin plugin, boolean on, String serverName) {
        enabled = on;
        server = serverName == null ? "" : serverName;
        file = new File(plugin.getDataFolder(), "logs/referrals.log");
        if (writer == null) {
            writer = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "FlareReferrals-log");
                t.setDaemon(true);
                return t;
            });
        }
    }

    public static synchronized void stop() {
        if (writer == null) return;
        writer.shutdown();
        try {
            writer.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        writer = null;
    }

    /** One line: log("INVITE", "Bob -> Alice (invites 4)"). */
    public static void log(String action, String text) {
        if (!enabled) return;
        ExecutorService w = writer;
        File f = file;
        if (w == null || f == null) return;
        long time = System.currentTimeMillis();
        String where = server.isEmpty() ? "" : " [" + server + "]";
        try {
            w.execute(() -> {
                String line = "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(time)) + "]" + where
                        + " " + action + " " + text + "\n";
                try {
                    if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
                    try (Writer out = new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8)) {
                        out.write(line);
                    }
                } catch (Throwable ignored) {
                    // the log is only a help: never stop anything for it
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
        }
    }
}
