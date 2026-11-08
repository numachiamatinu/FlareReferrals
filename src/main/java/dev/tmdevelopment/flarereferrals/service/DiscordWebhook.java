package dev.tmdevelopment.flarereferrals.service;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.util.Text;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Messages to a Discord channel (config.yml discord): new invites, milestones, winners of the weekly / monthly top.
 * Sent one by one on its own thread, never on the main thread. Nothing is retried; the URL is never written in logs.
 */
public final class DiscordWebhook {

    private static final Pattern URL_OK = Pattern.compile(
            "https://(?:(?:ptb|canary)\\.)?discord(?:app)?\\.com/api/webhooks/\\d{5,30}/[A-Za-z0-9_\\-]{20,120}");
    private static final Pattern COLOR_CODES = Pattern.compile("(?i)&#[0-9a-f]{6}|&[0-9a-fk-or]|§[0-9a-fk-or]|<[^>]{1,60}>");
    private static final Pattern TEMPLATE_COLORS = Pattern.compile(
            "(?i)&#[0-9a-f]{6}|&[0-9a-fk-or]|§[0-9a-fk-or]|</?(?:gradient|rainbow|bold|italic|underlined|b|i|u|color)[^>]{0,60}>");

    private final FlareReferrals plugin;
    /** At most 20 messages waiting: a flood of invites drops the extra ones instead of piling up. */
    private final ThreadPoolExecutor sender = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(20), r -> {
                Thread t = new Thread(r, "FlareReferrals-Discord");
                t.setDaemon(true);
                return t;
            }, new ThreadPoolExecutor.DiscardPolicy());
    private volatile long pausedUntil;
    private volatile boolean warnedUrl;

    public DiscordWebhook(FlareReferrals plugin) {
        this.plugin = plugin;
        sender.allowCoreThreadTimeOut(true);
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    /** On, with a valid URL, and this kind of message has a text. */
    public boolean wants(String kind) {
        if (!cfg().getBoolean("discord.enabled", false)) return false;
        String text = cfg().getString("discord.messages." + kind, "");
        return text != null && !text.trim().isEmpty() && url() != null;
    }

    private String url() {
        String u = cfg().getString("discord.url", "");
        u = u == null ? "" : u.trim();
        if (URL_OK.matcher(u).matches()) return u;
        if (!warnedUrl && !u.isEmpty()) {
            warnedUrl = true;
            plugin.getLogger().warning("discord.url is not a Discord webhook link (https://discord.com/api/webhooks/...): nothing is sent.");
        }
        return null;
    }

    /** Player names and other values: Discord markdown characters lose their meaning. */
    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if ("\\*_~`|>#[]()@:".indexOf(c) >= 0) b.append('\\');
            b.append(c);
        }
        return b.toString();
    }

    /** Sends discord.messages.&lt;kind&gt; with the placeholders (values are escaped here). */
    public void send(String kind, String... ph) {
        if (!wants(kind)) return;
        String[] safe = ph.clone();
        // colors out first (a translated name can have them), then the markdown characters lose their meaning
        for (int i = 1; i < safe.length; i += 2) safe[i] = escape(COLOR_CODES.matcher(safe[i] == null ? "" : safe[i]).replaceAll(""));
        String text = Text.replace(cfg().getString("discord.messages." + kind, ""), safe);
        // Minecraft colors of the template (Discord tags like <:emoji:id> and <t:...> stay)
        text = TEMPLATE_COLORS.matcher(text).replaceAll("");
        if (text.length() > 1900) text = text.substring(0, 1900);
        String url = url();
        String name = cfg().getString("discord.username", "Referrals");
        String color = cfg().getString("discord.color", "#FF7A6B");
        boolean embed = cfg().getBoolean("discord.embed", true);
        final String body = json(text, name, color, embed);
        sender.execute(() -> post(url, body));
    }

    private static String json(String text, String name, String color, boolean embed) {
        StringBuilder b = new StringBuilder("{");
        if (name != null && !name.trim().isEmpty()) {
            String n = name.trim();
            b.append("\"username\":").append(quote(n.length() > 80 ? n.substring(0, 80) : n)).append(',');
        }
        // no @everyone, @here or role / user pings, whatever the text says
        b.append("\"allowed_mentions\":{\"parse\":[]},");
        if (embed) {
            int c = 0xFF7A6B;
            try {
                c = Integer.parseInt(color.replace("#", "").trim(), 16) & 0xFFFFFF;
            } catch (RuntimeException ignored) {
                // default color
            }
            b.append("\"embeds\":[{\"description\":").append(quote(text)).append(",\"color\":").append(c).append("}]");
        } else {
            b.append("\"content\":").append(quote(text));
        }
        return b.append('}').toString();
    }

    static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.append('"').toString();
    }

    private void post(String url, String body) {
        if (System.currentTimeMillis() < pausedUntil) return;
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            con.setRequestProperty("User-Agent", "FlareReferrals");
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            con.setFixedLengthStreamingMode(out.length);
            try (OutputStream os = con.getOutputStream()) {
                os.write(out);
            }
            int code = con.getResponseCode();
            if (code == 429) {
                // too many messages: wait a bit, the ones in between are dropped
                pausedUntil = System.currentTimeMillis() + 10_000L;
            } else if (code == 401 || code == 403 || code == 404) {
                pausedUntil = System.currentTimeMillis() + 10 * 60_000L;
                plugin.getLogger().warning("Discord refused the webhook (" + code + "): check discord.url. Paused for 10 minutes.");
            } else if (code >= 300) {
                plugin.getLogger().warning("Discord answered " + code + " to a referral message.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not send the Discord message: " + e.getClass().getSimpleName());
        } finally {
            if (con != null) con.disconnect();
        }
    }

    public void shutdown() {
        sender.shutdown();
    }
}
