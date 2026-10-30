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
            "top", "milestone", "milestones", "claim", "settings", "reload", "admin", "help", "cancel", "layout", "layouts", "migrate", "collect", "version", "ver", "about",
            "by", "invited", "event", "events"));

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
        // a player called like a command he can't use (Admin, Reload...): he is the one who invited
        if (SUBS.contains(sub) && args.length == 1 && sender instanceof Player && !canUse(sender, sub)
                && dev.tmdevelopment.flarereferrals.util.Perms.has(sender, "use") && namedPlayer(args[0])) {
            plugin.referrals().refer((Player) sender, args[0]);
            return true;
        }
        if ("by".equals(sub)) {
            // /referrals by <name>: always works, also for players called like a command (Top, Menu, Help...)
            if (!(sender instanceof Player)) { lang().send(sender, "general.players-only"); return true; }
            if (!dev.tmdevelopment.flarereferrals.util.Perms.has(sender, "use")) return noPerm(sender);
            if (args.length < 2) { lang().send(sender, "usage.by", "%label%", label); return true; }
            plugin.referrals().refer((Player) sender, args[1]);
            return true;
        }
        if ("invited".equals(sub)) { invited(sender, label, args); return true; }
        if ("event".equals(sub) || "events".equals(sub)) { plugin.events().command(sender, label, args); return true; }
        if ("help".equals(sub)) { help(sender, label); }
        else if ("cancel".equals(sub)) {
                if (sender instanceof Player && !plugin.chatInput().cancel((Player) sender)) lang().send(sender, "input.nothing-to-cancel");
            }
        else if ("info".equals(sub)) {
                if (args.length >= 2) {
                    if (!dev.tmdevelopment.flarereferrals.util.Perms.has(sender, "info.others")) return noPerm(sender);
                    PlayerData d = findData(args[1]);
                    if (d == null) {
                        lang().send(sender, "admin.player-not-found", "%player%", Text.safe(args[1]));
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
                    if (!plugin.referrals().claim(p, amount, null)) lang().send(p, "milestone.nothing");
                } else {
                    int n = plugin.referrals().claimAll(p);
                    if (n == 0) lang().send(p, "milestone.nothing");
                }
            }
        else if ("collect".equals(sub)) {
                if (!(sender instanceof Player)) { lang().send(sender, "general.players-only"); return true; }
                Player p = (Player) sender;
                if (!plugin.canOpen(p, "collect")) return unknown(sender, label);
                new dev.tmdevelopment.flarereferrals.gui.CollectMenu(plugin, p, plugin.canOpen(p, "menu") ? new PlayerMenu(plugin, p) : null).open();
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
                    lang().send(sender, "input.not-a-number", "%input%", Text.safe(args[1]));
                    lang().send(sender, "usage.milestone", "%label%", label);
                    return true;
                }
                Milestone m = plugin.rewards().milestone(amount);
                if (m == null) {
                    lang().send(sender, "admin.milestone-unknown", "%amount%", Text.safe(args[1]));
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
                boolean restart = plugin.reloadAll();
                lang().send(sender, "admin.reloaded");
                // files with a mistake: never overwritten, the owner is told where the mistake is
                for (String[] pr : dev.tmdevelopment.flarereferrals.util.SafeYaml.problems()) {
                    lang().send(sender, "admin.file-error", "%file%", pr[0], "%line%", pr[1], "%column%", pr[2], "%problem%", pr[3]);
                }
                if (restart) lang().send(sender, "admin.restart-needed");
            }
        else if ("admin".equals(sub)) { admin(sender, label, args); }
        else if ("about".equals(sub) || "version".equals(sub) || "ver".equals(sub)) { about(sender, label, args); }
        else if ("migrate".equals(sub)) {
                if (!dev.tmdevelopment.flarereferrals.util.Perms.has(sender, "admin")) return noPerm(sender);
                new MigrateCommand(plugin).run(sender, label, args);
            }
        else if ("layout".equals(sub) || "layouts".equals(sub)) {
                if (!dev.tmdevelopment.flarereferrals.util.Perms.has(sender, "admin")) return noPerm(sender);
                if (!(sender instanceof Player)) { lang().send(sender, "general.players-only"); return true; }
                Player p = (Player) sender;
                if (args.length >= 2 && dev.tmdevelopment.flarereferrals.layout.ChestBuilder.WORDS.contains(args[1].toLowerCase(Locale.ROOT))) {
                    // chest builder: add / import / chest / remove / list
                    new dev.tmdevelopment.flarereferrals.layout.ChestBuilder(plugin, label).run(p, args);
                } else if (args.length >= 2) {
                    dev.tmdevelopment.flarereferrals.layout.LayoutMenu m = dev.tmdevelopment.flarereferrals.layout.LayoutMenus.create(plugin, args[1].toLowerCase(Locale.ROOT), p, null);
                    if (m == null) lang().send(sender, "admin.layout-unknown", "%menu%", Text.safe(args[1]));
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

    /** Can this sender use the subcommand? (false = for him it doesn't exist or he has no permission) */
    private boolean canUse(CommandSender s, String sub) {
        boolean admin = dev.tmdevelopment.flarereferrals.util.Perms.has(s, "admin");
        switch (sub) {
            case "help": case "cancel": case "claim": case "by": return true;
            case "info": return plugin.canOpen(s, "info");
            case "menu": return plugin.canOpen(s, "menu");
            case "rewards": return plugin.canOpen(s, "rewards");
            case "top": return plugin.canOpen(s, "top");
            case "collect": return plugin.canOpen(s, "collect");
            case "invited": return plugin.canOpen(s, "invited");
            case "milestone": case "milestones": return admin || plugin.canOpen(s, "preview") || plugin.canOpen(s, "menu");
            case "about": case "version": case "ver": return canVersion(s);
            case "event": case "events": return plugin.events().visibleTo(s);
            default: return admin;
        }
    }

    /** A player with this name exists (data of the plugin or players the server knows). */
    private boolean namedPlayer(String name) {
        if (plugin.data().byName(name) != null) return true;
        OfflinePlayer op = Bukkit.getPlayerExact(name);
        if (op == null) op = dev.tmdevelopment.flarereferrals.util.Compat.knownPlayer(name);
        return op != null && (op.isOnline() || op.hasPlayedBefore());
    }

    /** /referrals invited [player]: the players he invited, in a menu (or in chat with "chat" / from the console). */
    private void invited(CommandSender sender, String label, String[] args) {
        boolean others = args.length >= 2 && !args[1].equalsIgnoreCase("chat");
        boolean chat = args.length >= 2 && args[args.length - 1].equalsIgnoreCase("chat");
        if (others) {
            if (!dev.tmdevelopment.flarereferrals.util.Perms.has(sender, "info.others")) { noPerm(sender); return; }
            PlayerData d = findData(args[1]);
            if (d == null) {
                lang().send(sender, "admin.player-not-found", "%player%", Text.safe(args[1]));
                return;
            }
            if (sender instanceof Player && !chat && plugin.layouts().get("invited").enabled()) {
                new dev.tmdevelopment.flarereferrals.gui.InvitedMenu(plugin, (Player) sender, d.uuid(), null).open();
            } else {
                sendInvited(sender, d.uuid(), d.name());
            }
            return;
        }
        if (!(sender instanceof Player)) { lang().send(sender, "general.players-only"); return; }
        Player p = (Player) sender;
        if (!plugin.canOpen(p, "invited")) { unknown(sender, label); return; }
        if (chat || !plugin.layouts().get("invited").enabled()) sendInvited(p, p.getUniqueId(), p.getName());
        else new dev.tmdevelopment.flarereferrals.gui.InvitedMenu(plugin, p, p.getUniqueId(), plugin.canOpen(p, "menu") ? new PlayerMenu(plugin, p) : null).open();
    }

    /** The invited players in chat. */
    private void sendInvited(CommandSender to, java.util.UUID owner, String name) {
        List<PlayerData> list = plugin.data().invitedBy(owner);
        java.text.SimpleDateFormat df = dev.tmdevelopment.flarereferrals.gui.InvitedMenu.dateFormat(lang().raw("invited.date-format"));
        int verified = 0;
        for (PlayerData d : list) if (!d.pendingInvite()) verified++;
        lang().send(to, "invited.header", "%player%", name, "%amount%", String.valueOf(list.size()),
                "%verified%", String.valueOf(verified), "%pending%", String.valueOf(list.size() - verified));
        if (list.isEmpty()) lang().send(to, "invited.empty", "%player%", name);
        int max = Math.max(1, plugin.getConfig().getInt("invited.chat-lines", 20));
        for (int i = 0; i < list.size() && i < max; i++) {
            PlayerData d = list.get(i);
            lang().send(to, "invited.line", "%number%", String.valueOf(i + 1), "%player%", d.name(),
                    "%date%", d.referredAt() > 0 ? df.format(new java.util.Date(d.referredAt())) : "-",
                    "%status%", lang().raw(d.pendingInvite() ? "invited.status.pending" : "invited.status.verified"));
        }
        if (list.size() > max) lang().send(to, "invited.more", "%amount%", String.valueOf(list.size() - max));
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
        lang().send(s, "help.header", "%label%", label, "%version%", plugin.getDescription().getVersion());
        if (s instanceof Player && dev.tmdevelopment.flarereferrals.util.Perms.has(s, "use")) lang().send(s, "help.lines.refer", "%label%", label);
        if (plugin.canOpen(s, "info")) lang().send(s, "help.lines.info", "%label%", label);
        if (plugin.canOpen(s, "menu")) lang().send(s, "help.lines.menu", "%label%", label);
        if (plugin.canOpen(s, "collect")) lang().send(s, "help.lines.collect", "%label%", label);
        if (plugin.canOpen(s, "rewards")) lang().send(s, "help.lines.rewards", "%label%", label);
        if (plugin.canOpen(s, "top")) lang().send(s, "help.lines.top", "%label%", label);
        if (plugin.canOpen(s, "invited")) lang().send(s, "help.lines.invited", "%label%", label);
        if (plugin.events().visibleTo(s) && plugin.events().active()) lang().send(s, "help.lines.event", "%label%", label);
        if (plugin.canOpen(s, "preview")) lang().send(s, "help.lines.milestone", "%label%", label);
        // /referrals claim is not listed: the [Collect] buttons in chat use it, /referrals collect is the one to type
        if (dev.tmdevelopment.flarereferrals.util.Perms.has(s, "admin")) lang().send(s, "help.admin", "%label%", label);
        if (canVersion(s)) lang().send(s, "help.lines.version", "%label%", label);
        lang().send(s, "help.footer", "%label%", label);
    }

    /** flarereferrals.command.version, also for admins that still have the old flaresrewards.admin. */
    private static boolean canVersion(CommandSender s) {
        return dev.tmdevelopment.flarereferrals.util.Perms.has(s, "command.version")
                || dev.tmdevelopment.flarereferrals.util.Perms.has(s, "admin");
    }

    /**
     * /referrals about (also version / ver, also from the console): plugin version (green = latest,
     * red = outdated), server, Java, storage, update notifier, plugin hooks and the permission list.
     */
    private void about(CommandSender s, String label, String[] args) {
        if (!canVersion(s)) { noPerm(s); return; }
        if (args.length > 1 && args[1].toLowerCase(Locale.ROOT).startsWith("perm")) {
            permissions(s);
            return;
        }
        dev.tmdevelopment.flarereferrals.update.UpdateChecker u = plugin.updates();
        if (u == null || !u.enabled()) {
            aboutLines(s, label, "about.check-off");
            return;
        }
        boolean[] answered = {false};
        u.check(() -> {
            answered[0] = true;
            if (s instanceof Player && !((Player) s).isOnline()) return;
            dev.tmdevelopment.flarereferrals.update.UpdateChecker.Result r = u.result();
            aboutLines(s, label, r == dev.tmdevelopment.flarereferrals.update.UpdateChecker.Result.UPDATE ? "about.outdated"
                    : r == dev.tmdevelopment.flarereferrals.update.UpdateChecker.Result.LATEST ? "about.latest" : "about.failed");
        });
        if (!answered[0]) lang().send(s, "about.checking");
    }

    private void aboutLines(CommandSender s, String label, String versionLine) {
        dev.tmdevelopment.flarereferrals.update.UpdateChecker u = plugin.updates();
        String current = u == null ? plugin.getDescription().getVersion() : u.current();
        String latest = u == null || u.latest() == null ? "?" : u.latest();
        String url = u == null ? "https://github.com/" + dev.tmdevelopment.flarereferrals.update.UpdateChecker.REPO + "/releases" : u.downloadUrl();
        String[] base = {"%label%", label, "%version%", current, "%latest%", latest, "%url%", url};
        lang().send(s, "about.header", base);
        lang().send(s, versionLine, base);
        // 1.21.1-R0.1-SNAPSHOT -> 1.21.1, 26.3.build.223 -> 26.3
        String mc = Bukkit.getBukkitVersion();
        java.util.regex.Matcher vm = java.util.regex.Pattern.compile("^(\\d+\\.\\d+(?:\\.\\d+)?)").matcher(mc);
        if (vm.find()) mc = vm.group(1);
        String network = plugin.data().network()
                ? Text.replace(lang().raw("about.network"), "%server-name%", plugin.data().server()) : "";
        String sep = lang().raw("about.separator");
        StringBuilder hooks = new StringBuilder();
        String[] names = {"PlaceholderAPI", "Vault", "LuckPerms"};
        for (String h : names) {
            if (hooks.length() > 0) hooks.append(sep);
            hooks.append(Text.replace(lang().raw(enabled(h) ? "about.hooked" : "about.missing"), "%hook%", h));
        }
        boolean notifier = u != null && u.enabled();
        lang().send(s, "about.info", "%label%", label, "%version%", current,
                "%minecraft%", mc, "%server%", Bukkit.getName(), "%java%", System.getProperty("java.version", "?"),
                "%storage%", plugin.data().type(), "%network%", network,
                "%notifier%", lang().raw(notifier ? "about.notifier-on" : "about.notifier-off"), "%hooks%", hooks.toString());
    }

    private static boolean enabled(String name) {
        org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin(name);
        return p != null && p.isEnabled();
    }

    /** Every permission of plugin.yml with what it does and who has it without a permissions plugin. */
    private void permissions(CommandSender s) {
        lang().send(s, "about.permissions.header");
        for (org.bukkit.permissions.Permission perm : plugin.getDescription().getPermissions()) {
            String def;
            switch (perm.getDefault()) {
                case TRUE: def = lang().raw("about.permissions.everyone"); break;
                case OP: def = lang().raw("about.permissions.op"); break;
                case NOT_OP: def = lang().raw("about.permissions.not-op"); break;
                default: def = lang().raw("about.permissions.nobody");
            }
            String desc = perm.getDescription() == null || perm.getDescription().isEmpty() ? "-" : perm.getDescription();
            lang().send(s, "about.permissions.line", "%permission%", perm.getName(), "%description%", desc, "%default%", def);
        }
    }

    private PlayerData findData(String name) {
        if (!Text.validName(name)) return null;
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
        List<java.util.UUID> offline = new ArrayList<>();
        for (PlayerData d : top) if (Bukkit.getPlayer(d.uuid()) == null) offline.add(d.uuid());
        Runnable send = () -> {
            if (to instanceof Player && !((Player) to).isOnline()) return;
            lang().send(to, "top.header");
            if (top.isEmpty()) {
                lang().send(to, "top.empty");
            }
            List<String> lines = lang().lines("top.line");
            for (int i = 0; i < top.size(); i++) {
                PlayerData d = top.get(i);
                org.bukkit.OfflinePlayer who = Bukkit.getOfflinePlayer(d.uuid());
                for (String line : lines) {
                    String text = Text.replace(line, "%position%", String.valueOf(i + 1),
                            "%player%", d.name(), "%invites%", String.valueOf(d.invites()));
                    // PlaceholderAPI placeholders (%luckperms_prefix%...) are of the player of this line
                    dev.tmdevelopment.flarereferrals.util.Platform.send(to, Text.parseAbout(text, who));
                }
            }
            lang().send(to, "top.footer");
        };
        // offline players: LuckPerms loads their rank first
        if (!String.join("", lang().lines("top.line")).contains("%")
                || !dev.tmdevelopment.flarereferrals.hook.LuckPermsHook.load(plugin, offline, send)) send.run();
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
            lang().send(sender, "usage.admin.unknown", "%action%", Text.safe(args[1]));
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
            lang().send(sender, "admin.player-not-found", "%player%", Text.safe(args[2]));
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
                    lang().send(sender, "input.not-a-number", "%input%", Text.safe(args[3]));
                    adminUsage(sender, label, action);
                    return;
                }
                int before = d.invites();
                java.util.UUID id = d.uuid();
                String name = d.name();
                java.util.function.IntConsumer done = now -> {
                    plugin.referrals().refreshMenu(id);
                    if (now >= 0) dev.tmdevelopment.flarereferrals.util.ReferralLog.log("ADMIN", sender.getName() + ": " + action + " " + name + " " + args[3] + " (invites " + before + " -> " + now + ")");
                    if (now < 0) lang().send(sender, "general.database-error");
                    else lang().send(sender, "admin.invites-updated", "%player%", name,
                            "%invites%", String.valueOf(now), "%before%", String.valueOf(before));
                };
                final int amount = n;
                if (action.equals("add")) {
                    plugin.referrals().addInvites(d, amount, done);
                } else if (action.equals("remove")) {
                    plugin.data().run(ops -> ops.addInvites(id, -amount), v -> done.accept(v == null ? -1 : v));
                } else {
                    plugin.data().run(ops -> {
                        ops.setInvites(id, amount);
                        return amount;
                    }, v -> done.accept(v == null ? -1 : v));
                }
            }
        else if ("reset".equals(action)) {
                java.util.UUID id = d.uuid();
                String name = d.name();
                plugin.data().run(ops -> {
                    ops.setInvites(id, 0);
                    ops.clearReferrer(id);
                    ops.deleteRewards(id, null);
                    return Boolean.TRUE;
                }, v -> {
                    if (v != null) dev.tmdevelopment.flarereferrals.util.ReferralLog.log("ADMIN", sender.getName() + ": reset " + name);
                    plugin.referrals().refreshMenu(id);
                    lang().send(sender, v == null ? "general.database-error" : "admin.reset", "%player%", name);
                });
            }
        else if ("resetclaimed".equals(action)) {
                java.util.UUID id = d.uuid();
                String name = d.name();
                plugin.data().run(ops -> {
                    // received milestones are forgotten (given again at the next invite); rewards still waiting
                    // (in the collect menu or in the queue) stay, so nothing the player earned is lost
                    dev.tmdevelopment.flarereferrals.data.PlayerData cur = ops.player(id);
                    if (cur != null) {
                        // ids first: with data.yml the list is the live one and deleting while looping would break it
                        List<Long> ids = new ArrayList<>();
                        for (dev.tmdevelopment.flarereferrals.data.RewardEntry e : cur.rewards()) {
                            if (e.milestone() > 0 && e.state() == dev.tmdevelopment.flarereferrals.data.RewardEntry.State.DELIVERED) {
                                ids.add(e.id());
                            }
                        }
                        for (Long rid : ids) ops.deleteReward(id, rid);
                    }
                    return Boolean.TRUE;
                }, v -> {
                    if (v != null) dev.tmdevelopment.flarereferrals.util.ReferralLog.log("ADMIN", sender.getName() + ": resetclaimed " + name);
                    plugin.referrals().refreshMenu(id);
                    lang().send(sender, v == null ? "general.database-error" : "admin.reset-claimed", "%player%", name);
                });
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
            for (String k : dev.tmdevelopment.flarereferrals.util.J8.list("info", "menu", "rewards", "top", "collect", "invited")) if (plugin.canOpen(sender, k)) opts.add(k);
            if (plugin.events().canManage(sender) || (plugin.events().visibleTo(sender) && plugin.events().active())) opts.add("event");
            if (plugin.canOpen(sender, "preview")) opts.add("milestone");
            if (admin) opts.addAll(dev.tmdevelopment.flarereferrals.util.J8.list("settings", "layout", "reload", "admin", "migrate"));
            if (canVersion(sender)) opts.add("about");
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
            } else if ((sub.equals("layout") || sub.equals("layouts")) && admin) {
                List<String> opts = new ArrayList<>(dev.tmdevelopment.flarereferrals.layout.ChestBuilder.WORDS);
                opts.addAll(plugin.layouts().allIds());
                filter(opts, args[1], out);
            } else if ((sub.equals("about") || sub.equals("version") || sub.equals("ver")) && canVersion(sender)) {
                filter(dev.tmdevelopment.flarereferrals.util.J8.list("permissions"), args[1], out);
            } else if ((sub.equals("event") || sub.equals("events")) && plugin.events().canManage(sender)) {
                filter(dev.tmdevelopment.flarereferrals.util.J8.list("start", "stop"), args[1], out);
            } else if (sub.equals("invited")) {
                List<String> opts = new ArrayList<>();
                if (plugin.canOpen(sender, "invited")) opts.add("chat");
                if (dev.tmdevelopment.flarereferrals.util.Perms.has(sender, "info.others")) opts.addAll(onlineNames());
                filter(opts, args[1], out);
            } else if (sub.equals("migrate") && admin) {
                filter(dev.tmdevelopment.flarereferrals.util.J8.list("yaml", "sqlite", "mysql"), args[1], out);
            } else if (sub.equals("admin") && admin) {
                filter(dev.tmdevelopment.flarereferrals.util.J8.list("set", "add", "remove", "reset", "resetclaimed"), args[1], out);
            }
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("layout") || args[0].equalsIgnoreCase("layouts")) && admin) {
            String w = args[1].toLowerCase(Locale.ROOT);
            if (w.equals("import") || w.equals("chest")) filter(plugin.layouts().allIds(), args[2], out);
            else if (w.equals("remove")) filter(plugin.layouts().customIds(), args[2], out);
        } else if (args.length == 4 && args[0].equalsIgnoreCase("layout") && args[1].equalsIgnoreCase("remove") && admin) {
            filter(dev.tmdevelopment.flarereferrals.util.J8.list("confirm"), args[3], out);
        } else if (args.length >= 3 && args[0].equalsIgnoreCase("migrate") && admin) {
            filter(dev.tmdevelopment.flarereferrals.util.J8.list("merge", "confirm"), args[args.length - 1], out);
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("event") || args[0].equalsIgnoreCase("events"))
                && args[1].equalsIgnoreCase("start") && plugin.events().canManage(sender)) {
            filter(dev.tmdevelopment.flarereferrals.util.J8.list("1.5", "2", "3"), args[2], out);
        } else if (args.length == 4 && (args[0].equalsIgnoreCase("event") || args[0].equalsIgnoreCase("events"))
                && args[1].equalsIgnoreCase("start") && plugin.events().canManage(sender)) {
            filter(dev.tmdevelopment.flarereferrals.util.J8.list("30m", "1h", "2h", "1d"), args[3], out);
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
