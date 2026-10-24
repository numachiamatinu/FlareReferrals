package dev.tmdevelopment.flarereferrals.currency;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Only loaded when Vault is installed. */
final class VaultHook {

    private static Economy economy;

    private VaultHook() {}

    /** true when Vault is installed and an economy plugin (EssentialsX, CMI...) is registered. */
    static boolean available() {
        if (economy != null) return true;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return true;
    }

    static boolean deposit(OfflinePlayer player, double amount) {
        if (economy == null) {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp == null) return false;
            economy = rsp.getProvider();
        }
        return economy.depositPlayer(player, amount).transactionSuccess();
    }
}
