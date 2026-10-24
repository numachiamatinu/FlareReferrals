package dev.tmdevelopment.flarereferrals.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public final class PlayerData {

    private final UUID uuid;
    private String name;
    private UUID referredBy;
    private long referredAt;
    private int invites;
    private String ipHash;
    private final Set<Integer> claimed = new TreeSet<>();
    /** Milestones reached but not collected yet (claim mode). A repeating milestone can be in it more times. */
    private final List<Integer> claimable = new ArrayList<>();
    /** Pending reward keys + context, format: "key|inviter|invited". */
    private final List<String> pending = new ArrayList<>();

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() { return uuid; }
    public String name() { return name == null ? uuid.toString().substring(0, 8) : name; }
    public void name(String v) { name = v; }
    public UUID referredBy() { return referredBy; }
    public void referredBy(UUID v) { referredBy = v; }
    public long referredAt() { return referredAt; }
    public void referredAt(long v) { referredAt = v; }
    public int invites() { return invites; }
    public void invites(int v) { invites = Math.max(0, v); }
    public String ipHash() { return ipHash; }
    public void ipHash(String v) { ipHash = v; }
    public Set<Integer> claimed() { return claimed; }
    public List<Integer> claimable() { return claimable; }
    public List<String> pending() { return pending; }
}
