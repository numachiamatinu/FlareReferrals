package dev.tmdevelopment.flarereferrals.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Called on the main thread when a player writes /referrals &lt;inviter&gt; and the checks of this server passed
 * (playtime, time limit, same IP, circular...), just before the invite is saved. The invite can still be refused
 * by the database (another server saved one first): use {@link ReferralCompleteEvent} for invites that count.
 * Cancel it to block the invite: FlareReferrals then sends nothing, so tell the player why yourself.
 */
public final class ReferralEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final UUID inviter;
    private final String inviterName;
    private boolean cancelled;

    public ReferralEvent(Player player, UUID inviter, String inviterName) {
        this.player = player;
        this.inviter = inviter;
        this.inviterName = inviterName;
    }

    /** The new player who wrote the command. */
    public Player getPlayer() {
        return player;
    }

    /** The player who invited him. */
    public UUID getInviter() {
        return inviter;
    }

    public String getInviterName() {
        return inviterName;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
