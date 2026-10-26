package dev.tmdevelopment.flarereferrals.listener;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
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
        plugin.referrals().join(e.getPlayer());
        plugin.updateNotice(e.getPlayer());
    }
}
