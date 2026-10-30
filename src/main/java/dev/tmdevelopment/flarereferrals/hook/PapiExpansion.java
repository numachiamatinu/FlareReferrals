package dev.tmdevelopment.flarereferrals.hook;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.data.PlayerData;
import dev.tmdevelopment.flarereferrals.reward.Milestone;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * %flarereferrals_invites%            invites of the player (counted)
 * %flarereferrals_invited_count%      players he invited, also the ones still waiting for their playtime
 * %flarereferrals_pending_invites%    invites waiting for referral.verify-after-minutes
 * %flarereferrals_referrer%           who invited the player
 * %flarereferrals_referred%           he entered who invited him (placeholders.true-text / false-text)
 * %flarereferrals_can_enter%          he can still enter who invited him (online players)
 * %flarereferrals_position%           position in the top (placeholders.nobody when he has no invites)
 * %flarereferrals_next_milestone%     invites of the next milestone
 * %flarereferrals_next_remaining%     invites missing to the next milestone
 * %flarereferrals_claimed%            number of milestones reached
 * %flarereferrals_claimable%          milestones waiting to be collected
 * %flarereferrals_top_<n>_name% / _invites / _line (line = placeholders.top-line)
 * %flarereferrals_event_active% / _multiplier / _time / _reason   reward event (/referrals event)
 * The old names %flaresrewards_...% (before the rename) keep working.
 */
public final class PapiExpansion extends PlaceholderExpansion {

    private final FlareReferrals plugin;
    private final String id;

    public PapiExpansion(FlareReferrals plugin) {
        this(plugin, "flarereferrals");
    }

    public PapiExpansion(FlareReferrals plugin, String id) {
        this.plugin = plugin;
        this.id = id;
    }

    /** Registers %flarereferrals_...% and the old %flaresrewards_...%. */
    public static void registerAll(FlareReferrals plugin) {
        new PapiExpansion(plugin).register();
        new PapiExpansion(plugin, "flaresrewards").register();
    }

    @Override public @NotNull String getIdentifier() { return id; }
    @Override public @NotNull String getAuthor() { return "TMDevelopment"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    private volatile java.util.List<PlayerData> topCache = new java.util.ArrayList<>();
    private volatile long topAt;

    /** The top is sorted at most every 5 seconds, not for every placeholder. */
    private java.util.List<PlayerData> top() {
        long now = System.currentTimeMillis();
        if (now - topAt > 5000) {
            topCache = plugin.data().top(Integer.MAX_VALUE);
            topAt = now;
        }
        return topCache;
    }

    private volatile java.util.Map<java.util.UUID, int[]> invitedCache = new java.util.HashMap<>();
    private volatile long invitedAt;

    /** Invited players of everyone [all, waiting for the playtime], counted at most every 5 seconds. */
    private int[] invited(java.util.UUID owner) {
        long now = System.currentTimeMillis();
        if (now - invitedAt > 5000) {
            java.util.Map<java.util.UUID, int[]> m = new java.util.HashMap<>();
            for (PlayerData x : plugin.data().snapshotView()) {
                if (x.referredBy() == null) continue;
                int[] c = m.computeIfAbsent(x.referredBy(), k -> new int[2]);
                c[0]++;
                if (x.pendingInvite()) c[1]++;
            }
            invitedCache = m;
            invitedAt = now;
        }
        int[] c = invitedCache.get(owner);
        return c == null ? new int[2] : c;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        // scoreboard / TAB plugins can ask from their own threads: never throw at them
        try {
            return value(player, params);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private String text(String key, String def) {
        return plugin.getConfig().getString("placeholders." + key, def);
    }

    /** "&a..." of config.yml as section colors, like every other placeholder plugin. */
    private static String colored(String s, OfflinePlayer about) {
        return dev.tmdevelopment.flarereferrals.util.Platform.legacy(dev.tmdevelopment.flarereferrals.util.Text.parseAbout(s, about));
    }

    private String bool(boolean v) {
        return colored(v ? text("true-text", "Yes") : text("false-text", "No"), null);
    }

    private String value(OfflinePlayer player, String params) {
        String p = params.toLowerCase(java.util.Locale.ROOT);
        if (p.startsWith("top_")) {
            String[] parts = p.split("_");
            if (parts.length != 3) return "";
            int pos;
            try { pos = Integer.parseInt(parts[1]); } catch (NumberFormatException e) { return ""; }
            java.util.List<PlayerData> top = pos >= 1 ? top() : new java.util.ArrayList<PlayerData>();
            if (pos < 1 || pos > top.size()) {
                if (parts[2].equals("invites")) return "0";
                if (parts[2].equals("line")) return "";
                return colored(text("nobody", "-"), null);
            }
            PlayerData d = top.get(pos - 1);
            switch (parts[2]) {
                case "invites": return String.valueOf(d.invites());
                case "line":
                    return colored(dev.tmdevelopment.flarereferrals.util.Text.replace(
                            text("top-line", "&#ff7a6b#{position} &f{player} &7- &#ff7a6b{invites}"),
                            "%position%", String.valueOf(pos), "%player%", d.name(), "%invites%", String.valueOf(d.invites())),
                            org.bukkit.Bukkit.getOfflinePlayer(d.uuid()));
                default: return d.name();
            }
        }
        if (p.startsWith("event_")) {
            dev.tmdevelopment.flarereferrals.service.ReferralEvents ev = plugin.events();
            boolean on = ev != null && ev.active();
            switch (p) {
                case "event_active": return bool(on);
                case "event_multiplier": return on ? dev.tmdevelopment.flarereferrals.service.ReferralEvents.format(ev.multiplier()) : "1";
                case "event_time": return on ? ev.duration(ev.millisLeft()) : "";
                case "event_reason": return on ? ev.reason() : "";
                default: return null;
            }
        }
        if (player == null) return "";
        PlayerData d = plugin.data().get(player.getUniqueId());
        switch (p) {
            case "invites":
                return String.valueOf(d == null ? 0 : d.invites());
            case "invited_count":
                return String.valueOf(invited(player.getUniqueId())[0]);
            case "pending_invites":
                return String.valueOf(invited(player.getUniqueId())[1]);
            case "referrer": {
                if (d == null || d.referredBy() == null) {
                    // colors as section codes, like every other placeholder plugin
                    return colored(plugin.lang().raw("info.nobody"), null);
                }
                PlayerData r = plugin.data().get(d.referredBy());
                return r == null ? "?" : r.name();
            }
            case "referred":
                return bool(d != null && d.referredBy() != null);
            case "can_enter": {
                org.bukkit.entity.Player online = player.getPlayer();
                return bool(online != null && plugin.hints() != null && plugin.hints().canEnter(online) == 0);
            }
            case "position": {
                java.util.List<PlayerData> top = top();
                for (int i = 0; i < top.size(); i++) {
                    if (top.get(i).uuid().equals(player.getUniqueId())) return String.valueOf(i + 1);
                }
                return colored(text("nobody", "-"), null);
            }
            case "next_milestone": {
                int inv = d == null ? 0 : d.invites();
                Milestone m = plugin.referrals().nextMilestone(inv);
                // a repeating milestone: the next number to reach (every 5, with 7 invites = 10)
                return m == null ? "-" : String.valueOf(plugin.referrals().nextTarget(m, inv));
            }
            case "next_remaining": {
                int inv = d == null ? 0 : d.invites();
                Milestone m = plugin.referrals().nextMilestone(inv);
                return m == null ? "0" : String.valueOf(plugin.referrals().nextTarget(m, inv) - inv);
            }
            case "claimable":
                return String.valueOf(d == null ? 0 : d.claimable().size());
            case "claimed":
                return String.valueOf(d == null ? 0 : d.claimedMilestones());
            default:
                return null;
        }
    }
}
