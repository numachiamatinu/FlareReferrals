package dev.tmdevelopment.flarereferrals.reward;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Reward for the inviter when a player he invited reaches a playtime (rewards.yml > active-friends), and
 * optionally ("invited:" inside the goal) a reward for the invited player himself at the same playtime.
 */
public final class Goal extends RewardSet {

    /** The part for the invited player ("invited:" inside the goal). */
    public static final class ForInvited extends RewardSet {
        private final Goal goal;
        private String name;

        ForInvited(Goal goal) {
            super("goal-invited:" + goal.minutes);
            this.goal = goal;
            this.name = "&#ff7a6bYou played " + Goal.time(goal.minutes);
        }

        @Override
        public void load(ConfigurationSection s) {
            super.load(s);
            if (s != null) name = s.getString("name", name);
        }

        @Override
        public void save(ConfigurationSection s) {
            s.set("name", name);
            super.save(s);
        }

        public Goal goal() { return goal; }
        public String name() { return name; }
    }

    private final int minutes;
    private String name;
    private ForInvited invited;

    public Goal(int minutes) {
        super("goal:" + minutes);
        this.minutes = minutes;
        this.name = "&#ff7a6bFriend played " + Goal.time(minutes);
    }

    @Override
    public void load(ConfigurationSection s) {
        super.load(s);
        if (s == null) return;
        name = s.getString("name", name);
        ConfigurationSection inv = s.getConfigurationSection("invited");
        if (inv != null) {
            invited = new ForInvited(this);
            invited.load(inv);
        }
    }

    @Override
    public void save(ConfigurationSection s) {
        s.set("name", name);
        super.save(s);
        if (invited != null) invited.save(s.createSection("invited"));
    }

    public int minutes() { return minutes; }
    public String name() { return name; }
    /** null when the goal gives nothing to the invited player. */
    public ForInvited invited() { return invited; }

    /** 90 = "1h 30m", 120 = "2h", 45 = "45m". */
    public static String time(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        if (h == 0) return m + "m";
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }
}
