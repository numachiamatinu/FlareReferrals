package dev.tmdevelopment.flarereferrals.reward;

import org.bukkit.configuration.ConfigurationSection;

/** Reward for the inviter when a player he invited reaches a playtime (rewards.yml > active-friends). */
public final class Goal extends RewardSet {

    private final int minutes;
    private String name;

    public Goal(int minutes) {
        super("goal:" + minutes);
        this.minutes = minutes;
        this.name = "&#ff7a6bFriend played " + Goal.time(minutes);
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

    public int minutes() { return minutes; }
    public String name() { return name; }

    /** 90 = "1h 30m", 120 = "2h", 45 = "45m". */
    public static String time(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        if (h == 0) return m + "m";
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }
}
