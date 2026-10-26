package dev.tmdevelopment.flarereferrals.data;

/**
 * One reward owed to a player. Every reward is a row: it is created once (the "uniq" key can't repeat for the
 * same player) and moves pending/claimable -> delivering -> delivered, so it can never be given twice,
 * not even by two servers of a network at the same time.
 */
public final class RewardEntry {

    public enum State {
        /** Given automatically as soon as the player is online on a server that can give it. */
        PENDING,
        /** Reached milestone the player collects from the menu or with /referrals claim. */
        CLAIMABLE,
        /** Being given right now by a server. */
        DELIVERING,
        DELIVERED;

        public String id() { return name().toLowerCase(java.util.Locale.ROOT); }

        public static State of(String s) {
            if (s == null) return PENDING;
            try {
                return valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return PENDING;
            }
        }
    }

    private final long id;
    private final String uniq;
    private final String reward;
    private State state;
    private final long created;
    private long delivered;
    private String deliveredOn;

    public RewardEntry(long id, String uniq, String reward, State state, long created, long delivered, String deliveredOn) {
        this.id = id;
        this.uniq = uniq;
        this.reward = reward;
        this.state = state;
        this.created = created;
        this.delivered = delivered;
        this.deliveredOn = deliveredOn;
    }

    public RewardEntry copy() {
        return new RewardEntry(id, uniq, reward, state, created, delivered, deliveredOn);
    }

    public long id() { return id; }
    /** Never repeats for the same player: "referral:invited", "referral:inviter@uuid", "milestone:5", "milestone:3@9". */
    public String uniq() { return uniq; }
    /** Reward key + context for placeholders: "milestone:5|inviter|invited". */
    public String reward() { return reward; }
    public State state() { return state; }
    public void state(State v) { state = v; }
    public long created() { return created; }
    public long delivered() { return delivered; }
    public void delivered(long v) { delivered = v; }
    public String deliveredOn() { return deliveredOn; }
    public void deliveredOn(String v) { deliveredOn = v; }

    /** "referral:inviter", "milestone:5"... */
    public String key() {
        int i = reward.indexOf('|');
        return i < 0 ? reward : reward.substring(0, i);
    }

    /** The milestone amount, or -1 when this is not a milestone reward. */
    public int milestone() {
        String k = key();
        if (!k.startsWith("milestone:")) return -1;
        try {
            return Integer.parseInt(k.substring("milestone:".length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
