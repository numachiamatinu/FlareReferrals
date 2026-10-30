package dev.tmdevelopment.flarereferrals;

import dev.tmdevelopment.flarereferrals.command.ReferralCommand;
import dev.tmdevelopment.flarereferrals.config.Lang;
import dev.tmdevelopment.flarereferrals.currency.CurrencyManager;
import dev.tmdevelopment.flarereferrals.data.DataManager;
import dev.tmdevelopment.flarereferrals.gui.MenuListener;
import dev.tmdevelopment.flarereferrals.hook.PapiExpansion;
import dev.tmdevelopment.flarereferrals.input.ChatInput;
import dev.tmdevelopment.flarereferrals.install.DependencyInstaller;
import dev.tmdevelopment.flarereferrals.listener.PlayerListener;
import dev.tmdevelopment.flarereferrals.reward.RewardManager;
import dev.tmdevelopment.flarereferrals.service.ReferralService;
import dev.tmdevelopment.flarereferrals.stats.TMDStats;
import dev.tmdevelopment.flarereferrals.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class FlareReferrals extends JavaPlugin {

    private Lang lang;
    private CurrencyManager currencies;
    private RewardManager rewards;
    private dev.tmdevelopment.flarereferrals.layout.LayoutManager layouts;
    private dev.tmdevelopment.flarereferrals.layout.MenuCommands menuCommands;
    private dev.tmdevelopment.flarereferrals.config.MenuTexts menuTexts;
    private DataManager data;
    private ReferralService referrals;
    private ChatInput chatInput;
    private dev.tmdevelopment.flarereferrals.service.ReferralEvents events;
    private dev.tmdevelopment.flarereferrals.service.InviteHint hints;
    private TMDStats stats;
    private dev.tmdevelopment.flarereferrals.update.UpdateChecker updates;

    /** Before the rename the plugin was called FlaresRewards: its folder is copied once, nothing is lost. */
    @Override
    public void onLoad() {
        java.io.File newDir = getDataFolder();
        java.io.File oldDir = new java.io.File(newDir.getParentFile(), "FlaresRewards");
        if (newDir.exists() || !oldDir.isDirectory()) return;
        try {
            java.nio.file.Path from = oldDir.toPath();
            java.nio.file.Path to = newDir.toPath();
            java.nio.file.Files.walk(from).forEach(src -> {
                try {
                    java.nio.file.Path dst = to.resolve(from.relativize(src).toString());
                    if (java.nio.file.Files.isDirectory(src)) java.nio.file.Files.createDirectories(dst);
                    else java.nio.file.Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
            getLogger().info("Copied your settings and data from plugins/FlaresRewards (old name) to plugins/FlareReferrals. "
                    + "You can delete the old folder and the old FlaresRewards jar.");
        } catch (Exception e) {
            getLogger().warning("Could not copy plugins/FlaresRewards to plugins/FlareReferrals: " + e.getMessage());
        }
    }

    private boolean unsupported;

    /** 1.16.5 or newer (older servers still load the plugin: api-version is not checked there). */
    private static boolean supportedServer() {
        String v = Bukkit.getBukkitVersion(); // 1.8.8-R0.1-SNAPSHOT, 1.21.4-R0.1-SNAPSHOT, 26.3.build.223-...
        String[] p = v.split("[^0-9]+");
        try {
            int major = Integer.parseInt(p[0]);
            if (major != 1) return true; // 26.x and later
            int minor = p.length > 1 ? Integer.parseInt(p[1]) : 0;
            int patch = p.length > 2 && v.startsWith("1." + minor + ".") ? Integer.parseInt(p[2]) : 0;
            return minor > 16 || (minor == 16 && patch >= 5);
        } catch (RuntimeException e) {
            return true;
        }
    }

    @Override
    public void onEnable() {
        if (!supportedServer()) {
            unsupported = true;
            String mc = Bukkit.getBukkitVersion();
            if (mc.indexOf('-') > 0) mc = mc.substring(0, mc.indexOf('-'));
            getLogger().severe("FlareReferrals needs Minecraft 1.16.5 or newer, this server is " + mc
                    + ": the plugin is turned off. (Players on older clients can join a 1.16.5+ server with ViaVersion.)");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("FlaresRewards") != null) {
            getLogger().severe("The old FlaresRewards jar is also installed: remove it from plugins/ and restart, "
                    + "FlareReferrals replaces it (same commands, data already copied).");
        }
        long start = System.currentTimeMillis();
        saveDefaultConfig();
        dev.tmdevelopment.flarereferrals.util.SafeYaml.clear();
        dev.tmdevelopment.flarereferrals.util.Headers.sync(this, new java.io.File(getDataFolder(), "config.yml"), "config.yml");
        if (!configReadable()) {
            // the storage / network settings are unknown: starting with the defaults could save data in the wrong place
            waitForFix("config.yml");
            return;
        }
        updateConfig();

        boolean papi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        Text.setPapi(papi);

        lang = new Lang(this);
        lang.load();
        currencies = new CurrencyManager(this);
        currencies.load();
        rewards = new RewardManager(this);
        rewards.load();
        if (dev.tmdevelopment.flarereferrals.util.SafeYaml.broken("rewards.yml")) {
            // without its rewards, invites would count and give nothing
            waitForFix("rewards.yml");
            return;
        }
        finishMigration();
        layouts = new dev.tmdevelopment.flarereferrals.layout.LayoutManager(this);
        layouts.load();
        menuTexts = new dev.tmdevelopment.flarereferrals.config.MenuTexts(this);
        menuTexts.load();
        menuCommands = new dev.tmdevelopment.flarereferrals.layout.MenuCommands(this);
        menuCommands.refresh();
        data = new DataManager(this);
        if (!data.start()) {
            data = null;
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        referrals = new ReferralService(this);
        referrals.checkServers();
        events = new dev.tmdevelopment.flarereferrals.service.ReferralEvents(this);
        events.load();
        hints = new dev.tmdevelopment.flarereferrals.service.InviteHint(this);
        hints.reload();
        dev.tmdevelopment.flarereferrals.util.ReferralLog.start(this, getConfig().getBoolean("logs.referrals", true),
                data.network() ? data.server() : "");
        chatInput = new ChatInput(this);

        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(chatInput, this);

        // /referrals, also /freferrals /fref /freferral /referral (plugin.yml aliases, useful if another plugin uses /referrals)
        PluginCommand cmd = getCommand("referrals");
        if (cmd != null) {
            ReferralCommand executor = new ReferralCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        if (papi) {
            PapiExpansion.registerAll(this);
        }

        // download + start missing dependencies by itself
        new DependencyInstaller(this).run(name -> {
            if (name.equalsIgnoreCase("PlaceholderAPI") && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                Text.setPapi(true);
                PapiExpansion.registerAll(this);
            }
        });

        // check currencies once every plugin is loaded
        Bukkit.getScheduler().runTaskLater(this, () -> currencies.checkAll(), 40L);

        // autosave every 5 minutes (data.yml; databases save at every change)
        Bukkit.getScheduler().runTaskTimer(this, () -> data.save(), 6000L, 6000L);
        // network playtime + invites waiting for referral.verify-after-minutes
        Bukkit.getScheduler().runTaskTimer(this, () -> referrals.minute(), 1200L, 1200L);
        for (Player p : Bukkit.getOnlinePlayers()) referrals.join(p); // /reload or plugin managers

        startMetrics();
        banner(papi, System.currentTimeMillis() - start);

        // update check: GitHub releases, in the background (update-check: false = off)
        updates = new dev.tmdevelopment.flarereferrals.update.UpdateChecker(this);
        updates.startup();
    }

    @Override
    public void onDisable() {
        if (unsupported) return;
        if (stats != null) stats.shutdown();
        if (events != null) events.shutdown();
        if (hints != null) hints.shutdown();
        dev.tmdevelopment.flarereferrals.service.ScreenEffects.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (dev.tmdevelopment.flarereferrals.gui.Menu.openFor(p) != null) {
                p.closeInventory();
            }
        }
        if (menuCommands != null) {
            try { menuCommands.unregisterAll(); } catch (Throwable ignored) {}
        }
        if (data != null) data.stop();
        dev.tmdevelopment.flarereferrals.util.ReferralLog.stop();
        dev.tmdevelopment.flarereferrals.gui.Menu.clearOpen();
    }

    /**
     * Adds options introduced by newer versions without touching existing values.
     * Lists of user-defined entries (currencies, auto-install plugins) are only created when missing entirely,
     * so entries the owner removed are not added back.
     */
    private boolean keepReferralRewardsAuto;

    /** After rewards.yml is loaded: finishes the claim-in-menu -> collect-menu change. */
    private void finishMigration() {
        if (!keepReferralRewardsAuto || rewards == null) return;
        keepReferralRewardsAuto = false;
        if (rewards.inviter().delivery().equals("default")) rewards.inviter().delivery("auto");
        if (rewards.invited().delivery().equals("default")) rewards.invited().delivery("auto");
        rewards.save();
    }

    /**
     * A file with a mistake at startup: nothing starts (no data, no rewards), the file is not touched, and /referrals
     * only says what to fix (a disabled plugin would answer with an error of the server instead).
     */
    private void waitForFix(String file) {
        unsupported = true;
        String[] p = null;
        for (String[] pr : dev.tmdevelopment.flarereferrals.util.SafeYaml.problems()) if (pr[0].equals(file)) p = pr;
        String where = p == null ? "" : " around line " + p[1] + " (" + p[3] + ")";
        String text = "FlareReferrals is off: " + file + " has a mistake" + where + ". Fix it and restart the server.";
        getLogger().severe(text);
        PluginCommand cmd = getCommand("referrals");
        if (cmd != null) {
            cmd.setExecutor((sender, command, label, args) -> {
                sender.sendMessage("\u00a7c" + text);
                return true;
            });
            cmd.setTabCompleter((sender, command, alias, args) -> new java.util.ArrayList<>());
        }
    }

    /** False when config.yml has a mistake (the reason is in the console): it is then never saved. */
    private boolean configReadable() {
        return dev.tmdevelopment.flarereferrals.util.SafeYaml.load(this, new java.io.File(getDataFolder(), "config.yml"), "config.yml") != null;
    }

    /** Never writes over a config.yml with a mistake: the owner's settings would be lost. */
    @Override
    public void saveConfig() {
        if (dev.tmdevelopment.flarereferrals.util.SafeYaml.broken("config.yml")) {
            getLogger().warning("config.yml was not saved because it has a mistake: fix it and use /referrals reload.");
            return;
        }
        // only what changed is written: comments, quotes and layout of the file stay as they are
        try {
            org.bukkit.configuration.file.YamlConfiguration now = new org.bukkit.configuration.file.YamlConfiguration();
            now.loadFromString(getConfig().saveToString()); // the values of the file, without the defaults of the jar
            if (dev.tmdevelopment.flarereferrals.util.YamlKeeper.keep(new java.io.File(getDataFolder(), "config.yml"), now)) return;
        } catch (Exception ignored) {
            // written again below
        }
        super.saveConfig();
    }

    /** config.yml inside the jar, as text. */
    private String defaultConfigText() {
        try (java.io.InputStream in = getResource("config.yml")) {
            return in == null ? null : new String(dev.tmdevelopment.flarereferrals.util.J8.readAll(in), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private void updateConfig() {
        org.bukkit.configuration.file.FileConfiguration cfg = getConfig();
        org.bukkit.configuration.Configuration defaults = cfg.getDefaults();
        if (defaults == null) return;
        boolean changed = false;
        // 1.0.21: milestones.claim-in-menu became rewards.collect-menu (for every reward)
        if (cfg.isSet("milestones.claim-in-menu")) {
            boolean old = cfg.getBoolean("milestones.claim-in-menu");
            if (!cfg.isSet("rewards.collect-menu")) {
                boolean newSection = !cfg.isSet("rewards");
                cfg.set("rewards.collect-menu", old);
                if (newSection) dev.tmdevelopment.flarereferrals.util.Comments.copy(cfg, defaults, "rewards");
                dev.tmdevelopment.flarereferrals.util.Comments.copy(cfg, defaults, "rewards.collect-menu");
                // before, only milestones waited in the menu: inviter and welcome rewards stay "right away"
                if (old) keepReferralRewardsAuto = true;
            }
            cfg.set("milestones.claim-in-menu", null);
            changed = true;
        }
        if (changed) saveConfig();
        java.util.List<String> missing = missingOptions(cfg, defaults);
        if (!missing.isEmpty()) {
            // written as text with the explanation above them, also on 1.16.5 - 1.17; the rest of the file is not touched
            java.io.File f = new java.io.File(getDataFolder(), "config.yml");
            String def = defaultConfigText();
            try {
                String text = new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
                String next = text;
                if (def != null) for (String key : missing) next = dev.tmdevelopment.flarereferrals.util.YamlPatch.addMissing(next, def.replace("\r\n", "\n"), key);
                if (!next.equals(text)) {
                    new org.bukkit.configuration.file.YamlConfiguration().loadFromString(next); // never write a broken file
                    dev.tmdevelopment.flarereferrals.util.J8.write(f.toPath(), next, java.nio.charset.StandardCharsets.UTF_8);
                    reloadConfig();
                    cfg = getConfig();
                    changed = true;
                }
            } catch (Exception e) {
                getLogger().fine("New options added the other way: " + e.getMessage());
            }
            // what could not be added as text (unusual layout of the file): the old way
            boolean rest = false;
            for (String key : missingOptions(cfg, defaults)) {
                dev.tmdevelopment.flarereferrals.util.Comments.add(cfg, defaults, key);
                rest = true;
            }
            if (rest) {
                super.saveConfig();
                changed = true;
            }
        }
        if (changed) getLogger().info("config.yml updated with the new options of this version.");
    }

    /** Options of the jar's config.yml missing in the server file (lists of your own entries are not completed). */
    private static java.util.List<String> missingOptions(org.bukkit.configuration.file.FileConfiguration cfg,
                                                         org.bukkit.configuration.Configuration defaults) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) continue;
            String userList = key.startsWith("currencies.") ? "currencies"
                    : key.startsWith("auto-install.plugins.") ? "auto-install.plugins" : null;
            if (userList != null && cfg.isSet(userList)) continue;
            if (!cfg.isSet(key)) out.add(key);
        }
        return out;
    }

    /**
     * What normal players can open (config.yml > player-access + permission flarereferrals.<key>).
     * Admins can always open everything. Keys: info, menu, rewards, top, preview
     */
    public boolean canOpen(org.bukkit.command.CommandSender s, String key) {
        // a menu turned off in menus/<menu>.yml (enabled: false) is off for everyone
        String layout = ("menu".equals(key) ? "main" : "rewards".equals(key) ? "rewards" : "preview".equals(key) ? "preview" : "topmenu".equals(key) ? "top" : "collect".equals(key) ? "collect" : "invited".equals(key) ? "invited" : null);
        if (layout != null && layouts != null && !layouts.get(layout).enabled()) return false;
        if (key.equals("topmenu")) key = "top";
        if (dev.tmdevelopment.flarereferrals.util.Perms.has(s, "admin")) return true;
        // "invited" (list of the invited players) is off until the admin turns it on
        return getConfig().getBoolean("player-access." + key, !"invited".equals(key)) && dev.tmdevelopment.flarereferrals.util.Perms.has(s, key);
    }

    /** Same as canOpen. Silent on purpose: things a player can't open must look like they don't exist. */
    public boolean checkOpen(org.bukkit.command.CommandSender s, String key) {
        return canOpen(s, key);
    }

    /** @return true when storage / network settings changed: they need a restart. */
    public boolean reloadAll() {
        String before = storageSettings();
        dev.tmdevelopment.flarereferrals.util.SafeYaml.clear();
        dev.tmdevelopment.flarereferrals.util.Headers.sync(this, new java.io.File(getDataFolder(), "config.yml"), "config.yml");
        // config.yml with a mistake: the last working settings stay
        if (configReadable()) {
            reloadConfig();
            updateConfig();
        }
        lang.load();
        if (data != null) dev.tmdevelopment.flarereferrals.util.ReferralLog.start(this, getConfig().getBoolean("logs.referrals", true),
                data.network() ? data.server() : "");
        currencies.load();
        currencies.checkAll();
        java.util.Set<String> offBefore = offRewards();
        rewards.load();
        finishMigration();
        referrals.checkServers();
        // rewards turned on in rewards.yml: what waited while they were off is given now
        for (dev.tmdevelopment.flarereferrals.reward.RewardSet set : allRewards()) {
            if (set.enabled() && offBefore.contains(set.id())) referrals.rewardTurnedOn(set);
        }
        layouts.load();
        menuTexts.load();
        menuCommands.refresh();
        data.reloadSettings();
        dev.tmdevelopment.flarereferrals.hook.LuckPermsHook.reset();
        events.reload();
        hints.reload();
        boolean restart = !before.equals(storageSettings());
        if (restart) getLogger().warning("Storage / network settings changed: restart the server to use them.");
        return restart;
    }

    private java.util.List<dev.tmdevelopment.flarereferrals.reward.RewardSet> allRewards() {
        java.util.List<dev.tmdevelopment.flarereferrals.reward.RewardSet> all = new java.util.ArrayList<>();
        all.add(rewards.inviter());
        all.add(rewards.invited());
        all.addAll(rewards.milestoneList());
        return all;
    }

    private java.util.Set<String> offRewards() {
        java.util.Set<String> off = new java.util.HashSet<>();
        for (dev.tmdevelopment.flarereferrals.reward.RewardSet set : allRewards()) if (!set.enabled()) off.add(set.id());
        return off;
    }

    private String storageSettings() {
        org.bukkit.configuration.file.FileConfiguration c = getConfig();
        StringBuilder sb = new StringBuilder();
        for (String k : new String[]{"storage.type", "storage.mysql.host", "storage.mysql.port", "storage.mysql.database",
                "storage.mysql.username", "storage.mysql.password", "storage.mysql.properties", "storage.mysql.table-prefix",
                "network.enabled", "network.server-name"}) {
            sb.append(c.getString(k, "")).append('|');
        }
        return sb.toString();
    }

    /** Anonymous usage statistics (TMDStats). Server owners can turn them off in plugins/TMDStats/config.yml. */
    private void startMetrics() {
        try {
            stats = new TMDStats(this, "flarereferrals", getConfig().getBoolean("stats", true));
            stats.addValue("milestone_mode", () -> referrals.claimMode() ? "Collect in menu" : "Automatic");
            stats.addValue("collect_menu", () -> getConfig().getBoolean("rewards.collect-menu", true) ? "On" : "Off");
            stats.addValue("milestones", () -> rewards.milestones().size());
            stats.addValue("storage", () -> data.type());
            stats.addValue("network", () -> data.network() ? "Network" : "Single server");
            stats.addValue("invite_hint", () -> hints.enabled() ? "On" : "Off");
            stats.addValue("events", () -> events.enabled() ? "On" : "Off");
            stats.addValue("invited_list", () -> getConfig().getBoolean("player-access.invited", false) ? "On" : "Off");
        } catch (Throwable t) {
            getLogger().fine("TMDStats not started: " + t.getMessage());
        }
    }

    private void banner(boolean papi, long ms) {
        boolean vault = Bukkit.getPluginManager().isPluginEnabled("Vault");
        String[] lines = {
                "",
                "<gradient:#ff5f6d:#ffc371>  ███████╗██╗      █████╗ ██████╗ ███████╗</gradient>",
                "<gradient:#ff5f6d:#ffc371>  ██╔════╝██║     ██╔══██╗██╔══██╗██╔════╝</gradient>",
                "<gradient:#ff5f6d:#ffc371>  █████╗  ██║     ███████║██████╔╝█████╗  </gradient>",
                "<gradient:#ff5f6d:#ffc371>  ██╔══╝  ██║     ██╔══██║██╔══██╗██╔══╝  </gradient>",
                "<gradient:#ff5f6d:#ffc371>  ██║     ███████╗██║  ██║██║  ██║███████╗</gradient>",
                "<gradient:#ff5f6d:#ffc371>  ╚═╝     ╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝╚══════╝</gradient>",
                "<gray>         R E F E R R A L S  <dark_gray>|  <white>v" + getDescription().getVersion(),
                "",
                "<gray>  Developed by <gradient:#ff5f6d:#ffc371><bold>TMDevelopment</bold></gradient>",
                "<gray>  Milestones: <white>" + rewards.milestones().size()
                        + " <dark_gray>| <gray>Currencies: <white>" + currencies.all().size()
                        + " <dark_gray>| <gray>Players: <white>" + data.size(),
                "<gray>  Storage: <white>" + data.type() + (data.network()
                        ? " <dark_gray>| <gray>Network: <green>on <gray>as <white>" + data.server() : ""),
                "<gray>  PlaceholderAPI: " + (papi ? "<green>hooked" : "<red>not found")
                        + " <dark_gray>| <gray>Vault: " + (vault ? "<green>found" : "<red>not found"),
                "<gray>  Enabled in <white>" + ms + "ms",
                ""
        };
        for (String line : lines) {
            dev.tmdevelopment.flarereferrals.util.Platform.send(Bukkit.getConsoleSender(), Text.parse(line, null));
        }
    }

    public Lang lang() { return lang; }
    public CurrencyManager currencies() { return currencies; }
    public RewardManager rewards() { return rewards; }
    public dev.tmdevelopment.flarereferrals.layout.LayoutManager layouts() { return layouts; }
    public dev.tmdevelopment.flarereferrals.layout.MenuCommands menuCommands() { return menuCommands; }
    public dev.tmdevelopment.flarereferrals.config.MenuTexts menuTexts() { return menuTexts; }
    public DataManager data() { return data; }
    public ReferralService referrals() { return referrals; }
    public ChatInput chatInput() { return chatInput; }
    public dev.tmdevelopment.flarereferrals.service.ReferralEvents events() { return events; }
    public dev.tmdevelopment.flarereferrals.service.InviteHint hints() { return hints; }
    public dev.tmdevelopment.flarereferrals.update.UpdateChecker updates() { return updates; }

    /** Admins who join hear about a new version (clickable), a few seconds after joining. */
    public void updateNotice(Player p) {
        if (updates == null || !updates.enabled() || !dev.tmdevelopment.flarereferrals.util.Perms.has(p, "admin")) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            updates.check(() -> {
                if (p.isOnline() && updates.result() == dev.tmdevelopment.flarereferrals.update.UpdateChecker.Result.UPDATE) {
                    lang.send(p, "update.join", "%version%", updates.current(), "%latest%", updates.latest(),
                            "%url%", updates.downloadUrl());
                }
            });
        }, 80L);
    }
}
