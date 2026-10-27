package dev.tmdevelopment.flarereferrals.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Called on the main thread when an invite counts: right after /referrals &lt;inviter&gt;, or after the playtime
 * check (referral.verify-after-minutes). The rewards are already saved. On a network it is called on the server
 * where the player wrote the command.
 */
public final class ReferralCompleteEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final String playerName;
    private final UUID inviter;
    private final String inviterName;
    private final int invites;

    public ReferralCompleteEvent(UUID player, String playerName, UUID inviter, String inviterName, int invites) {
        this.player = player;
        this.playerName = playerName;
        this.inviter = inviter;
        this.inviterName = inviterName;
        this.invites = invites;
    }

    /** The invited player. */
    public UUID getPlayer() {
        return player;
    }

    public String getPlayerName() {
        return playerName;
    }

    /** The player who invited him. */
    public UUID getInviter() {
        return inviter;
    }

    public String getInviterName() {
        return inviterName;
    }

    /** Invites of the inviter now (this one included). */
    public int getInvites() {
        return invites;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
