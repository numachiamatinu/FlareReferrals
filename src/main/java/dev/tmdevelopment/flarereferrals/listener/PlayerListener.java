package dev.tmdevelopment.flarereferrals.listener;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerListener implements Listener {

    private final FlareReferrals plugin;

    public PlayerListener(FlareReferrals plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p.getUniqueId());
        boolean trackIp = plugin.getConfig().getBoolean("referral.block-same-ip", true);
        if (d != null || trackIp) {
            d = plugin.data().getOrCreate(p.getUniqueId(), p.getName());
            if (trackIp) d.ipHash(plugin.referrals().ipHash(p));
        }
        if (d != null && !d.claimable().isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) plugin.referrals().remindClaims(p);
            }, 60L);
        }
        if (d != null && !d.pending().isEmpty()) {
            // deliver a bit later so the player is fully loaded
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) plugin.referrals().deliverPending(p);
            }, 40L);
        }
    }
}
