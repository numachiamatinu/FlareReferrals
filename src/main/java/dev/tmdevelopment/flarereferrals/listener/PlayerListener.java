package dev.tmdevelopment.flarereferrals.listener;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {

    private final FlareReferrals plugin;

    public PlayerListener(FlareReferrals plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.referrals().join(e.getPlayer());
        plugin.updateNotice(e.getPlayer());
        plugin.events().join(e.getPlayer());
        plugin.hints().join(e.getPlayer(), !e.getPlayer().hasPlayedBefore());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.hints().quit(e.getPlayer());
    }
}
