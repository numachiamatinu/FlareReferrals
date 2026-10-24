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
    private TMDStats stats;

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

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("FlaresRewards") != null) {
            getLogger().severe("The old FlaresRewards jar is also installed: remove it from plugins/ and restart, "
                    + "FlareReferrals replaces it (same commands, data already copied).");
        }
        long start = System.currentTimeMillis();
        saveDefaultConfig();
        dev.tmdevelopment.flarereferrals.util.Headers.sync(this, new java.io.File(getDataFolder(), "config.yml"), "config.yml");
        updateConfig();

        boolean papi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        Text.setPapi(papi);

        lang = new Lang(this);
        lang.load();
        currencies = new CurrencyManager(this);
        currencies.load();
        rewards = new RewardManager(this);
        rewards.load();
        layouts = new dev.tmdevelopment.flarereferrals.layout.LayoutManager(this);
        layouts.load();
        menuTexts = new dev.tmdevelopment.flarereferrals.config.MenuTexts(this);
        menuTexts.load();
        menuCommands = new dev.tmdevelopment.flarereferrals.layout.MenuCommands(this);
        menuCommands.refresh();
        data = new DataManager(this);
        data.load();
        referrals = new ReferralService(this);
        chatInput = new ChatInput(this);

        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(chatInput, this);

        // /referrals, also /freferrals /fref /freferral (plugin.yml aliases, useful if another plugin uses /referrals)
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

        // autosave every 5 minutes
        Bukkit.getScheduler().runTaskTimer(this, () -> data.save(), 6000L, 6000L);

        startMetrics();
        banner(papi, System.currentTimeMillis() - start);
    }

    @Override
    public void onDisable() {
        if (stats != null) stats.shutdown();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (dev.tmdevelopment.flarereferrals.util.Compat.topHolder(p) instanceof dev.tmdevelopment.flarereferrals.gui.Menu) {
                p.closeInventory();
            }
        }
        if (menuCommands != null) {
            try { menuCommands.unregisterAll(); } catch (Throwable ignored) {}
        }
        if (data != null) data.saveSync();
    }

    /**
     * Adds options introduced by newer versions without touching existing values.
     * Lists of user-defined entries (currencies, auto-install plugins) are only created when missing entirely,
     * so entries the owner removed are not added back.
     */
    private void updateConfig() {
        org.bukkit.configuration.file.FileConfiguration cfg = getConfig();
        org.bukkit.configuration.Configuration defaults = cfg.getDefaults();
        if (defaults == null) return;
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) continue;
            String userList = key.startsWith("currencies.") ? "currencies"
                    : key.startsWith("auto-install.plugins.") ? "auto-install.plugins" : null;
            if (userList != null && cfg.isSet(userList)) continue;
            if (!cfg.isSet(key)) {
                cfg.set(key, defaults.get(key));
                changed = true;
            }
        }
        if (changed) {
            saveConfig();
            getLogger().info("config.yml updated with the new options of this version.");
        }
    }

    /**
     * What normal players can open (config.yml > player-access + permission flarereferrals.<key>).
     * Admins can always open everything. Keys: info, menu, rewards, top, preview
     */
    public boolean canOpen(org.bukkit.command.CommandSender s, String key) {
        // a menu turned off in menus/<menu>.yml (enabled: false) is off for everyone
        String layout = ("menu".equals(key) ? "main" : "rewards".equals(key) ? "rewards" : "preview".equals(key) ? "preview" : "topmenu".equals(key) ? "top" : null);
        if (layout != null && layouts != null && !layouts.get(layout).enabled()) return false;
        if (key.equals("topmenu")) key = "top";
        if (dev.tmdevelopment.flarereferrals.util.Perms.has(s, "admin")) return true;
        return getConfig().getBoolean("player-access." + key, true) && dev.tmdevelopment.flarereferrals.util.Perms.has(s, key);
    }

    /** Same as canOpen. Silent on purpose: things a player can't open must look like they don't exist. */
    public boolean checkOpen(org.bukkit.command.CommandSender s, String key) {
        return canOpen(s, key);
    }

    public void reloadAll() {
        dev.tmdevelopment.flarereferrals.util.Headers.sync(this, new java.io.File(getDataFolder(), "config.yml"), "config.yml");
        reloadConfig();
        lang.load();
        currencies.load();
        currencies.checkAll();
        rewards.load();
        layouts.load();
        menuTexts.load();
        menuCommands.refresh();
    }

    /** Anonymous usage statistics (TMDStats). Server owners can turn them off in plugins/TMDStats/config.yml. */
    private void startMetrics() {
        try {
            stats = new TMDStats(this, "flarereferrals");
            stats.addValue("milestone_mode", () -> referrals.claimMode() ? "Collect in menu" : "Automatic");
            stats.addValue("milestones", () -> rewards.milestones().size());
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
}
