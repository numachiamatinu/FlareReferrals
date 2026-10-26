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

    public RewardManager(FlareReferrals plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rewards.yml");
    }

    public void load() {
        if (!file.exists()) plugin.saveResource("rewards.yml", false);
        dev.tmdevelopment.flarereferrals.util.Headers.sync(plugin, file, "rewards.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
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
        if (renamed) save();
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();

        inviter.save(yml.createSection("referral.inviter"));
        invited.save(yml.createSection("referral.invited"));
        ConfigurationSection ms = yml.createSection("milestones");
        for (Milestone m : milestones.values()) {
            m.save(ms.createSection(String.valueOf(m.amount())));
        }
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

    /** Resolves a pending reward key ("referral:inviter", "referral:invited", "milestone:5"). */
    public RewardSet resolve(String key) {
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
