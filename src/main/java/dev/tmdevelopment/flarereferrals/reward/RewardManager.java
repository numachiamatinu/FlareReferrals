package dev.tmdevelopment.flarereferrals.reward;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

public final class RewardManager {

    private final FlareReferrals plugin;
    private final File file;
    private final RewardSet inviter = new RewardSet("referral:inviter");
    private final RewardSet invited = new RewardSet("referral:invited");
    private final TreeMap<Integer, Milestone> milestones = new TreeMap<>();
    /** active-friends: minutes of playtime of the invited player -> reward for the inviter */
    private final TreeMap<Integer, Goal> goals = new TreeMap<>();
    /** period-top: weekly / monthly -> position -> reward */
    private final java.util.Map<String, TreeMap<Integer, TopReward>> tops = new java.util.LinkedHashMap<>();
    public static final String[] PERIODS = {"weekly", "monthly"};
    /** Sections that are in the file (written back also when empty). */
    private final java.util.Set<String> present = new java.util.HashSet<>();

    public RewardManager(FlareReferrals plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rewards.yml");
    }

    public void load() {
        if (!file.exists()) plugin.saveResource("rewards.yml", false);
        dev.tmdevelopment.flarereferrals.util.Headers.sync(plugin, file, "rewards.yml");
        YamlConfiguration yml = dev.tmdevelopment.flarereferrals.util.SafeYaml.load(plugin, file, "rewards.yml");
        // a file with a mistake is never saved: the rewards loaded before stay (none at startup)
        if (yml == null) return;
        File mark = new File(plugin.getDataFolder(), "data/rewards-1.1.yml");
        if ((!yml.isSet("active-friends") || !yml.isSet("period-top")) && !mark.exists()) {
            // files of older versions: the new sections are added once with their explanation (examples, off in
            // config.yml). Removed by the owner later = not added again
            YamlConfiguration more = addSections(yml);
            if (more != null) yml = more;
        }
        if (!mark.exists()) {
            try {
                File dir = mark.getParentFile();
                if (dir != null && !dir.exists()) dir.mkdirs();
                dev.tmdevelopment.flarereferrals.util.J8.write(mark.toPath(),
                        "# rewards.yml got the active-friends and period-top examples (1.1.0). Don't edit.\nadded: true\n",
                        java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not write data/rewards-1.1.yml: " + e.getMessage());
            }
        }
        present.clear();
        if (yml.isSet("active-friends")) present.add("active-friends");
        for (String p : PERIODS) if (yml.isSet("period-top." + p)) present.add("period-top." + p);
        boolean renamed = dev.tmdevelopment.flarereferrals.util.TextUpdates.renameCommand(yml);
        inviter.load(yml.getConfigurationSection("referral.inviter"));
        invited.load(yml.getConfigurationSection("referral.invited"));
        milestones.clear();
        ConfigurationSection ms = yml.getConfigurationSection("milestones");
        if (ms != null) {
            for (String key : ms.getKeys(false)) {
                int amount;
                try {
                    amount = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Milestone '" + key + "' is not a number, skipped.");
                    continue;
                }
                if (amount <= 0) continue;
                Milestone m = new Milestone(amount);
                m.load(ms.getConfigurationSection(key));
                milestones.put(amount, m);
            }
        }
        goals.clear();
        ConfigurationSection gs = yml.getConfigurationSection("active-friends");
        if (gs != null) {
            for (String key : gs.getKeys(false)) {
                int minutes = number(key);
                if (minutes <= 0 || !gs.isConfigurationSection(key)) {
                    plugin.getLogger().warning("rewards.yml > active-friends: '" + key + "' is not a number of minutes, skipped.");
                    continue;
                }
                Goal g = new Goal(minutes);
                g.load(gs.getConfigurationSection(key));
                goals.put(minutes, g);
            }
        }
        tops.clear();
        for (String period : PERIODS) {
            TreeMap<Integer, TopReward> map = new TreeMap<>();
            ConfigurationSection ts = yml.getConfigurationSection("period-top." + period);
            if (ts != null) {
                for (String key : ts.getKeys(false)) {
                    int pos = number(key);
                    if (pos <= 0 || pos > 100 || !ts.isConfigurationSection(key)) {
                        plugin.getLogger().warning("rewards.yml > period-top." + period + ": '" + key + "' is not a position (1-100), skipped.");
                        continue;
                    }
                    TopReward t = new TopReward(period, pos);
                    t.load(ts.getConfigurationSection(key));
                    map.put(pos, t);
                }
            }
            tops.put(period, map);
        }
        if (renamed) save();
    }

    private YamlConfiguration addSections(YamlConfiguration yml) {
        try (java.io.InputStream in = plugin.getResource("rewards.yml")) {
            if (in == null) return null;
            String def = new String(dev.tmdevelopment.flarereferrals.util.J8.readAll(in), java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
            String old = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
            String text = old;
            for (String key : new String[]{"active-friends", "period-top"}) {
                if (!yml.isSet(key)) text = dev.tmdevelopment.flarereferrals.util.YamlPatch.addMissing(text, def, key);
            }
            if (text.equals(old)) return null;
            YamlConfiguration check = new YamlConfiguration();
            check.loadFromString(text);
            dev.tmdevelopment.flarereferrals.util.J8.write(file.toPath(), text, java.nio.charset.StandardCharsets.UTF_8);
            return check;
        } catch (Exception e) {
            plugin.getLogger().warning("Could not add the new sections to rewards.yml: " + e.getMessage());
            return null;
        }
    }

    private static int number(String key) {
        try {
            return Integer.parseInt(key.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public void save() {
        if (dev.tmdevelopment.flarereferrals.util.SafeYaml.broken("rewards.yml")) {
            plugin.getLogger().warning("rewards.yml was not saved because it has a mistake: fix it and use /referrals reload.");
            return;
        }
        YamlConfiguration yml = new YamlConfiguration();

        inviter.save(yml.createSection("referral.inviter"));
        invited.save(yml.createSection("referral.invited"));
        ConfigurationSection ms = yml.createSection("milestones");
        for (Milestone m : milestones.values()) {
            m.save(ms.createSection(String.valueOf(m.amount())));
        }
        // written only when they exist, so files of older versions are not filled with empty sections
        if (!goals.isEmpty() || present.contains("active-friends")) {
            ConfigurationSection gs = yml.createSection("active-friends");
            for (Goal g : goals.values()) g.save(gs.createSection(String.valueOf(g.minutes())));
        }
        for (java.util.Map.Entry<String, TreeMap<Integer, TopReward>> e : tops.entrySet()) {
            if (e.getValue().isEmpty() && !present.contains("period-top." + e.getKey())) continue;
            ConfigurationSection ts = yml.createSection("period-top." + e.getKey());
            for (TopReward t : e.getValue().values()) t.save(ts.createSection(String.valueOf(t.position())));
        }
        // only what changed is written: comments, quotes and layout of the file stay as they are
        if (dev.tmdevelopment.flarereferrals.util.YamlKeeper.keep(file, yml)) return;
        try {
            dev.tmdevelopment.flarereferrals.util.J8.write(file.toPath(),
                    dev.tmdevelopment.flarereferrals.util.Headers.of(plugin, "rewards.yml") + comments(yml.saveToString()),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save rewards.yml: " + e.getMessage());
        }
    }

    /** The two explanations inside "referral" (the file is written again, these would be lost). */
    private static String comments(String yml) {
        return yml.replaceFirst("(?m)^  inviter:$", "  # Owner of the referral code (the player written in /referrals <player>)\n  inviter:")
                .replaceFirst("(?m)^  invited:$", "  # Player who used /referrals <player>\n  invited:");
    }

    public RewardSet inviter() { return inviter; }
    public RewardSet invited() { return invited; }

    public Collection<Milestone> milestones() { return milestones.values(); }

    public List<Milestone> milestoneList() { return new ArrayList<>(milestones.values()); }

    public Milestone milestone(int amount) { return milestones.get(amount); }

    public Milestone create(int amount) {
        Milestone m = milestones.get(amount);
        if (m != null) return m;
        m = new Milestone(amount);
        m.load(null);
        milestones.put(amount, m);
        save();
        return m;
    }

    public void delete(int amount) {
        milestones.remove(amount);
        save();
    }

    /** @return false when another milestone already uses the new amount. */
    public boolean changeAmount(Milestone m, int newAmount) {
        if (newAmount <= 0 || milestones.containsKey(newAmount)) return false;
        milestones.remove(m.amount());
        m.amount(newAmount);
        milestones.put(newAmount, m);
        save();
        return true;
    }

    public Collection<Goal> goals() { return goals.values(); }

    /** Rewards of the weekly or monthly top, by position. */
    public java.util.SortedMap<Integer, TopReward> top(String period) {
        TreeMap<Integer, TopReward> m = tops.get(period);
        return m == null ? new TreeMap<Integer, TopReward>() : m;
    }

    /** Resolves a pending reward key ("referral:inviter", "referral:invited", "milestone:5", "goal:60", "top:weekly:1"). */
    public RewardSet resolve(String key) {
        if (key.startsWith("goal:")) return goals.get(number(key.substring("goal:".length())));
        if (key.startsWith("goal-invited:")) {
            Goal g = goals.get(number(key.substring("goal-invited:".length())));
            return g == null ? null : g.invited();
        }
        if (key.startsWith("top:")) {
            String[] p = key.split(":");
            return p.length == 3 ? top(p[1]).get(number(p[2])) : null;
        }
        if (key.equals("referral:inviter")) return inviter;
        if (key.equals("referral:invited")) return invited;
        if (key.startsWith("milestone:")) {
            try {
                return milestones.get(Integer.parseInt(key.substring("milestone:".length())));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
