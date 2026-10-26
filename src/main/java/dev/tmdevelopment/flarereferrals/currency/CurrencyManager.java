package dev.tmdevelopment.flarereferrals.currency;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CurrencyManager {

    private final FlareReferrals plugin;
    private final Map<String, Currency> currencies = new LinkedHashMap<>();

    public CurrencyManager(FlareReferrals plugin) {
        this.plugin = plugin;
    }

    public void load() {
        currencies.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("currencies");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection c = sec.getConfigurationSection(id);
            if (c == null || !c.getBoolean("enabled", true)) continue;
            Currency.Type type;
            try {
                type = Currency.Type.valueOf(c.getString("type", "COMMAND").toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Currency '" + id + "' has an invalid type, skipped.");
                continue;
            }
            Material icon = Material.matchMaterial(c.getString("icon", "GOLD_NUGGET"));
            if (icon == null || !icon.isItem()) icon = Material.GOLD_NUGGET;
            currencies.put(id.toLowerCase(), new Currency(
                    id.toLowerCase(), type,
                    c.getString("command", ""),
                    c.getString("name", id),
                    c.getString("display", "%amount% " + id),
                    icon));
        }
    }

    public Collection<Currency> all() {
        return currencies.values();
    }

    public Currency get(String id) {
        return id == null ? null : currencies.get(id.toLowerCase());
    }

    public String display(String id, double amount) {
        Currency c = get(id);
        String amt = Text.formatAmount(amount);
        // a currency turned off is shown only in the admin menus, marked as off
        return c == null ? "&7" + amt + " " + id + " &c(off)" : Text.replace(c.display(), "%amount%", amt);
    }

    private final java.util.Set<String> warned = new java.util.HashSet<>();

    /** null when the currency works, otherwise the reason (shown in console and in the currency menu). */
    public String problem(Currency c) {
        if (c.type() == Currency.Type.VAULT) {
                if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) return "Vault is not installed";
                boolean eco;
                try { eco = VaultHook.available(); } catch (Throwable t) { eco = false; }
                return eco ? null : "no economy plugin (EssentialsX, CMI...) is connected to Vault";
            }
        else if (c.type() == Currency.Type.COMMAND) {
                String cmd = c.command() == null ? "" : c.command().trim();
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                if (cmd.isEmpty()) return "no command set";
                String label = cmd.split(" ")[0].toLowerCase(java.util.Locale.ROOT);
                try {
                    if (dev.tmdevelopment.flarereferrals.util.Compat.commandMap().getCommand(label) == null) {
                        return "the command /" + label + " does not exist (is its plugin installed?)";
                    }
                } catch (Throwable ignored) {
                }
                return null;
            }
        else {
                return null;
            }
    }

    /** Logs once which currencies can't work right now (called when the server finished loading). */
    public void checkAll() {
        warned.clear();
        for (Currency c : currencies.values()) {
            String p = problem(c);
            if (p != null) {
                warned.add(c.id());
                plugin.getLogger().warning("Currency '" + c.id() + "' is not available: " + p
                        + ". Rewards with it are skipped. Fix it or set enabled: false in config.yml > currencies." + c.id());
            }
        }
    }

    public void give(Player player, String id, double amount) {
        Currency c = get(id);
        if (c == null) {
            if (warned.add("unknown:" + id)) {
                plugin.getLogger().warning("A reward uses the currency '" + id + "' which is disabled or not in config.yml: skipped.");
            }
            return;
        }
        if (amount <= 0) return;
        String problem = problem(c);
        if (problem != null) {
            if (warned.add(c.id())) {
                plugin.getLogger().warning("Currency '" + c.id() + "' skipped: " + problem + ".");
            }
            return;
        }
        if (c.type() == Currency.Type.VAULT) {
                boolean ok = false;
                if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
                    try {
                        ok = VaultHook.deposit(player, amount);
                    } catch (Throwable t) {
                        ok = false;
                    }
                }
                if (!ok) plugin.getLogger().warning("Could not deposit " + amount + " (" + id + ") to "
                        + player.getName() + ": the economy plugin refused the payment.");
            }
        else if (c.type() == Currency.Type.COMMAND) {
                String amt = amount == Math.rint(amount) ? String.valueOf((long) amount) : String.valueOf(amount);
                String cmd = Text.replace(c.command(),
                        "%player%", player.getName(),
                        "%uuid%", player.getUniqueId().toString(),
                        "%amount%", amt);
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                if (!dev.tmdevelopment.flarereferrals.util.J8.blank(cmd)) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        else if (c.type() == Currency.Type.EXP) { player.giveExp((int) amount); }
        else if (c.type() == Currency.Type.EXP_LEVELS) { player.giveExpLevels((int) amount); }
    }
}
