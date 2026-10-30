package dev.tmdevelopment.flarereferrals.storage;

import dev.tmdevelopment.flarereferrals.data.PlayerData;
import dev.tmdevelopment.flarereferrals.data.RewardEntry;

import java.util.UUID;

/**
 * Data operations inside one transaction. Every check that matters on a network is done here
 * (by the database for sqlite/mysql), so two servers can't both succeed.
 * Implementations never touch the Bukkit API: with sqlite/mysql they run off the main thread.
 */
public interface Ops {

    /** Current data of a player, null when unknown. */
    PlayerData player(UUID uuid);

    /** Creates the player when missing and updates name / ip hash (when not null) / first seen (keeps the oldest). */
    PlayerData touch(UUID uuid, String name, String ipHash, long firstSeen);

    /**
     * Locks the two players until the end of the transaction (MySQL: two servers can't save an invite between
     * them at the same moment) and returns who invited {@code target}, as saved right now.
     */
    default UUID lockPair(UUID user, UUID target) {
        PlayerData t = player(target);
        return t == null ? null : t.referredBy();
    }

    /** Saves the inviter only when the player has none yet. @return false when he already has one. */
    boolean setReferrer(UUID user, UUID inviter, long at, String status);

    void clearReferrer(UUID user);

    /** pending -> verified. @return false when it was not pending (already verified by another server). */
    boolean verify(UUID user);

    /** @return the new amount (never below 0). */
    int addInvites(UUID uuid, int delta);

    void setInvites(UUID uuid, int value);

    /** @return the new entry, or null when the player already has one with this uniq key. */
    RewardEntry addReward(UUID uuid, String uniq, String reward, RewardEntry.State state);

    /** Moves a reward only when it is still in the expected state. @return false when another server was faster. */
    boolean moveReward(UUID uuid, long id, RewardEntry.State from, RewardEntry.State to, String server);

    /** Deletes the rewards whose uniq key starts with the prefix (null = all of them). */
    void deleteRewards(UUID uuid, String uniqPrefix);

    void deleteReward(UUID uuid, long id);

    void addPlaytime(UUID uuid, long seconds);

    /** A shared value of the network (the reward event...), null when unknown. Only databases keep them. */
    default String meta(String key) {
        return null;
    }

    /** Saves a shared value of the network ("" = none). Only databases keep them. */
    default void meta(String key, String value) {
    }

    /** Message for the other servers of the network (ignored without network). */
    void event(String type, UUID target, String data);
}
