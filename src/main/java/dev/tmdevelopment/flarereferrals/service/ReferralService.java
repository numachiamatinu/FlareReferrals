package dev.tmdevelopment.flarereferrals.service;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.config.Lang;
import dev.tmdevelopment.flarereferrals.data.PlayerData;
import dev.tmdevelopment.flarereferrals.reward.Milestone;
import dev.tmdevelopment.flarereferrals.reward.RewardSet;
import dev.tmdevelopment.flarereferrals.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

public final class ReferralService {

    private final FlareReferrals plugin;

    public ReferralService(FlareReferrals plugin) {
        this.plugin = plugin;
    }

    private Lang lang() { return plugin.lang(); }

    // ------------------------------------------------------------------ referral

    public void refer(Player user, String targetName) {
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("referral.enabled", true)) {
            lang().send(user, "referral.disabled");
            sound(user, "error");
            return;
        }
        PlayerData userData = plugin.data().getOrCreate(user.getUniqueId(), user.getName());
        if (userData.referredBy() != null) {
            PlayerData by = plugin.data().get(userData.referredBy());
            lang().send(user, "referral.already-referred", "%player%", by == null ? "?" : by.name());
            sound(user, "error");
            return;
        }

        OfflinePlayer target = Bukkit.getPlayerExact(targetName);
        if (target == null) target = dev.tmdevelopment.flarereferrals.util.Compat.knownPlayer(targetName);
        if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) {
            lang().send(user, "referral.not-found", "%player%", targetName);
            sound(user, "error");
            return;
        }
        if (target.getUniqueId().equals(user.getUniqueId())) {
            lang().send(user, "referral.self");
            sound(user, "error");
            return;
        }
        String targetNameReal = target.getName() == null ? targetName : target.getName();
        PlayerData targetData = plugin.data().getOrCreate(target.getUniqueId(), targetNameReal);

        if (cfg.getBoolean("referral.prevent-circular", true) && user.getUniqueId().equals(targetData.referredBy())) {
            lang().send(user, "referral.circular", "%player%", targetNameReal);
            sound(user, "error");
            return;
        }

        boolean bypass = dev.tmdevelopment.flarereferrals.util.Perms.has(user, "bypass");
        int minMinutes = cfg.getInt("referral.min-playtime-minutes", 0);
        if (!bypass && minMinutes > 0) {
            int played = user.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20 / 60;
            if (played < minMinutes) {
                lang().send(user, "referral.need-playtime",
                        "%required%", String.valueOf(minMinutes), "%played%", String.valueOf(played));
                sound(user, "error");
                return;
            }
        }
        if (!bypass && cfg.getBoolean("referral.block-same-ip", true)) {
            String myIp = ipHash(user);
            userData.ipHash(myIp);
            if (myIp != null && myIp.equals(targetData.ipHash())) {
                lang().send(user, "referral.same-ip", "%player%", targetNameReal);
                sound(user, "error");
                return;
            }
        }

        // ---- success
        userData.referredBy(target.getUniqueId());
        userData.referredAt(System.currentTimeMillis());
        targetData.invites(targetData.invites() + 1);

        lang().send(user, "referral.success", "%player%", targetNameReal);
        sound(user, "success");
        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null) {
            lang().send(onlineTarget, "referral.received",
                    "%player%", user.getName(), "%invites%", String.valueOf(targetData.invites()));
            sound(onlineTarget, "success");
        }
        if (cfg.getBoolean("referral.broadcast", false)) {
            for (String line : lang().lines("referral.broadcast")) {
                dev.tmdevelopment.flarereferrals.util.Platform.broadcast(Text.parse(Text.replace(line, "%player%", user.getName(),
                        "%inviter%", targetNameReal, "%invites%", String.valueOf(targetData.invites())), null));
            }
        }

        String ctx = "|" + targetNameReal + "|" + user.getName();
        give(user.getUniqueId(), "referral:invited" + ctx);
        give(target.getUniqueId(), "referral:inviter" + ctx);
        checkMilestones(targetData, ctx);
        plugin.data().save();
    }

    // ------------------------------------------------------------------ milestones

    /** Called after the invites of a player went up by one. */
    public void checkMilestones(PlayerData data, String ctx) {
        if (!plugin.getConfig().getBoolean("milestones.enabled", true)) return;
        int inv = data.invites();
        for (Milestone m : plugin.rewards().milestoneList()) {
            if (!m.enabled()) continue;
            if (m.repeat()) {
                if (inv > 0 && inv % m.amount() == 0) reach(data, m, ctx);
            } else if (inv >= m.amount() && !data.claimed().contains(m.amount())) {
                data.claimed().add(m.amount());
                reach(data, m, ctx);
            }
        }
    }

    public boolean claimMode() {
        return plugin.getConfig().getBoolean("milestones.claim-in-menu", true);
    }

    /** A milestone was reached: give it now, or keep it to be collected (claim mode). */
    private void reach(PlayerData data, Milestone m, String ctx) {
        if (!claimMode()) {
            give(data.uuid(), "milestone:" + m.amount() + ctx);
            return;
        }
        data.claimable().add(m.amount());
        Player p = Bukkit.getPlayer(data.uuid());
        if (p != null) {
            lang().send(p, "milestone.ready", "%name%", m.name(), "%amount%", String.valueOf(m.amount()));
            sound(p, "success");
        }
    }

    /** Gives one reached milestone to the player. @return false when there is nothing to claim. */
    public boolean claim(Player p, int amount) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null || !d.claimable().remove(Integer.valueOf(amount))) return false;
        Milestone m = plugin.rewards().milestone(amount);
        if (m != null && m.enabled()) {
            deliver(p, m, "milestone:" + amount + "|-|-");
        }
        plugin.data().save();
        return true;
    }

    /** Gives every reached milestone. @return how many. */
    public int claimAll(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return 0;
        int n = 0;
        for (Integer a : new java.util.ArrayList<>(d.claimable())) if (claim(p, a)) n++;
        return n;
    }

    /** On join: reminder of rewards waiting to be collected. */
    public void remindClaims(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d != null && !d.claimable().isEmpty()) {
            lang().send(p, "milestone.remind", "%amount%", String.valueOf(d.claimable().size()));
        }
    }

    /** Admin add: increments one by one so repeating milestones trigger correctly. */
    public void addInvites(PlayerData data, int amount) {
        for (int i = 0; i < amount; i++) {
            data.invites(data.invites() + 1);
            checkMilestones(data, "|" + data.name() + "|-");
        }
        plugin.data().save();
    }

    public Milestone nextMilestone(int invites) {
        Milestone best = null;
        int bestTarget = Integer.MAX_VALUE;
        for (Milestone m : plugin.rewards().milestones()) {
            if (!m.enabled()) continue;
            int t = nextTarget(m, invites);
            if (t > invites && t < bestTarget) {
                best = m;
                bestTarget = t;
            }
        }
        return best;
    }

    public int nextTarget(Milestone m, int invites) {
        if (!m.repeat()) return m.amount();
        return (invites / m.amount() + 1) * m.amount();
    }

    // ------------------------------------------------------------------ delivery

    /** Gives a reward now if online, otherwise queues it (if enabled). key format "set|inviter|invited". */
    public void give(UUID uuid, String keyWithCtx) {
        String key = keyWithCtx.split("\\|", 2)[0];
        RewardSet set = plugin.rewards().resolve(key);
        if (set == null || !set.enabled()) return;
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            deliver(p, set, keyWithCtx);
        } else if (plugin.getConfig().getBoolean("rewards.offline-queue", true)) {
            PlayerData d = plugin.data().getOrCreate(uuid, null);
            d.pending().add(keyWithCtx);
        }
    }

    public void deliverPending(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null || d.pending().isEmpty()) return;
        java.util.List<String> list = new java.util.ArrayList<>(d.pending());
        d.pending().clear();
        lang().send(p, "reward.pending", "%amount%", String.valueOf(list.size()));
        for (String entry : list) {
            RewardSet set = plugin.rewards().resolve(entry.split("\\|", 2)[0]);
            if (set != null && set.enabled()) deliver(p, set, entry);
        }
        plugin.data().save();
    }

    public void deliver(Player p, RewardSet set, String keyWithCtx) {
        String[] parts = keyWithCtx.split("\\|", -1);
        String inviter = parts.length > 1 ? parts[1] : "";
        String invited = parts.length > 2 ? parts[2] : "";
        PlayerData d = plugin.data().get(p.getUniqueId());
        String milestone = set instanceof Milestone ? String.valueOf(((Milestone) set).amount()) : "";
        String[] ph = {
                "%player%", p.getName(),
                "%uuid%", p.getUniqueId().toString(),
                "%invites%", String.valueOf(d == null ? 0 : d.invites()),
                "%milestone%", milestone,
                "%inviter%", inviter,
                "%invited%", invited
        };

        boolean drop = plugin.getConfig().getBoolean("rewards.drop-if-full", true);
        for (ItemStack it : set.items()) {
            if (it == null || it.getType().isAir()) continue;
            Map<Integer, ItemStack> left = p.getInventory().addItem(it.clone());
            if (drop) {
                for (ItemStack l : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
            }
        }
        for (Map.Entry<String, Double> e : set.currencies().entrySet()) {
            plugin.currencies().give(p, e.getKey(), e.getValue());
        }
        for (String cmd : set.commands()) {
            String c = Text.replace(cmd, ph);
            if (c.startsWith("/")) c = c.substring(1);
            if (!dev.tmdevelopment.flarereferrals.util.J8.blank(c)) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
        }
        lang().sendRaw(p, set.messages(), ph);
        for (String line : set.broadcast()) {
            if (line == null || line.isEmpty()) continue;
            dev.tmdevelopment.flarereferrals.util.Platform.broadcast(Text.parse(Text.replace(line.replace("%prefix%", lang().prefix()).replace("{prefix}", lang().prefix()), ph), p));
        }
        ScreenEffects.show(plugin, p, set.display(), ph);
        sound(p, "reward");
    }

    // ------------------------------------------------------------------ utils

    public void sound(Player p, String type) {
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("sounds.enabled", true)) return;
        String key = cfg.getString("sounds." + type, "");
        if (key == null || dev.tmdevelopment.flarereferrals.util.J8.blank(key)) return;
        try {
            p.playSound(p.getLocation(), key.toLowerCase(), 1f, 1f);
        } catch (Exception ignored) {
        }
    }

    public String ipHash(Player p) {
        if (p.getAddress() == null || p.getAddress().getAddress() == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(("flares:" + p.getAddress().getAddress().getHostAddress())
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 12; i++) hex.append(String.format("%02x", h[i] & 0xff));
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
