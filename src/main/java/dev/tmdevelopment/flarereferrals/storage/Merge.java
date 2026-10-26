package dev.tmdevelopment.flarereferrals.storage;

import dev.tmdevelopment.flarereferrals.data.PlayerData;
import dev.tmdevelopment.flarereferrals.data.RewardEntry;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * /referrals migrate &lt;type&gt; merge: joins the data of this server with the data already in the target
 * (two sections that had their own data.yml before going on one database).
 */
public final class Merge {

    public final Map<UUID, PlayerData> players = new LinkedHashMap<>();
    /** Players only on this server. */
    public int added;
    /** Players on both. */
    public int joined;
    /** Players that entered a different inviter on the two sections: the first one entered is kept. */
    public int conflicts;

    private Merge() {
    }

    public static Merge of(Map<UUID, PlayerData> existing, Collection<PlayerData> incoming) {
        Merge m = new Merge();
        for (PlayerData d : existing.values()) m.players.put(d.uuid(), d.copy());
        Map<UUID, Integer> minus = new HashMap<>();
        for (PlayerData in : incoming) {
            PlayerData t = m.players.get(in.uuid());
            if (t == null) {
                m.players.put(in.uuid(), in.copy());
                m.added++;
                continue;
            }
            m.joined++;
            if (t.rawName() == null) t.name(in.rawName());
            t.invites(t.invites() + in.invites());
            if (t.referredBy() == null) {
                if (in.referredBy() != null) {
                    t.referredBy(in.referredBy());
                    t.referredAt(in.referredAt());
                    t.status(in.status());
                }
            } else if (in.referredBy() != null) {
                // the player counted as an invite on both sections: keep one inviter, remove the second count
                boolean keepIncoming = in.referredAt() > 0 && (t.referredAt() == 0 || in.referredAt() < t.referredAt());
                String statusBefore = t.status();
                UUID dropped = keepIncoming ? t.referredBy() : in.referredBy();
                if (!t.referredBy().equals(in.referredBy())) m.conflicts++;
                if (keepIncoming) {
                    t.referredBy(in.referredBy());
                    t.referredAt(in.referredAt());
                    t.status(in.status());
                }
                String droppedStatus = keepIncoming ? statusBefore : in.status();
                // a "pending" invite was never counted, nothing to remove
                if (!PlayerData.PENDING.equals(droppedStatus)) minus.merge(dropped, 1, Integer::sum);
            }
            if (t.ipHash() == null) t.ipHash(in.ipHash());
            if (in.firstSeen() > 0 && (t.firstSeen() == 0 || in.firstSeen() < t.firstSeen())) t.firstSeen(in.firstSeen());
            t.playtime(t.playtime() + in.playtime());
            if (t.code() == null) t.code(in.code());
            for (RewardEntry e : in.rewards()) {
                if (!t.hasUniq(e.uniq())) {
                    t.rewards().add(e.copy());
                } else if (e.uniq().startsWith("old-") || e.uniq().contains("@")) {
                    // rows from the old format / repeating milestones: two sections can have the same key, keep both
                    // (first free #mN, also when a third section is joined later)
                    int n = 0;
                    while (t.hasUniq(e.uniq() + "#m" + n)) n++;
                    t.rewards().add(new RewardEntry(e.id(), e.uniq() + "#m" + n, e.reward(), e.state(), e.created(),
                            e.delivered(), e.deliveredOn()));
                }
            }
        }
        for (Map.Entry<UUID, Integer> e : minus.entrySet()) {
            PlayerData d = m.players.get(e.getKey());
            if (d != null) d.invites(d.invites() - e.getValue());
        }
        // new reward numbers, unique in the merged data
        long id = 1;
        for (PlayerData d : m.players.values()) {
            java.util.List<RewardEntry> list = new java.util.ArrayList<>(d.rewards());
            d.rewards().clear();
            for (RewardEntry e : list) {
                d.rewards().add(new RewardEntry(id++, e.uniq(), e.reward(), e.state(), e.created(), e.delivered(), e.deliveredOn()));
            }
        }
        return m;
    }
}
