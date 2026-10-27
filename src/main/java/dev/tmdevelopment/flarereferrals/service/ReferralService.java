package dev.tmdevelopment.flarereferrals.service;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.config.Lang;
import dev.tmdevelopment.flarereferrals.data.PlayerData;
import dev.tmdevelopment.flarereferrals.data.RewardEntry;
import dev.tmdevelopment.flarereferrals.reward.Milestone;
import dev.tmdevelopment.flarereferrals.reward.RewardSet;
import dev.tmdevelopment.flarereferrals.storage.Ops;
import dev.tmdevelopment.flarereferrals.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;

public final class ReferralService {

    private static final char SEP = '\u001f';

    private final FlareReferrals plugin;
    /** Players whose referral / verification is being saved right now (no double clicks). */
    private final Set<UUID> busy = new HashSet<>();
    /** Rewards being given right now. */
    private final Set<Long> giving = new HashSet<>();

    public ReferralService(FlareReferrals plugin) {
        this.plugin = plugin;
    }

    private Lang lang() { return plugin.lang(); }
    private FileConfiguration cfg() { return plugin.getConfig(); }

    // ------------------------------------------------------------------ rules snapshot (read on the main thread)

    /** Settings copied before a change runs on the database thread (it can't read the config there). */
    private static final class Rules {
        boolean milestones;
        boolean inviter;
        boolean invited;
        boolean inviterMenu;
        boolean invitedMenu;
        boolean queue;
        final List<int[]> list = new ArrayList<>();
    }

    private Rules rules() {
        Rules r = new Rules();
        r.milestones = cfg().getBoolean("milestones.enabled", true);
        r.inviter = plugin.rewards().inviter().enabled();
        r.invited = plugin.rewards().invited().enabled();
        r.inviterMenu = inMenu(plugin.rewards().inviter());
        r.invitedMenu = inMenu(plugin.rewards().invited());
        r.queue = plugin.data().network() || cfg().getBoolean("rewards.offline-queue", true);
        // turned off rewards are saved too: they wait and are given when the reward is turned on again
        for (Milestone m : plugin.rewards().milestoneList()) {
            r.list.add(new int[]{m.amount(), m.repeat() ? 1 : 0, inMenu(m) ? 1 : 0, m.enabled() ? 1 : 0});
        }
        return r;
    }

    private static final int OK = 0, OK_PENDING = 1, ALREADY = 2, CIRCULAR = 3, SAME_IP = 4;

    private static final class Outcome {
        int code;
        int invites;
        /** New rewards waiting in the collect menu of the inviter / of the new player. */
        final List<RewardEntry> ready = new ArrayList<>();
        final List<RewardEntry> readyUser = new ArrayList<>();

        Outcome(int code) {
            this.code = code;
        }
    }

    // ------------------------------------------------------------------ referral

    public void refer(Player user, String targetName) {
        FileConfiguration cfg = cfg();
        if (!cfg.getBoolean("referral.enabled", true)) {
            lang().send(user, "referral.disabled");
            sound(user, "error");
            return;
        }
        UUID userId = user.getUniqueId();
        if (busy.contains(userId)) return;
        PlayerData userData = plugin.data().get(userId);
        if (userData != null && userData.referredBy() != null) {
            alreadyReferred(user, userData);
            return;
        }

        // the inviter: anyone with data (on a network: any server), or a player this server knows
        PlayerData targetData = plugin.data().byName(targetName);
        UUID targetId;
        String targetReal;
        if (targetData != null) {
            targetId = targetData.uuid();
            targetReal = targetData.name();
        } else {
            OfflinePlayer t = Bukkit.getPlayerExact(targetName);
            if (t == null) t = dev.tmdevelopment.flarereferrals.util.Compat.knownPlayer(targetName);
            if (t == null || (!t.isOnline() && !t.hasPlayedBefore())) {
                lang().send(user, "referral.not-found", "%player%", targetName);
                sound(user, "error");
                return;
            }
            targetId = t.getUniqueId();
            targetReal = t.getName() == null ? targetName : t.getName();
        }
        if (targetId.equals(userId)) {
            dev.tmdevelopment.flarereferrals.util.ReferralLog.log("BLOCKED", user.getName() + " -> " + targetReal + " (himself)");
            lang().send(user, "referral.self");
            sound(user, "error");
            return;
        }
        boolean circular = cfg.getBoolean("referral.prevent-circular", true);
        if (circular && targetData != null && userId.equals(targetData.referredBy())) {
            dev.tmdevelopment.flarereferrals.util.ReferralLog.log("BLOCKED", user.getName() + " -> " + targetReal + " (circular: " + targetReal + " was invited by him)");
            lang().send(user, "referral.circular", "%player%", targetReal);
            sound(user, "error");
            return;
        }

        boolean bypass = dev.tmdevelopment.flarereferrals.util.Perms.has(user, "bypass");
        int played = playtimeMinutes(user, userData);
        int minMinutes = cfg.getInt("referral.min-playtime-minutes", 0);
        if (!bypass && minMinutes > 0 && played < minMinutes) {
            dev.tmdevelopment.flarereferrals.util.ReferralLog.log("BLOCKED", user.getName() + " -> " + targetReal + " (playtime " + played + "/" + minMinutes + " min)");
            lang().send(user, "referral.need-playtime",
                    "%required%", String.valueOf(minMinutes), "%played%", String.valueOf(played));
            sound(user, "error");
            return;
        }
        int maxDays = cfg.getInt("referral.max-days-after-first-join", 0);
        long first = firstSeen(user, userData);
        if (!bypass && maxDays > 0 && first > 0 && System.currentTimeMillis() - first > maxDays * 86_400_000L) {
            dev.tmdevelopment.flarereferrals.util.ReferralLog.log("BLOCKED", user.getName() + " -> " + targetReal + " (more than " + maxDays + " days after the first join)");
            lang().send(user, "referral.too-late", "%days%", String.valueOf(maxDays));
            sound(user, "error");
            return;
        }
        boolean sameIp = !bypass && cfg.getBoolean("referral.block-same-ip", true);
        // the hash is always saved, so the check also works for players who joined while it was off
        String myIp = ipHash(user);
        Player targetOnline = Bukkit.getPlayer(targetId);
        final String targetIpNow = targetOnline != null ? ipHash(targetOnline) : null;
        if (sameIp && myIp != null && (myIp.equals(targetIpNow) || (targetData != null && myIp.equals(targetData.ipHash())))) {
            dev.tmdevelopment.flarereferrals.util.ReferralLog.log("BLOCKED", user.getName() + " -> " + targetReal + " (same IP)");
            lang().send(user, "referral.same-ip", "%player%", targetReal);
            sound(user, "error");
            return;
        }

        // other plugins can block the invite (they tell the player why)
        dev.tmdevelopment.flarereferrals.api.ReferralEvent event = new dev.tmdevelopment.flarereferrals.api.ReferralEvent(user, targetId, targetReal);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            dev.tmdevelopment.flarereferrals.util.ReferralLog.log("BLOCKED", user.getName() + " -> " + targetReal + " (cancelled by another plugin)");
            return;
        }

        int verifyMin = cfg.getInt("referral.verify-after-minutes", 0);
        boolean pending = !bypass && verifyMin > 0 && played < verifyMin;
        Rules r = rules();
        boolean targetQueue = r.queue || Bukkit.getPlayer(targetId) != null;
        long now = System.currentTimeMillis();
        String ctx = "|" + targetReal + "|" + user.getName();
        String userName = user.getName();
        final boolean known = targetData != null;

        busy.add(userId);
        plugin.data().run(ops -> {
            ops.touch(userId, userName, myIp, first);
            if (!known || ops.player(targetId) == null) ops.touch(targetId, targetReal, null, 0);
            PlayerData t = ops.player(targetId);
            if (circular && t != null && userId.equals(t.referredBy())) return new Outcome(CIRCULAR);
            if (sameIp && myIp != null && (myIp.equals(targetIpNow) || (t != null && myIp.equals(t.ipHash())))) return new Outcome(SAME_IP);
            if (!ops.setReferrer(userId, targetId, now, pending ? PlayerData.PENDING : PlayerData.VERIFIED)) {
                return new Outcome(ALREADY);
            }
            if (pending) return new Outcome(OK_PENDING);
            return complete(ops, r, userId, targetId, ctx, targetQueue);
        }, out -> {
            busy.remove(userId);
            Player u = Bukkit.getPlayer(userId);
            if (out == null) {
                if (u != null) lang().send(u, "general.database-error");
                return;
            }
            if (u == null) return;
            switch (out.code) {
                case ALREADY:
                    PlayerData d = plugin.data().get(userId);
                    if (d != null) alreadyReferred(u, d);
                    break;
                case CIRCULAR:
                    dev.tmdevelopment.flarereferrals.util.ReferralLog.log("BLOCKED", userName + " -> " + targetReal + " (circular)");
                    lang().send(u, "referral.circular", "%player%", targetReal);
                    sound(u, "error");
                    break;
                case SAME_IP:
                    dev.tmdevelopment.flarereferrals.util.ReferralLog.log("BLOCKED", userName + " -> " + targetReal + " (same IP)");
                    lang().send(u, "referral.same-ip", "%player%", targetReal);
                    sound(u, "error");
                    break;
                case OK_PENDING:
                    dev.tmdevelopment.flarereferrals.util.ReferralLog.log("PENDING", userName + " -> " + targetReal + " (counts after " + verifyMin + " min of play)");
                    lang().send(u, "referral.pending", "%player%", targetReal,
                            "%minutes%", String.valueOf(Math.max(1, verifyMin - played)));
                    sound(u, "success");
                    break;
                default:
                    dev.tmdevelopment.flarereferrals.util.ReferralLog.log("INVITE", userName + " -> " + targetReal + " (" + targetReal + " has " + out.invites + " invites)");
                    lang().send(u, "referral.success", "%player%", targetReal);
                    sound(u, "success");
                    completed(userId, userName, targetId, targetReal, out);
            }
        });
    }

    private void alreadyReferred(Player user, PlayerData userData) {
        PlayerData by = plugin.data().get(userData.referredBy());
        lang().send(user, "referral.already-referred", "%player%", by == null ? "?" : by.name());
        sound(user, "error");
    }

    /** The invite counts: +1 to the inviter and every reward created. Runs inside the transaction. */
    private Outcome complete(Ops ops, Rules r, UUID user, UUID target, String ctx, boolean targetQueue) {
        Outcome out = new Outcome(OK);
        out.invites = ops.addInvites(target, 1);
        // r.inviter / r.invited = turned on: a turned off reward is saved anyway and waits (no message now)
        RewardEntry ue = ops.addReward(user, "referral:invited", "referral:invited" + ctx,
                r.invitedMenu ? RewardEntry.State.CLAIMABLE : RewardEntry.State.PENDING);
        if (ue != null && r.invitedMenu && r.invited) out.readyUser.add(ue);
        if (targetQueue || r.inviterMenu) {
            RewardEntry e = ops.addReward(target, "referral:inviter@" + user, "referral:inviter" + ctx,
                    r.inviterMenu ? RewardEntry.State.CLAIMABLE : RewardEntry.State.PENDING);
            if (e != null && r.inviterMenu && r.inviter) out.ready.add(e);
        }
        milestones(ops, r, target, out.invites, ctx, targetQueue, out);
        return out;
    }

    /** Creates the milestone rewards for a new amount of invites. A reward can't be created twice (uniq key). */
    private void milestones(Ops ops, Rules r, UUID target, int invites, String ctx, boolean queue, Outcome out) {
        if (!r.milestones) return;
        for (int[] m : r.list) {
            int amount = m[0];
            boolean repeat = m[1] == 1;
            String uniq;
            if (repeat) {
                if (invites <= 0 || invites % amount != 0) continue;
                // only the server that raised the invites to this number gets here: every time is a new reward
                uniq = "milestone:" + amount + "@" + invites + "-" + Long.toString(System.nanoTime() & 0xffffffL, 36)
                        + Integer.toString(java.util.concurrent.ThreadLocalRandom.current().nextInt(1 << 20), 36);
            } else {
                if (invites < amount) continue;
                uniq = "milestone:" + amount;
            }
            RewardEntry.State state = m[2] == 1 ? RewardEntry.State.CLAIMABLE : RewardEntry.State.PENDING;
            if (state == RewardEntry.State.PENDING && !queue) {
                // offline and the queue is off: lost, like before. A non repeating milestone counts as reached anyway
                if (!repeat) ops.addReward(target, uniq, "milestone:" + amount + ctx, RewardEntry.State.DELIVERED);
                continue;
            }
            RewardEntry e = ops.addReward(target, uniq, "milestone:" + amount + ctx, state);
            if (e != null && state == RewardEntry.State.CLAIMABLE && m[3] == 1) out.ready.add(e);
        }
    }

    /** Messages + rewards after an invite counted (also after a verification). */
    private void completed(UUID userId, String userName, UUID targetId, String targetReal, Outcome out) {
        try {
            Bukkit.getPluginManager().callEvent(new dev.tmdevelopment.flarereferrals.api.ReferralCompleteEvent(
                    userId, userName, targetId, targetReal, out.invites));
        } catch (Throwable t) {
            plugin.getLogger().warning("A plugin listening to ReferralCompleteEvent failed: " + t);
        }
        refreshMenu(userId);
        refreshMenu(targetId);
        Player u = Bukkit.getPlayer(userId);
        if (u != null) deliverPending(u, false);
        notifyCollect(userId, out.readyUser, false); // "success" sound already played
        notify(targetId, "referral.received", "%player%", userName, "%invites%", String.valueOf(out.invites));
        notifyCollect(targetId, out.ready, false);
        Player t = Bukkit.getPlayer(targetId);
        if (t != null) deliverPending(t, false);
        if (cfg().getBoolean("referral.broadcast", false)) {
            for (String line : lang().lines("referral.broadcast")) {
                broadcast(Text.replace(line, "%player%", userName, "%inviter%", targetReal,
                        "%invites%", String.valueOf(out.invites)));
            }
        }
    }

    // ------------------------------------------------------------------ verification (referral.verify-after-minutes)

    /** Called every minute for online players and on join: a pending invite counts once he played enough. */
    public void checkVerify(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null || !d.pendingInvite() || d.referredBy() == null) return;
        int verifyMin = cfg().getInt("referral.verify-after-minutes", 0);
        if (verifyMin > 0 && playtimeMinutes(p, d) < verifyMin) return;
        UUID userId = p.getUniqueId();
        if (!busy.add(userId)) return;
        UUID targetId = d.referredBy();
        PlayerData td = plugin.data().get(targetId);
        String targetReal = td == null ? "?" : td.name();
        String userName = p.getName();
        Rules r = rules();
        boolean targetQueue = r.queue || Bukkit.getPlayer(targetId) != null;
        String ctx = "|" + targetReal + "|" + userName;
        plugin.data().run(ops -> {
            if (!ops.verify(userId)) return new Outcome(ALREADY);
            return complete(ops, r, userId, targetId, ctx, targetQueue);
        }, out -> {
            busy.remove(userId);
            if (out == null || out.code != OK) return;
            dev.tmdevelopment.flarereferrals.util.ReferralLog.log("INVITE", userName + " -> " + targetReal + " (after the playtime check, " + targetReal + " has " + out.invites + " invites)");
            Player u = Bukkit.getPlayer(userId);
            if (u != null) {
                lang().send(u, "referral.verified", "%player%", targetReal);
                sound(u, "success");
            }
            completed(userId, userName, targetId, targetReal, out);
        });
    }

    // ------------------------------------------------------------------ milestones

    /** true when at least one reward waits in the collect menu (help line, statistics). */
    public boolean claimMode() {
        if (inMenu(plugin.rewards().inviter()) || inMenu(plugin.rewards().invited())) return true;
        for (Milestone m : plugin.rewards().milestones()) if (inMenu(m)) return true;
        return false;
    }

    /** Does this reward wait in /referrals collect (true) or is it given right away (false)? */
    public boolean inMenu(RewardSet set) {
        // menu turned off for players: nobody could open it, so rewards are given right away
        if (!cfg().getBoolean("player-access.collect", true) || !plugin.layouts().get("collect").enabled()) return false;
        if ("menu".equals(set.delivery())) return true;
        if ("auto".equals(set.delivery())) return false;
        return cfg().getBoolean("rewards.collect-menu", true);
    }

    /** Name of a reward for menus and messages. */
    public String rewardName(RewardEntry e) {
        RewardSet set = plugin.rewards().resolve(e.key());
        if (set instanceof Milestone) return ((Milestone) set).name();
        if (e.key().equals("referral:inviter")) return lang().raw("gui.names.inviter");
        if (e.key().equals("referral:invited")) return lang().raw("gui.names.invited");
        return e.key();
    }

    /** "Something to collect" (one notice for all the new rewards): here when he is online here, otherwise on his server. */
    private void notifyCollect(UUID owner, List<RewardEntry> list, boolean sound) {
        if (list.isEmpty()) return;
        RewardEntry first = list.get(0);
        String name = list.size() == 1 ? rewardName(first)
                : Text.replace(lang().raw("collect.several"), "%amount%", String.valueOf(list.size()));
        Player p = Bukkit.getPlayer(owner);
        if (p != null) {
            // several rewards: the notice follows one that can be collected here, if there is one
            for (RewardEntry e : list) {
                RewardSet set = plugin.rewards().resolve(e.key());
                if (set != null && allowedHere(set)) {
                    collectNotice(p, e.key(), name, sound, null);
                    return;
                }
            }
            // none here: say every server where they can be collected
            java.util.Set<String> where = new java.util.LinkedHashSet<>();
            for (RewardEntry e : list) {
                RewardSet set = plugin.rewards().resolve(e.key());
                if (set != null) where.add(serversText(set));
            }
            if (where.size() > 1) where.remove(lang().raw("milestone.no-server"));
            collectNotice(p, first.key(), name, sound, String.join(", ", where));
            return;
        }
        if (plugin.data().network()) plugin.data().publish("collect", owner, first.key() + SEP + name);
    }

    /** Chat [Collect] + title / action bar / sound, as set in config.yml rewards.notify. */
    public void collectNotice(Player p, String key, String rewardName, boolean sound) {
        collectNotice(p, key, rewardName, sound, null);
    }

    /** @param servers where to collect it, or null for the servers of the reward */
    private void collectNotice(Player p, String key, String rewardName, boolean sound, String servers) {
        FileConfiguration cfg = cfg();
        lastNotice.put(p.getUniqueId(), System.currentTimeMillis()); // no "you have rewards" right after this
        RewardSet set = plugin.rewards().resolve(key);
        String[] ph = {"%reward%", rewardName, "%amount%", String.valueOf(collectableHere(p)),
                "%servers%", servers != null ? servers : set == null ? "" : serversText(set)};
        if (set != null && !allowedHere(set)) {
            // to collect on another server: just say where
            if (cfg.getBoolean("rewards.notify.chat", true)) lang().send(p, "collect.new-other", ph);
            return;
        }
        boolean menu = plugin.canOpen(p, "collect");
        if (cfg.getBoolean("rewards.notify.chat", true)) lang().send(p, menu ? "collect.new" : "collect.new-claim", ph);
        if (cfg.getBoolean("rewards.notify.title", false)) {
            dev.tmdevelopment.flarereferrals.reward.Display d = new dev.tmdevelopment.flarereferrals.reward.Display();
            d.title(lang().raw("collect.title"));
            d.subtitle(lang().raw("collect.subtitle"));
            ScreenEffects.show(plugin, p, d, ph);
        }
        if (cfg.getBoolean("rewards.notify.actionbar", true)) {
            dev.tmdevelopment.flarereferrals.reward.Display d = new dev.tmdevelopment.flarereferrals.reward.Display();
            d.actionbar(lang().raw("collect.actionbar"));
            ScreenEffects.show(plugin, p, d, ph);
        }
        if (sound && cfg.getBoolean("rewards.notify.sound", true)) sound(p, "success");
    }

    /** Rewards this player can collect on this server. */
    public int collectableHere(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return 0;
        int n = 0;
        for (RewardEntry e : d.claimableEntries()) {
            RewardSet set = plugin.rewards().resolve(e.key());
            if (set != null && set.enabled() && allowedHere(set)) n++;
        }
        return n;
    }

    /**
     * Collects one reward from the menu. Keeps it there when the items don't fit in the inventory.
     * @return false when it can't be collected now (message already sent)
     */
    public boolean collectEntry(Player p, RewardEntry e, Runnable after) {
        if (giving.contains(e.id()) || e.state() != RewardEntry.State.CLAIMABLE) return false;
        RewardSet set = plugin.rewards().resolve(e.key());
        if (set != null && set.enabled() && allowedHere(set) && !fits(p, set)) {
            lang().send(p, "collect.inventory-full", "%reward%", rewardName(e));
            sound(p, "error");
            return false;
        }
        List<RewardEntry> one = new ArrayList<>();
        one.add(e);
        collect(p, one, after);
        return true;
    }

    /** Free inventory slots for the items of a reward (very big rewards are dropped as before). */
    private boolean fits(Player p, RewardSet set) {
        int items = itemCount(set);
        if (items == 0 || items > 36) return true;
        return freeSlots(p) >= items;
    }

    /** Inventory slots the items of a reward need (an amount above the stack size needs more slots). */
    private static int itemCount(RewardSet set) {
        int n = 0;
        for (ItemStack it : set.items()) {
            if (it == null || it.getType().isAir()) continue;
            int max = Math.max(1, it.getMaxStackSize());
            n += Math.max(1, (it.getAmount() + max - 1) / max);
        }
        return n;
    }

    private static int freeSlots(Player p) {
        int free = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (it == null || it.getType().isAir()) free++;
        return free;
    }

    /**
     * Gives one reached milestone to the player. {@code after} runs when done (menu refresh).
     * @return false when there is nothing to collect.
     */
    public boolean claim(Player p, int amount, Runnable after) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return false;
        RewardEntry entry = null;
        for (RewardEntry e : d.claimableEntries()) {
            if (e.milestone() == amount && !giving.contains(e.id())) {
                entry = e;
                break;
            }
        }
        if (entry == null) return false;
        if (!collectEntry(p, entry, after) && after != null) after.run();
        return true;
    }

    /** Gives every reached milestone this server can give. @return how many were found. */
    public int claimAll(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return 0;
        return claimAll(p, null);
    }

    /** Collects everything that fits in the inventory, the rest stays in the menu. @return how many were found. */
    public int claimAll(Player p, Runnable after) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return 0;
        List<RewardEntry> list = new ArrayList<>();
        int found = 0;
        int free = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (it == null || it.getType().isAir()) free++;
        boolean full = false;
        for (RewardEntry e : d.claimableEntries()) {
            RewardSet set = plugin.rewards().resolve(e.key());
            // hidden (reward off / deleted) or for another server: left where it is
            if (set == null || !set.enabled() || !allowedHere(set)) continue;
            found++;
            if (giving.contains(e.id())) continue; // already being collected (double click)
            int need = itemCount(set);
            if (need > 36) need = 0;
            if (need > free) {
                full = true;
                continue;
            }
            free -= need;
            list.add(e);
        }
        if (full) {
            lang().send(p, "collect.inventory-full-some");
            sound(p, "error");
        }
        if (list.isEmpty()) {
            if (after != null) after.run();
            return found;
        }
        collect(p, list, after);
        return found;
    }

    private void collect(Player p, List<RewardEntry> list, Runnable after) {
        UUID uuid = p.getUniqueId();
        List<RewardEntry> give = new ArrayList<>();
        List<RewardEntry> drop = new ArrayList<>();
        RewardSet elsewhere = null;
        for (RewardEntry e : list) {
            RewardSet set = plugin.rewards().resolve(e.key());
            // a reward turned off or deleted stays waiting (it comes back if it is turned on again)
            if (set == null || !set.enabled()) continue;
            if (allowedHere(set)) give.add(e);
            else if (elsewhere == null) elsewhere = set;
        }
        if (elsewhere != null) {
            lang().send(p, "collect.other-server", "%servers%", serversText(elsewhere));
            sound(p, "error");
        }
        if (give.isEmpty() && drop.isEmpty()) {
            if (after != null) after.run();
            return;
        }
        take(p, give, drop, RewardEntry.State.CLAIMABLE, false, after);
    }

    /** On join: reminder of rewards waiting to be collected. */
    public void remindClaims(Player p) {
        Long last = lastNotice.get(p.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < 30_000L) return; // just told about a new reward
        int n = collectableHere(p);
        if (n > 0) lang().send(p, plugin.canOpen(p, "collect") ? "collect.remind" : "collect.remind-claim", "%amount%", String.valueOf(n));
    }

    /**
     * A reward was turned on again (menu or reload): what waited while it was off is given to the online players
     * now ("right away" rewards) or announced ("collect menu" rewards).
     */
    public void rewardTurnedOn(RewardSet set) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.data().get(p.getUniqueId());
            if (d == null) continue;
            boolean waits = false;
            for (RewardEntry e : d.rewards()) {
                if (e.state() == RewardEntry.State.CLAIMABLE && plugin.rewards().resolve(e.key()) == set) waits = true;
            }
            deliverPending(p, false);
            if (waits) {
                lastNotice.remove(p.getUniqueId());
                remindClaims(p);
            }
        }
    }

    /** Admin add: one by one so repeating milestones trigger correctly. {@code done} gets the new amount (-1 = error). */
    public void addInvites(PlayerData data, int amount, IntConsumer done) {
        UUID target = data.uuid();
        Rules r = rules();
        boolean queue = r.queue || Bukkit.getPlayer(target) != null;
        String ctx = "|" + data.name() + "|-";
        plugin.data().run(ops -> {
            Outcome out = new Outcome(OK);
            for (int i = 0; i < amount; i++) {
                out.invites = ops.addInvites(target, 1);
                milestones(ops, r, target, out.invites, ctx, queue, out);
            }
            return out;
        }, out -> {
            if (out == null) {
                if (done != null) done.accept(-1);
                return;
            }
            notifyCollect(target, out.ready, true);
            Player t = Bukkit.getPlayer(target);
            if (t != null) deliverPending(t, false);
            if (done != null) done.accept(out.invites);
        });
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

    /** Gives the rewards waiting for this player that this server can give. */
    public void deliverPending(Player p, boolean announce) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return;
        List<RewardEntry> give = new ArrayList<>();
        List<RewardEntry> drop = new ArrayList<>();
        for (RewardEntry e : d.pending()) {
            if (giving.contains(e.id())) continue;
            RewardSet set = plugin.rewards().resolve(e.key());
            // a reward turned off, deleted or with a changed amount stays waiting: it is given if it comes back
            if (set != null && set.enabled() && allowedHere(set)) give.add(e);
        }
        if (give.isEmpty() && drop.isEmpty()) return;
        take(p, give, drop, RewardEntry.State.PENDING, announce, null);
    }

    /**
     * Takes the rewards for this server (another server can't take them at the same time), gives them,
     * then marks them as given. Rewards in {@code drop} are closed without giving anything.
     */
    private void take(Player p, List<RewardEntry> give, List<RewardEntry> drop, RewardEntry.State from,
                      boolean announce, Runnable after) {
        UUID uuid = p.getUniqueId();
        String server = plugin.data().server();
        for (RewardEntry e : give) giving.add(e.id());
        for (RewardEntry e : drop) giving.add(e.id());
        plugin.data().run(ops -> {
            List<RewardEntry> taken = new ArrayList<>();
            for (RewardEntry e : give) {
                if (ops.moveReward(uuid, e.id(), from, RewardEntry.State.DELIVERING, server)) {
                    taken.add(e);
                    plugin.data().taken(uuid, e.id(), from);
                }
            }
            for (RewardEntry e : drop) ops.moveReward(uuid, e.id(), from, RewardEntry.State.DELIVERED, server);
            return taken;
        }, taken -> {
            for (RewardEntry e : give) giving.remove(e.id());
            for (RewardEntry e : drop) giving.remove(e.id());
            if (taken == null) {
                for (RewardEntry e : give) plugin.data().handed(e.id());
                Player now = Bukkit.getPlayer(uuid);
                if (now != null) lang().send(now, "general.database-error");
                if (after != null) after.run();
                return;
            }
            if (taken.isEmpty()) {
                if (after != null) after.run();
                return;
            }
            for (RewardEntry e : taken) plugin.data().handed(e.id());
            Player now = Bukkit.getPlayer(uuid);
            if (now == null) {
                // logged out in the meantime: back in the queue
                plugin.data().finish(ops -> {
                    for (RewardEntry e : taken) ops.moveReward(uuid, e.id(), RewardEntry.State.DELIVERING, from, server);
                    return Boolean.TRUE;
                }, null);
                return;
            }
            if (announce && from == RewardEntry.State.PENDING) {
                lang().send(now, "reward.pending", "%amount%", String.valueOf(taken.size()));
            }
            List<RewardEntry> back = new ArrayList<>();
            int free = from == RewardEntry.State.CLAIMABLE ? freeSlots(now) : Integer.MAX_VALUE;
            for (RewardEntry e : taken) {
                RewardSet set = plugin.rewards().resolve(e.key());
                if (set != null && from == RewardEntry.State.CLAIMABLE) {
                    int need = itemCount(set);
                    if (need > 36) need = 0;
                    if (need > free) {
                        // the inventory filled up meanwhile: back in the menu
                        back.add(e);
                        continue;
                    }
                    free -= need;
                }
                if (set != null) {
                    try {
                        deliver(now, set, e.reward());
                    } catch (Throwable t) {
                        plugin.getLogger().warning("Error while giving " + e.key() + " to " + now.getName() + ": " + t.getMessage());
                    }
                }
            }
            if (!back.isEmpty()) {
                lang().send(now, "collect.inventory-full-some");
                sound(now, "error");
            }
            plugin.data().finish(ops -> {
                for (RewardEntry e : taken) {
                    ops.moveReward(uuid, e.id(), RewardEntry.State.DELIVERING,
                            back.contains(e) ? from : RewardEntry.State.DELIVERED, server);
                }
                return Boolean.TRUE;
            }, x -> {
                if (after != null) after.run();
            });
        });
    }

    /** Network: can this server give this reward? */
    public boolean allowedHere(RewardSet set) {
        if (!plugin.data().network()) return true;
        if (!cfg().getBoolean("network.rewards-here", true)) return false;
        return set.allServers() || set.servers().contains(plugin.data().server().toLowerCase(java.util.Locale.ROOT));
    }

    public String serversText(RewardSet set) {
        boolean here = cfg().getBoolean("network.rewards-here", true);
        if (set.allServers()) return here ? "" : lang().raw("milestone.any-other-server");
        List<String> list = new ArrayList<>(set.servers());
        // this server gives no rewards: don't send the player here
        if (!here && plugin.data().network()) list.remove(plugin.data().server().toLowerCase(java.util.Locale.ROOT));
        if (list.isEmpty()) return lang().raw("milestone.no-server");
        return String.join(", ", list);
    }

    /**
     * Console warning for rewards nobody can collect: only this server is in "servers" of the reward,
     * but this server has network.rewards-here: false. Called after the files are loaded.
     */
    public void checkServers() {
        if (!plugin.data().network() || cfg().getBoolean("network.rewards-here", true)) return;
        String me = plugin.data().server().toLowerCase(java.util.Locale.ROOT);
        List<RewardSet> sets = new ArrayList<>();
        sets.add(plugin.rewards().inviter());
        sets.add(plugin.rewards().invited());
        sets.addAll(plugin.rewards().milestoneList());
        for (RewardSet set : sets) {
            if (set.allServers()) continue;
            boolean other = false;
            for (String sv : set.servers()) if (!sv.equals(me)) other = true;
            if (other) continue;
            String name = set instanceof Milestone ? "Milestone " + ((Milestone) set).amount()
                    : set == plugin.rewards().inviter() ? "The inviter reward" : "The welcome reward";
            plugin.getLogger().warning(name + " can be collected only on \"" + me + "\", but this server has"
                    + " network.rewards-here: false, so nobody can collect it. Add another server to the reward"
                    + " or set network.rewards-here: true.");
        }
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

        boolean drop = cfg().getBoolean("rewards.drop-if-full", true);
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
        String what = set instanceof Milestone ? "milestone " + milestone : parts[0].startsWith("referral:inviter") ? "inviter reward"
                : parts[0].startsWith("referral:invited") ? "welcome reward" : parts[0];
        dev.tmdevelopment.flarereferrals.util.ReferralLog.log("REWARD", p.getName() + " got the " + what + (invited.isEmpty() || set instanceof Milestone ? "" : " (" + inviter + " <- " + invited + ")"));
    }

    // ------------------------------------------------------------------ network

    /** A message for a player: here when he is online here, otherwise to the server he is on. */
    private void notify(UUID target, String key, String... ph) {
        Player p = Bukkit.getPlayer(target);
        if (p != null) {
            lang().send(p, key, ph);
            sound(p, "success");
            return;
        }
        if (!plugin.data().network()) return;
        StringBuilder sb = new StringBuilder(key);
        for (String s : ph) sb.append(SEP).append(s);
        plugin.data().publish("msg", target, sb.toString());
    }

    /** Broadcast on this server and, on a network, on every other server. */
    private void broadcast(String line) {
        dev.tmdevelopment.flarereferrals.util.Platform.broadcast(Text.parse(line, null));
        plugin.data().publish("bc", null, line);
    }

    /** Another server changed this player. */
    public void remoteChange(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null) return;
        deliverPending(p, false);
        checkVerify(p);
        refreshMenu(uuid);
    }

    /**
     * The invites or the rewards of a player changed: the FlareReferrals menu he has open shows it at once
     * (progress, milestones, rewards to collect). Main thread only.
     */
    public void refreshMenu(UUID uuid) {
        Player p = uuid == null ? null : Bukkit.getPlayer(uuid);
        if (p == null) return;
        Object holder = dev.tmdevelopment.flarereferrals.gui.Menu.openFor(p);
        if (holder instanceof dev.tmdevelopment.flarereferrals.layout.LayoutMenu) {
            dev.tmdevelopment.flarereferrals.layout.LayoutMenu m = (dev.tmdevelopment.flarereferrals.layout.LayoutMenu) holder;
            if (!m.editing()) m.refresh();
        }
    }

    /** Message from another server. */
    public void remoteEvent(String type, UUID target, String data) {
        if (data == null) return;
        if ("msg".equals(type) && target != null) {
            Player p = Bukkit.getPlayer(target);
            if (p == null) return;
            String[] parts = data.split(String.valueOf(SEP), -1);
            String[] ph = new String[parts.length - 1];
            System.arraycopy(parts, 1, ph, 0, ph.length);
            lang().send(p, parts[0], ph);
            sound(p, "success");
        } else if ("collect".equals(type) && target != null) {
            Player p = Bukkit.getPlayer(target);
            int i = data.indexOf(SEP);
            if (p != null && i > 0) collectNotice(p, data.substring(0, i), data.substring(i + 1), true);
        } else if ("bc".equals(type)) {
            dev.tmdevelopment.flarereferrals.util.Platform.broadcast(Text.parse(data, null));
        }
    }

    // ------------------------------------------------------------------ join / playtime

    public void join(Player p) {
        UUID uuid = p.getUniqueId();
        String name = p.getName();
        String ip = ipHash(p); // only a short hash, never the address (used by referral.block-same-ip)
        long first = firstSeen(p, plugin.data().get(uuid));
        if (first <= 0) first = System.currentTimeMillis();
        final long fs = first;
        plugin.data().run(ops -> ops.touch(uuid, name, ip, fs), d -> {
            Player now = Bukkit.getPlayer(uuid);
            if (now == null) return;
            PlayerData data = plugin.data().get(uuid);
            if (data == null) return;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (now.isOnline()) remindClaims(now);
            }, 60L);
            lastReminder.put(uuid, System.currentTimeMillis());
            // a bit later so the player is fully loaded
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!now.isOnline()) return;
                deliverPending(now, true);
                checkVerify(now);
            }, 40L);
        });
    }

    /** Every minute: network playtime + pending invites that can count now. */
    public void minute() {
        List<UUID> online = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) online.add(p.getUniqueId());
        if (online.isEmpty()) return;
        if (plugin.data().network()) {
            for (UUID u : online) {
                PlayerData d = plugin.data().get(u);
                if (d != null) d.playtime(d.playtime() + 60);
            }
            plugin.data().run(ops -> {
                for (UUID u : online) ops.addPlaytime(u, 60);
                return Boolean.TRUE;
            }, null);
        }
        if (cfg().getInt("referral.verify-after-minutes", 0) > 0) {
            for (Player p : Bukkit.getOnlinePlayers()) checkVerify(p);
        }
        // reminder while something waits in the collect menu
        int every = cfg().getInt("rewards.remind-minutes", 10);
        if (every > 0) {
            long now = System.currentTimeMillis();
            for (Player p : Bukkit.getOnlinePlayers()) {
                Long last = lastReminder.get(p.getUniqueId());
                if (last != null && now - last < every * 60_000L - 5_000L) continue;
                lastReminder.put(p.getUniqueId(), now);
                remindClaims(p);
            }
        }
        lastReminder.keySet().removeIf(u -> Bukkit.getPlayer(u) == null);
        lastNotice.keySet().removeIf(u -> Bukkit.getPlayer(u) == null);
    }

    private final Map<UUID, Long> lastReminder = new java.util.HashMap<>();
    private final Map<UUID, Long> lastNotice = new java.util.HashMap<>();

    /** Minutes played: on a network the highest between this server and the whole network. */
    public int playtimeMinutes(Player p, PlayerData d) {
        int local = 0;
        try {
            local = p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20 / 60;
        } catch (Throwable ignored) {
        }
        if (!plugin.data().network() || d == null) return local;
        return (int) Math.max(local, d.playtime() / 60);
    }

    /** First time seen: on a network the oldest between this server and the whole network. */
    public long firstSeen(Player p, PlayerData d) {
        long local = 0;
        try {
            local = p.getFirstPlayed();
        } catch (Throwable ignored) {
        }
        long net = d == null ? 0 : d.firstSeen();
        if (local <= 0) return net;
        if (net <= 0) return local;
        return Math.min(local, net);
    }

    // ------------------------------------------------------------------ utils

    public void sound(Player p, String type) {
        FileConfiguration cfg = cfg();
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
