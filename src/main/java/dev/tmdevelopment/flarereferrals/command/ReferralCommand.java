package dev.tmdevelopment.flarereferrals.command;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.config.Lang;
import dev.tmdevelopment.flarereferrals.data.PlayerData;
import dev.tmdevelopment.flarereferrals.gui.MilestoneListMenu;
import dev.tmdevelopment.flarereferrals.gui.PlayerMenu;
import dev.tmdevelopment.flarereferrals.gui.PreviewMenu;
import dev.tmdevelopment.flarereferrals.gui.ReferralRewardsMenu;
import dev.tmdevelopment.flarereferrals.gui.RewardSetMenu;
import dev.tmdevelopment.flarereferrals.gui.SettingsMenu;
import dev.tmdevelopment.flarereferrals.reward.Milestone;
import dev.tmdevelopment.flarereferrals.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ReferralCommand implements TabExecutor {

    private static final Set<String> SUBS = new java.util.HashSet<>(java.util.Arrays.asList("info", "menu", "rewards",
            "top", "milestone", "milestones", "claim", "settings", "reload", "admin", "help", "cancel", "layout", "layouts"));

    private final FlareReferrals plugin;

    public ReferralCommand(FlareReferrals plugin) {
        this.plugin = plugin;
    }

    private Lang lang() { return plugin.lang(); }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                if (!plugin.canOpen(p, "info")) return unknown(sender, label);
                sendInfo(p, p.getUniqueId(), p.getName());
            } else {
                help(sender, label);
            }
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("help".equals(sub)) { help(sender, label); }
        else if ("cancel".equals(sub)) {
                if (sender instanceof Player && !plugin.chatInput().cancel((Player) sender)) lang().send(sender, "input.nothing-to-cancel");
            }
        else if ("info".equals(sub)) {
                if (args.length >= 2) {
                    if (!dev.tmdevelopment.flarereferrals.util.Perms.has(sender, "info.others")) return noPerm(sender);
                    PlayerData d = findData(args[1]);
                    if (d == null) {
                        lang().send(sender, "referral.not-found", "%player%", args[1]);
                        return true;
                    }
                    sendInfo(sender, d.uuid(), d.name());
                } else if (sender instanceof Player) {
                    Player p = (Player) sender;
                    if (!plugin.canOpen(p, "info")) return unknown(sender, label);
                    sendInfo(p, p.getUniqueId(), p.getName());
                } else {
                    lang().send(sender, "general.players-only");
                }
            }
        else if ("claim".equals(sub)) {
                if (!(sender instanceof Player)) { lang().send(sender, "general.players-only"); return true; }
                Player p = (Player) sender;
                if (args.length >= 2) {
                    int amount;
                    try { amount = Integer.parseInt(args[1]); } catch (NumberFormatException e) { amount = -1; }
                    if (!plugin.referrals().claim(p, amount)) lang().send(p, "milestone.nothing");
                } else {
                    int n = plugin.referrals().claimAll(p);
                    if (n == 0) lang().send(p, "milestone.nothing");
                }
            }
        else if ("rewards".equals(sub)) {
                if (!(sender instanceof Player)) { lang().send(sender, "general.players-only"); return true; }
                Player p = (Player) sender;
                if (!plugin.canOpen(p, "rewards")) return unknown(sender, label);
                new ReferralRewardsMenu(plugin, p, null).open();
            }
        else if ("menu".equals(sub)) {
                if (!(sender instanceof Player)) { lang().send(sender, "general.players-only"); return true; }
                Player p = (Player) sender;
                if (!plugin.canOpen(p, "menu")) return unknown(sender, label);
                new PlayerMenu(plugin, p).open();
            }
        else if ("top".equals(sub)) {
                if (!plugin.canOpen(sender, "top")) return unknown(sender, label);
                sendTop(sender);
            }
        else if ("milestone".equals(sub) || "milestones".equals(sub)) {
                if (!(sender instanceof Player)) { lang().send(sender, "general.players-only"); return true; }
                Player p = (Player) sender;
                if (!dev.tmdevelopment.flarereferrals.util.Perms.has(p, "admin") && !plugin.canOpen(p, "preview") && !plugin.canOpen(p, "menu")) {
                    return unknown(sender, label);
                }
                if (args.length < 2) {
                    if (dev.tmdevelopment.flarereferrals.util.Perms.has(p, "admin")) new MilestoneListMenu(plugin, p, new SettingsMenu(plugin, p)).open();
                    else if (plugin.canOpen(p, "menu")) new PlayerMenu(plugin, p).open();
                    else return unknown(sender, label);
                    return true;
                }
                int amount;
                try { amount = Integer.parseInt(args[1]); } catch (NumberFormatException e) {
                    lang().send(sender, "input.not-a-number", "%input%", args[1]);
                    lang().send(sender, "usage.milestone", "%label%", label);
                    return true;
                }
                Milestone m = plugin.rewards().milestone(amount);
                if (m == null) {
                    lang().send(sender, "admin.milestone-unknown", "%amount%", args[1]);
                    return true;
                }
                if (dev.tmdevelopment.flarereferrals.util.Perms.has(p, "admin")) {
                    new RewardSetMenu(plugin, p, m, new MilestoneListMenu(plugin, p, new SettingsMenu(plugin, p))).open();
                } else if (plugin.canOpen(p, "preview")) {
                    new PreviewMenu(plugin, p, m, plugin.canOpen(p, "menu") ? new PlayerMenu(plugin, p) : null).open();
                } else {
                    return unknown(sender, label);
                }
            }
        else if ("settings".equals(sub)) {
                if (!dev.tmdevelopment.flarereferrals.util.Perms.has(sender, "admin")) return noPerm(sender);
                if (!(sender instanceof Player)) { lang().send(sender, "general.players-only"); return true; }
                Player p = (Player) sender;
                new SettingsMenu(plugin, p).open();
            }
        else if ("reload".equals(sub)) {
                if (!dev.tmdevelopment.flarereferrals.util.Perms.has(sender, "admin")) return noPerm(sender);
                plugin.reloadAll();
                lang().send(sender, "admin.reloaded");
            }
        else if ("admin".equals(sub)) { admin(sender, label, args); }
        else if ("layout".equals(sub) || "layouts".equals(sub)) {
                if (!dev.tmdevelopment.flarereferrals.util.Perms.has(sender, "admin")) return noPerm(sender);
                if (!(sender instanceof Player)) { lang().send(sender, "general.players-only"); return true; }
                Player p = (Player) sender;
                if (args.length >= 2) {
                    dev.tmdevelopment.flarereferrals.layout.LayoutMenu m = dev.tmdevelopment.flarereferrals.layout.LayoutMenus.create(plugin, args[1].toLowerCase(Locale.ROOT), p, null);
                    if (m == null) lang().send(sender, "admin.layout-unknown", "%menu%", args[1]);
                    else m.editMode().open();
                } else {
                    new dev.tmdevelopment.flarereferrals.layout.LayoutListMenu(plugin, p, new SettingsMenu(plugin, p)).open();
                }
            }
        else {
                if (!(sender instanceof Player)) { lang().send(sender, "general.players-only"); return true; }
                Player p = (Player) sender;
                if (!dev.tmdevelopment.flarereferrals.util.Perms.has(p, "use")) return noPerm(sender);
                plugin.referrals().refer(p, args[0]);
            }
        return true;
    }

    /** Answer for things that are turned off: same as a command that doesn't exist. */
    private boolean unknown(CommandSender s, String label) {
        lang().send(s, "general.unknown-command", "%label%", label);
        return true;
    }

    private boolean noPerm(CommandSender s) {
        lang().send(s, "general.no-permission");
        return true;
    }

    /** Help with only the commands this player can really use. */
    private void help(CommandSender s, String label) {
        lang().send(s, "help.header", "%label%", label);
        lang().send(s, "help.lines.refer", "%label%", label);
        if (plugin.canOpen(s, "info")) lang().send(s, "help.lines.info", "%label%", label);
        if (plugin.canOpen(s, "menu")) lang().send(s, "help.lines.menu", "%label%", label);
        if (plugin.canOpen(s, "rewards")) lang().send(s, "help.lines.rewards", "%label%", label);
        if (plugin.canOpen(s, "top")) lang().send(s, "help.lines.top", "%label%", label);
        if (plugin.canOpen(s, "preview")) lang().send(s, "help.lines.milestone", "%label%", label);
        if (plugin.referrals().claimMode()) lang().send(s, "help.lines.claim", "%label%", label);
        if (dev.tmdevelopment.flarereferrals.util.Perms.has(s, "admin")) lang().send(s, "help.admin", "%label%", label);
        else lang().send(s, "help.footer", "%label%", label);
    }

    private PlayerData findData(String name) {
        PlayerData d = plugin.data().byName(name);
        if (d != null) return d;
        OfflinePlayer op = Bukkit.getPlayerExact(name);
        if (op == null) op = dev.tmdevelopment.flarereferrals.util.Compat.knownPlayer(name);
        if (op == null || (!op.isOnline() && !op.hasPlayedBefore())) return null;
        return plugin.data().getOrCreate(op.getUniqueId(), op.getName());
    }

    // ---------------------------------------------------------------- info

    private void sendInfo(CommandSender to, java.util.UUID uuid, String name) {
        PlayerData d = plugin.data().get(uuid);
        int invites = d == null ? 0 : d.invites();
        String referrer = lang().raw("info.nobody");
        if (d != null && d.referredBy() != null) {
            PlayerData r = plugin.data().get(d.referredBy());
            referrer = r == null ? "?" : r.name();
        }
        Milestone next = plugin.referrals().nextMilestone(invites);
        String nextStr = next == null ? lang().raw("info.no-next")
                : String.valueOf(plugin.referrals().nextTarget(next, invites));
        String remaining = next == null ? "0" : String.valueOf(plugin.referrals().nextTarget(next, invites) - invites);
        String[] ph = {"%player%", name, "%invites%", String.valueOf(invites), "%referrer%", referrer,
                "%next%", nextStr, "%remaining%", remaining};

        Player viewer = to instanceof Player ? (Player) to : null;
        for (String line : lang().lines("info.lines")) {
            if (line.trim().equals("%milestones%") || line.trim().equals("{milestones}")) {
                sendMilestoneLines(to, viewer, d, invites);
                continue;
            }
            dev.tmdevelopment.flarereferrals.util.Platform.send(to, Text.parse(Text.replace(line, ph), viewer));
        }
    }

    private void sendMilestoneLines(CommandSender to, Player viewer, PlayerData d, int invites) {
        if (!plugin.getConfig().getBoolean("milestones.enabled", true)) return;
        boolean admin = dev.tmdevelopment.flarereferrals.util.Perms.has(to, "admin");
        List<Milestone> list = new ArrayList<>();
        for (Milestone m : plugin.rewards().milestones()) if (m.enabled() || admin) list.add(m);
        // header: mentions clicking only when the player can open what the click leads to
        boolean preview = plugin.canOpen(to, "preview");
        boolean menu = plugin.canOpen(to, "menu");
        for (String h : lang().lines(preview ? "info.milestones-header" : "info.milestones-header-plain")) {
            net.kyori.adventure.text.Component c = Text.parse(h, viewer);
            if (menu) {
                c = c.clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/referrals menu"))
                        .hoverEvent(Text.parse(lang().raw("info.milestones-header-hover"), viewer));
            }
            dev.tmdevelopment.flarereferrals.util.Platform.send(to, c);
        }
        if (list.isEmpty()) {
            lang().send(to, "info.no-milestones");
            return;
        }
        for (Milestone m : list) {
            String status;
            if (!m.enabled()) status = lang().raw("info.status.disabled");
            else if (m.repeat() && d != null && d.claimable().contains(m.amount())) status = lang().raw("info.status.ready");
            else if (m.repeat()) status = lang().raw("info.status.repeat");
            else if (d != null && d.claimable().contains(m.amount())) status = lang().raw("info.status.ready");
            else if (invites >= m.amount()) status = lang().raw("info.status.claimed"); // reached = ✔, not reached = ✖
            else status = lang().raw("info.status.locked");
            int target = plugin.referrals().nextTarget(m, invites);
            String hover = lang().raw(admin ? "info.hover-admin" : "info.hover");
            String line = lang().raw("info.milestone-line");
            line = Text.replace(line, "%hover%", hover);
            line = Text.replace(line,
                    "%status%", status,
                    "%name%", m.name(),
                    "%amount%", String.valueOf(m.amount()),
                    "%target%", String.valueOf(target),
                    "%invites%", String.valueOf(Math.min(invites, target)),
                    "%remaining%", String.valueOf(Math.max(0, target - invites)));
            // the whole line is clickable, whatever is written in the lang file
            boolean ready = d != null && d.claimable().contains(m.amount()) && to instanceof Player
                    && ((Player) to).getUniqueId().equals(d.uuid());
            if (ready) {
                // reached and not collected: click = collect it
                dev.tmdevelopment.flarereferrals.util.Platform.send(to, Text.parse(line, viewer)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/referrals claim " + m.amount()))
                        .hoverEvent(Text.parse(lang().raw("info.hover-claim"), viewer)));
            } else if (plugin.canOpen(to, "preview")) {
                dev.tmdevelopment.flarereferrals.util.Platform.send(to, Text.parse(line, viewer)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/referrals milestone " + m.amount()))
                        .hoverEvent(Text.parse(hover, viewer)));
            } else {
                dev.tmdevelopment.flarereferrals.util.Platform.send(to, Text.parse(line, viewer));
            }
        }
    }

    private void sendTop(CommandSender to) {
        List<PlayerData> top = plugin.data().top(10);
        lang().send(to, "top.header");
        if (top.isEmpty()) {
            lang().send(to, "top.empty");
        }
        for (int i = 0; i < top.size(); i++) {
            PlayerData d = top.get(i);
            lang().send(to, "top.line", "%position%", String.valueOf(i + 1),
                    "%player%", d.name(), "%invites%", String.valueOf(d.invites()));
        }
        lang().send(to, "top.footer");
    }

    // ---------------------------------------------------------------- admin

    private static final List<String> ADMIN_ACTIONS = dev.tmdevelopment.flarereferrals.util.J8.list("set", "add", "remove", "reset", "resetclaimed");

    /** Explains one admin action (or all of them) instead of failing silently. */
    private void adminUsage(CommandSender sender, String label, String action) {
        lang().send(sender, "usage.admin." + action, "%label%", label);
    }

    private void admin(CommandSender sender, String label, String[] args) {
        if (!dev.tmdevelopment.flarereferrals.util.Perms.has(sender, "admin")) { noPerm(sender); return; }
        if (args.length < 2) {
            adminUsage(sender, label, "overview");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (!ADMIN_ACTIONS.contains(action)) {
            lang().send(sender, "usage.admin.unknown", "%action%", args[1]);
            adminUsage(sender, label, "overview");
            return;
        }
        if (args.length < 3) {
            lang().send(sender, "usage.admin.missing-player");
            adminUsage(sender, label, action);
            return;
        }
        PlayerData d = findData(args[2]);
        if (d == null) {
            lang().send(sender, "referral.not-found", "%player%", args[2]);
            return;
        }
        if ("set".equals(action) || "add".equals(action) || "remove".equals(action)) {
                if (args.length < 4) {
                    lang().send(sender, "usage.admin.missing-amount");
                    adminUsage(sender, label, action);
                    return;
                }
                int n;
                try { n = Integer.parseInt(args[3]); } catch (NumberFormatException e) { n = -1; }
                if (n < 0 || (n == 0 && !action.equals("set"))) {
                    lang().send(sender, "input.not-a-number", "%input%", args[3]);
                    adminUsage(sender, label, action);
                    return;
                }
                int before = d.invites();
                if (action.equals("add")) {
                    plugin.referrals().addInvites(d, n);
                } else if (action.equals("remove")) {
                    d.invites(d.invites() - n);
                    plugin.data().save();
                } else {
                    d.invites(n);
                    plugin.data().save();
                }
                lang().send(sender, "admin.invites-updated", "%player%", d.name(),
                        "%invites%", String.valueOf(d.invites()), "%before%", String.valueOf(before));
            }
        else if ("reset".equals(action)) {
                d.invites(0);
                d.claimed().clear();
                d.claimable().clear();
                d.referredBy(null);
                d.referredAt(0);
                d.pending().clear();
                plugin.data().save();
                lang().send(sender, "admin.reset", "%player%", d.name());
            }
        else if ("resetclaimed".equals(action)) {
                d.claimed().clear();
                d.claimable().clear();
                plugin.data().save();
                lang().send(sender, "admin.reset-claimed", "%player%", d.name());
            }
        else { adminUsage(sender, label, "overview"); }
    }

    // ---------------------------------------------------------------- tab

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = dev.tmdevelopment.flarereferrals.util.Perms.has(sender, "admin");
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(dev.tmdevelopment.flarereferrals.util.J8.list("help", "claim"));
            for (String k : dev.tmdevelopment.flarereferrals.util.J8.list("info", "menu", "rewards", "top")) if (plugin.canOpen(sender, k)) opts.add(k);
            if (plugin.canOpen(sender, "preview")) opts.add("milestone");
            if (admin) opts.addAll(dev.tmdevelopment.flarereferrals.util.J8.list("settings", "layout", "reload", "admin"));
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(sender)) opts.add(p.getName());
            }
            filter(opts, args[0], out);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("milestone") || sub.equals("milestones")) {
                List<String> opts = new ArrayList<>();
                for (Milestone m : plugin.rewards().milestones()) opts.add(String.valueOf(m.amount()));
                filter(opts, args[1], out);
            } else if (sub.equals("info") && dev.tmdevelopment.flarereferrals.util.Perms.has(sender, "info.others")) {
                filter(onlineNames(), args[1], out);
            } else if (sub.equals("layout") && admin) {
                filter(dev.tmdevelopment.flarereferrals.layout.LayoutManager.IDS, args[1], out);
            } else if (sub.equals("admin") && admin) {
                filter(dev.tmdevelopment.flarereferrals.util.J8.list("set", "add", "remove", "reset", "resetclaimed"), args[1], out);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && admin) {
            filter(onlineNames(), args[2], out);
        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin") && admin
                && dev.tmdevelopment.flarereferrals.util.J8.list("set", "add", "remove").contains(args[1].toLowerCase(Locale.ROOT))) {
            filter(dev.tmdevelopment.flarereferrals.util.J8.list("1", "3", "5", "10"), args[3], out);
        }
        return out;
    }

    private List<String> onlineNames() {
        List<String> l = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) l.add(p.getName());
        return l;
    }

    private void filter(List<String> opts, String prefix, List<String> out) {
        String pre = prefix.toLowerCase(Locale.ROOT);
        for (String o : opts) if (o.toLowerCase(Locale.ROOT).startsWith(pre)) out.add(o);
    }

    public static boolean isSub(String s) {
        return SUBS.contains(s.toLowerCase(Locale.ROOT));
    }
}
