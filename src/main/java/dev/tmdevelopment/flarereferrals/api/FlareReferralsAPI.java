package dev.tmdevelopment.flarereferrals.api;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.data.PlayerData;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * For other plugins: invites, inviters and the top. Read only, call it from the main thread.
 * Add FlareReferrals to softdepend in your plugin.yml. Events: {@link ReferralEvent}, {@link ReferralCompleteEvent}.
 *
 * <pre>
 * int invites = FlareReferralsAPI.getInvites(player.getUniqueId());
 * </pre>
 */
public final class FlareReferralsAPI {

    private FlareReferralsAPI() {
    }

    /** True when FlareReferrals is installed and running. */
    public static boolean isReady() {
        org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("FlareReferrals");
        return p instanceof FlareReferrals && p.isEnabled() && ((FlareReferrals) p).data() != null;
    }

    private static PlayerData data(UUID player) {
        if (!isReady() || player == null) return null;
        return ((FlareReferrals) Bukkit.getPluginManager().getPlugin("FlareReferrals")).data().get(player);
    }

    /** Players this player invited (0 when unknown). */
    public static int getInvites(UUID player) {
        PlayerData d = data(player);
        return d == null ? 0 : d.invites();
    }

    /** Who invited this player, or null. */
    public static UUID getInviter(UUID player) {
        PlayerData d = data(player);
        return d == null ? null : d.referredBy();
    }

    /** True when the player already wrote who invited him (also while the invite waits for the playtime check). */
    public static boolean hasInviter(UUID player) {
        return getInviter(player) != null;
    }

    /** True when his invite still waits for referral.verify-after-minutes. */
    public static boolean isPending(UUID player) {
        PlayerData d = data(player);
        return d != null && d.pendingInvite();
    }

    /** One line of the top. */
    public static final class TopEntry {
        private final UUID uuid;
        private final String name;
        private final int invites;

        TopEntry(UUID uuid, String name, int invites) {
            this.uuid = uuid;
            this.name = name;
            this.invites = invites;
        }

        public UUID getUuid() { return uuid; }
        public String getName() { return name; }
        public int getInvites() { return invites; }
    }

    /** The best inviters, first = most invites. */
    public static List<TopEntry> getTop(int amount) {
        if (!isReady() || amount <= 0) return Collections.emptyList();
        List<TopEntry> out = new ArrayList<>();
        for (PlayerData d : ((FlareReferrals) Bukkit.getPluginManager().getPlugin("FlareReferrals")).data().top(amount)) {
            out.add(new TopEntry(d.uuid(), d.name(), d.invites()));
        }
        return out;
    }
}
