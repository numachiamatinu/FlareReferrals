package dev.tmdevelopment.flarereferrals.storage;

import dev.tmdevelopment.flarereferrals.data.PlayerData;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Where the data lives: yaml (data.yml), sqlite (data/data.db) or mysql (shared by a network). */
public interface Storage {

    /** yaml, sqlite or mysql. */
    String type();

    /** true = a database: calls run off the main thread and the data can change from other servers. */
    boolean remote();

    void open() throws Exception;

    void close();

    /** Reads every player. */
    Map<UUID, PlayerData> loadAll() throws Exception;

    /** Fresh data of one player (null when unknown). */
    PlayerData load(UUID uuid) throws Exception;

    /** Runs the work in one transaction: all or nothing. */
    <T> Result<T> transaction(Function<Ops, T> work) throws Exception;

    /** Saves the file (yaml). Databases save at every transaction. */
    void flush();

    void flushSync();

    /** @return number of players in the storage. */
    int count() throws Exception;

    /** Copies players and rewards into this (empty) storage: /referrals migrate. */
    void importAll(Collection<PlayerData> players) throws Exception;

    // ---------------------------------------------------------------- network events

    default long lastEventId() throws Exception { return 0; }

    default List<Event> events(long afterId) throws Exception { return Collections.emptyList(); }

    default void purgeEvents(long olderThan) throws Exception {
    }

    final class Result<T> {
        public final T value;
        /** Fresh copies of the players the transaction changed (databases only). */
        public final List<PlayerData> changed;
        /** Players the transaction changed (a copy may be missing in "changed" when reading it back failed). */
        public final List<UUID> touched;

        public Result(T value, List<PlayerData> changed, List<UUID> touched) {
            this.value = value;
            this.changed = changed;
            this.touched = touched;
        }
    }

    final class Event {
        public final long id;
        public final String origin;
        public final String type;
        public final UUID target;
        public final String data;

        public Event(long id, String origin, String type, UUID target, String data) {
            this.id = id;
            this.origin = origin;
            this.type = type;
            this.target = target;
            this.data = data;
        }
    }
}
