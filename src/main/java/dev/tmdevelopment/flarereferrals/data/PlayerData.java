package dev.tmdevelopment.flarereferrals.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayerData {

    public static final String NONE = "none";
    public static final String PENDING = "pending";
    public static final String VERIFIED = "verified";

    private final UUID uuid;
    private String name;
    private UUID referredBy;
    private long referredAt;
    /** none / pending (invite saved, counts after the playtime in referral.verify-after-minutes) / verified */
    private String status = NONE;
    private int invites;
    private String ipHash;
    private long firstSeen;
    /** Seconds played on the whole network (network mode only). */
    private long playtime;
    private String code;
    private final List<RewardEntry> rewards = new ArrayList<>();

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() { return uuid; }
    public String name() { return name == null ? uuid.toString().substring(0, 8) : name; }
    public String rawName() { return name; }
    public void name(String v) { if (v != null) name = v; }
    public UUID referredBy() { return referredBy; }
    public void referredBy(UUID v) { referredBy = v; }
    public long referredAt() { return referredAt; }
    public void referredAt(long v) { referredAt = v; }
    public String status() { return status; }
    public void status(String v) { status = v == null ? NONE : v; }
    public boolean pendingInvite() { return PENDING.equals(status); }
    public int invites() { return invites; }
    public void invites(int v) { invites = Math.max(0, v); }
    public String ipHash() { return ipHash; }
    public void ipHash(String v) { ipHash = v; }
    public long firstSeen() { return firstSeen; }
    public void firstSeen(long v) { firstSeen = v; }
    public long playtime() { return playtime; }
    public void playtime(long v) { playtime = Math.max(0, v); }
    public String code() { return code; }
    public void code(String v) { code = v; }
    public List<RewardEntry> rewards() { return rewards; }

    public RewardEntry reward(long id) {
        for (RewardEntry e : rewards) if (e.id() == id) return e;
        return null;
    }

    public boolean hasUniq(String uniq) {
        for (RewardEntry e : rewards) if (e.uniq().equals(uniq)) return true;
        return false;
    }

    /** Reached milestones not collected yet (claim mode). A repeating milestone can be in it more times. */
    public List<Integer> claimable() {
        List<Integer> out = new ArrayList<>();
        for (RewardEntry e : rewards) {
            if (e.state() == RewardEntry.State.CLAIMABLE && e.milestone() > 0) out.add(e.milestone());
        }
        return out;
    }

    public List<RewardEntry> claimableEntries() {
        return byState(RewardEntry.State.CLAIMABLE);
    }

    /** Rewards waiting to be given. */
    public List<RewardEntry> pending() {
        return byState(RewardEntry.State.PENDING);
    }

    /** Non repeating milestones reached (collected or not), like the old "claimed" list. */
    public int claimedMilestones() {
        int n = 0;
        for (RewardEntry e : rewards) if (e.milestone() > 0 && e.uniq().indexOf('@') < 0) n++;
        return n;
    }

    public List<RewardEntry> byState(RewardEntry.State s) {
        List<RewardEntry> out = new ArrayList<>();
        for (RewardEntry e : rewards) if (e.state() == s) out.add(e);
        return out;
    }

    /** Takes every value from a fresher copy (network sync), keeping this object. */
    public void copyFrom(PlayerData o) {
        name(o.name);
        referredBy = o.referredBy;
        referredAt = o.referredAt;
        status = o.status;
        invites = o.invites;
        ipHash = o.ipHash;
        firstSeen = o.firstSeen;
        playtime = o.playtime;
        code = o.code;
        rewards.clear();
        for (RewardEntry e : o.rewards) rewards.add(e.copy());
    }

    public PlayerData copy() {
        PlayerData d = new PlayerData(uuid, name);
        d.copyFrom(this);
        return d;
    }
}
