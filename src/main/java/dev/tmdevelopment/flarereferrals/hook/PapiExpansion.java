package dev.tmdevelopment.flarereferrals.hook;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.data.PlayerData;
import dev.tmdevelopment.flarereferrals.reward.Milestone;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * %flarereferrals_invites%            invites of the player
 * %flarereferrals_referrer%           who invited the player
 * %flarereferrals_next_milestone%     next milestone amount
 * %flarereferrals_next_remaining%     invites missing to the next milestone
 * %flarereferrals_claimed%            number of milestones reached
 * %flarereferrals_claimable%          milestones waiting to be collected
 * %flarereferrals_top_<n>_name% / %flarereferrals_top_<n>_invites%
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

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String p = params.toLowerCase();
        if (p.startsWith("top_")) {
            String[] parts = p.split("_");
            if (parts.length != 3) return "";
            int pos;
            try { pos = Integer.parseInt(parts[1]); } catch (NumberFormatException e) { return ""; }
            java.util.List<PlayerData> top = plugin.data().top(pos);
            if (pos < 1 || pos > top.size()) return parts[2].equals("invites") ? "0" : "-";
            PlayerData d = top.get(pos - 1);
            return parts[2].equals("invites") ? String.valueOf(d.invites()) : d.name();
        }
        if (player == null) return "";
        PlayerData d = plugin.data().get(player.getUniqueId());
        switch (p) {
            case "invites":
                return String.valueOf(d == null ? 0 : d.invites());
            case "referrer": {
                if (d == null || d.referredBy() == null) return plugin.lang().raw("info.nobody");
                PlayerData r = plugin.data().get(d.referredBy());
                return r == null ? "?" : r.name();
            }
            case "next_milestone": {
                Milestone m = plugin.referrals().nextMilestone(d == null ? 0 : d.invites());
                return m == null ? "-" : String.valueOf(m.amount());
            }
            case "next_remaining": {
                int inv = d == null ? 0 : d.invites();
                Milestone m = plugin.referrals().nextMilestone(inv);
                return m == null ? "0" : String.valueOf(plugin.referrals().nextTarget(m, inv) - inv);
            }
            case "claimable":
                return String.valueOf(d == null ? 0 : d.claimable().size());
            case "claimed":
                return String.valueOf(d == null ? 0 : d.claimed().size());
            default:
                return null;
        }
    }
}
