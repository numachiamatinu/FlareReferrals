package dev.tmdevelopment.flarereferrals.reward;

import org.bukkit.configuration.ConfigurationSection;

/** Reward for a position of the weekly or monthly top (rewards.yml > period-top). */
public final class TopReward extends RewardSet {

    private final String period;
    private final int position;
    private String name;

    public TopReward(String period, int position) {
        super("top:" + period + ":" + position);
        this.period = period;
        this.position = position;
        this.name = "&#ff7a6b#" + position + " of the " + period + " top";
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

    /** weekly or monthly */
    public String period() { return period; }
    public int position() { return position; }
    public String name() { return name; }
}
