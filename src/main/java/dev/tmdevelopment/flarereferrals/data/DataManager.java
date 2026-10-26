package dev.tmdevelopment.flarereferrals.data;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.storage.Ops;
import dev.tmdevelopment.flarereferrals.storage.SqlStorage;
import dev.tmdevelopment.flarereferrals.storage.Storage;
import dev.tmdevelopment.flarereferrals.storage.YamlStorage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Player data in memory + the storage behind it (data.yml, sqlite or mysql).
 * Reads come from memory (menus, placeholders, top). Changes go through {@link #run}: on a database they run
 * on one background thread, then the result comes back to the main thread.
 */
public final class DataManager {

    private final FlareReferrals plugin;
    // written on the main thread only; concurrent so placeholders asked from other threads can read it safely
    private final Map<UUID, PlayerData> players = new java.util.concurrent.ConcurrentHashMap<>();
    private Storage storage;
    private ExecutorService db;
    private boolean network;
    private String server = "";
    private BukkitTask syncTask;
    private long lastEvent;
    private final Set<Long> seenEvents = new LinkedHashSet<>();
    private int polls;
    /** After /referrals migrate: no more changes until the restart (they would not be in the new storage). */
    private volatile boolean frozen;
    /** Rewards taken by this server and not handed to the player yet (id -> owner, previous state). */
    private final Map<Long, Object[]> handOff = new java.util.concurrent.ConcurrentHashMap<>();

    public DataManager(FlareReferrals plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- start / stop

    /** @return false when the data can't be loaded: the plugin must not run (it would lose or split data). */
    public boolean start() {
        FileConfiguration cfg = plugin.getConfig();
        String type = cfg.getString("storage.type", "yaml").trim().toLowerCase(Locale.ROOT);
        server = cfg.getString("network.server-name", "").trim();
        if (server.length() > 32) {
            plugin.getLogger().warning("network.server-name is longer than 32 characters, it is cut to: " + server.substring(0, 32));
            server = server.substring(0, 32);
        }
        network = cfg.getBoolean("network.enabled", false);
        if (network && !type.equals("mysql")) {
            plugin.getLogger().warning("network.enabled is true but storage.type is " + type
                    + ": the network needs storage.type: mysql. Network mode stays off.");
            network = false;
        }
        if (network && server.isEmpty()) {
            plugin.getLogger().severe("network.enabled is true but network.server-name is empty. Give every server "
                    + "its own name (lobby, skyblock, survival...). Network mode stays off.");
            network = false;
        }
        try {
            storage = create(type, network, false);
            storage.open();
            Map<UUID, PlayerData> loaded = storage.loadAll();
            if (loaded != players) {
                players.clear();
                players.putAll(loaded);
            }
            if (network) lastEvent = storage.lastEventId(); // before the database thread starts using the connection
        } catch (Throwable t) {
            plugin.getLogger().severe("Could not open the " + type + " storage: " + t.getMessage());
            if (type.equals("mysql")) {
                plugin.getLogger().severe("Check storage.mysql in config.yml (host, port, database, username, password) "
                        + "and that the database server is running. FlareReferrals stays off so no data is split or lost.");
            } else {
                plugin.getLogger().severe("FlareReferrals stays off so no data is lost. See the Storage page of the "
                        + "FlareReferrals wiki on tmstudio.dev.");
            }
            if (storage != null) storage.close();
            storage = null;
            return false;
        }
        if (storage.remote()) {
            db = Executors.newSingleThreadExecutor(r -> {
                Thread th = new Thread(r, "FlareReferrals-DB");
                th.setDaemon(true);
                return th;
            });
        }
        recoverInterrupted();
        if (network) startSync();
        return true;
    }

    /**
     * Builds a storage from config.yml. Also used by /referrals migrate for the target
     * ({@code separate} = its own data, not the data this server works on).
     */
    public Storage create(String type, boolean withNetwork, boolean separate) throws Exception {
        FileConfiguration cfg = plugin.getConfig();
        if (type.equals("sqlite")) {
            return SqlStorage.sqlite(plugin.getLogger(), new File(plugin.getDataFolder(), "data/data.db"), server);
        }
        if (type.equals("mysql")) {
            return SqlStorage.mysql(plugin.getLogger(),
                    cfg.getString("storage.mysql.host", "localhost"),
                    cfg.getInt("storage.mysql.port", 3306),
                    cfg.getString("storage.mysql.database", "flarereferrals"),
                    cfg.getString("storage.mysql.username", "root"),
                    cfg.getString("storage.mysql.password", ""),
                    cfg.getString("storage.mysql.properties", "useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8"),
                    cfg.getString("storage.mysql.table-prefix", "fr_"),
                    server, withNetwork);
        }
        if (!type.equals("yaml")) {
            plugin.getLogger().warning("storage.type '" + type + "' is unknown (yaml, sqlite, mysql): using yaml.");
        }
        return new YamlStorage(plugin, new File(plugin.getDataFolder(), "data.yml"),
                separate ? new HashMap<UUID, PlayerData>() : players);
    }

    /** Rewards that were being given when the server stopped: they are marked as given and listed in the console. */
    private void recoverInterrupted() {
        List<Object[]> stuck = new ArrayList<>();
        for (PlayerData d : players.values()) {
            for (RewardEntry e : d.rewards()) {
                if (e.state() != RewardEntry.State.DELIVERING) continue;
                String on = e.deliveredOn() == null ? "" : e.deliveredOn();
                if (!on.equals(server)) continue; // another server of the network is giving it
                stuck.add(new Object[]{d, e});
            }
        }
        if (stuck.isEmpty()) return;
        plugin.getLogger().warning(stuck.size() + " reward(s) were being given when the server stopped. They are marked "
                + "as given; check these players and give them again by hand if they did not get them:");
        for (Object[] o : stuck) {
            PlayerData d = (PlayerData) o[0];
            RewardEntry e = (RewardEntry) o[1];
            plugin.getLogger().warning("  " + d.name() + " - " + e.key());
            run(ops -> ops.moveReward(d.uuid(), e.id(), RewardEntry.State.DELIVERING, RewardEntry.State.DELIVERED, server), null);
        }
    }

    public void stop() {
        if (syncTask != null) syncTask.cancel();
        syncTask = null;
        boolean busyDb = false;
        if (db != null) {
            db.shutdown();
            try {
                if (!db.awaitTermination(10, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("Some database writes did not finish in 10 seconds.");
                    db.shutdownNow();
                    busyDb = true;
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                busyDb = true;
            }
            db = null;
        }
        if (storage != null) {
            // rewards taken but never given (server stopping): back where they were, given at the next start
            if (!handOff.isEmpty() && busyDb) {
                // the database thread may still use the connection: not touched now, listed in the console at the next start
                plugin.getLogger().warning(handOff.size() + " reward(s) were not given: they are listed in the console at the next start.");
                handOff.clear();
            }
            if (!handOff.isEmpty()) {
                try {
                    storage.transaction(ops -> {
                        for (Map.Entry<Long, Object[]> e : handOff.entrySet()) {
                            ops.moveReward((UUID) e.getValue()[0], e.getKey(), RewardEntry.State.DELIVERING,
                                    (RewardEntry.State) e.getValue()[1], server);
                        }
                        return Boolean.TRUE;
                    });
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not put back " + handOff.size() + " reward(s) not given yet: " + e.getMessage());
                }
                handOff.clear();
            }
            storage.flushSync();
            storage.close();
        }
    }

    // ---------------------------------------------------------------- changes

    /**
     * Runs a change in one transaction. {@code done} gets the result on the main thread (null = the database
     * failed: the change was not made). With data.yml everything happens right away.
     */
    public <T> void run(Function<Ops, T> work, Consumer<T> done) {
        run(work, done, false);
    }

    /**
     * Same as {@link #run}, also while paused for /referrals migrate: only to finish a reward that is being given
     * right now (it was taken before the pause), so it is never left half done.
     */
    public <T> void finish(Function<Ops, T> work, Consumer<T> done) {
        run(work, done, true);
    }

    /** Changes still running on the database thread (and their main-thread part). */
    private final java.util.concurrent.atomic.AtomicInteger inFlight = new java.util.concurrent.atomic.AtomicInteger();

    public int inFlight() {
        return inFlight.get();
    }

    private <T> void run(Function<Ops, T> work, Consumer<T> done, boolean evenFrozen) {
        if (storage == null || (frozen && !evenFrozen)) {
            if (done != null) done.accept(null);
            return;
        }
        if (!storage.remote()) {
            T value = null;
            try {
                value = storage.transaction(work).value;
            } catch (Throwable t) {
                plugin.getLogger().severe("Could not save a change: " + t.getMessage());
            }
            if (done != null) done.accept(value);
            return;
        }
        if (db == null || db.isShutdown()) {
            if (done != null) done.accept(null);
            return;
        }
        inFlight.incrementAndGet();
        db.execute(() -> {
            Storage.Result<T> res = null;
            try {
                res = storage.transaction(work);
            } catch (Throwable t) {
                plugin.getLogger().severe("Database error: " + t.getMessage());
            }
            final Storage.Result<T> r = res;
            if (!plugin.isEnabled()) {
                inFlight.decrementAndGet();
                return;
            }
            main(() -> {
                try {
                    if (r != null) {
                        Set<UUID> missing = new java.util.HashSet<>(r.touched);
                        for (PlayerData fresh : r.changed) {
                            apply(fresh);
                            missing.remove(fresh.uuid());
                        }
                        if (!missing.isEmpty()) reload(missing);
                    }
                    if (done != null) done.accept(r == null ? null : r.value);
                } finally {
                    inFlight.decrementAndGet();
                }
            });
        });
    }

    /** Message to the other servers of the network. */
    public void publish(String type, UUID target, String data) {
        if (!network) return;
        run(ops -> {
            ops.event(type, target, data);
            return Boolean.TRUE;
        }, null);
    }

    private void main(Runnable r) {
        if (!plugin.isEnabled()) return;
        try {
            Bukkit.getScheduler().runTask(plugin, r);
        } catch (RuntimeException ignored) {
            // plugin disabling
        }
    }

    /** Reads players again from the database (a read after a save failed). */
    private void reload(Set<UUID> list) {
        if (db == null || db.isShutdown()) return;
        db.execute(() -> {
            List<PlayerData> fresh = new ArrayList<>();
            for (UUID u : list) {
                try {
                    PlayerData d = storage.load(u);
                    if (d != null) fresh.add(d);
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not read " + u + " from the database: " + e.getMessage());
                }
            }
            main(() -> {
                for (PlayerData d : fresh) apply(d);
            });
        });
    }

    /** Called inside a transaction: this server took the reward and still has to give it. */
    public void taken(UUID uuid, long id, RewardEntry.State from) {
        handOff.put(id, new Object[]{uuid, from});
    }

    /** Main thread: the reward is being given now. */
    public void handed(long id) {
        handOff.remove(id);
    }

    /** No more changes until the restart (/referrals migrate finished). */
    public void freeze() {
        frozen = true;
    }

    /** The copy did not happen (error, target not empty): changes are allowed again. */
    public void unfreeze() {
        frozen = false;
    }

    public boolean frozen() {
        return frozen;
    }

    /**
     * Everything saved right now, as copies. Database thread only (called from {@link #background}):
     * with a database it reads it again, so changes still queued when the copy started are included.
     */
    public List<PlayerData> currentData() throws Exception {
        if (storage == null) return new ArrayList<>();
        if (!storage.remote()) return snapshot(); // main thread only, see MigrateCommand
        List<PlayerData> out = new ArrayList<>();
        for (PlayerData d : storage.loadAll().values()) out.add(d.copy());
        return out;
    }

    private void apply(PlayerData fresh) {
        PlayerData cur = players.get(fresh.uuid());
        if (cur == null) players.put(fresh.uuid(), fresh);
        else cur.copyFrom(fresh);
    }

    // ---------------------------------------------------------------- network sync

    private void startSync() {
        long ticks = Math.max(1, plugin.getConfig().getInt("network.sync-seconds", 3)) * 20L;
        syncTask = Bukkit.getScheduler().runTaskTimer(plugin, this::poll, ticks, ticks);
    }

    private volatile boolean polling;

    private void poll() {
        if (polling || db == null || db.isShutdown()) return;
        polling = true;
        boolean purge = ++polls % 100 == 0;
        db.execute(() -> {
            try {
                // ids are given before commit, a slower server can commit a lower id later: look back a bit
                List<Storage.Event> list = storage.events(Math.max(0, lastEvent - 50));
                List<Storage.Event> fresh = new ArrayList<>();
                Map<UUID, PlayerData> reload = new HashMap<>();
                for (Storage.Event e : list) {
                    if (!seenEvents.add(e.id)) continue;
                    lastEvent = Math.max(lastEvent, e.id);
                    if (server.equals(e.origin)) continue;
                    if ("sync".equals(e.type) && e.target != null) {
                        if (!reload.containsKey(e.target)) reload.put(e.target, storage.load(e.target));
                    } else {
                        fresh.add(e);
                    }
                }
                while (seenEvents.size() > 2000) seenEvents.remove(seenEvents.iterator().next());
                if (purge) storage.purgeEvents(System.currentTimeMillis() - 10 * 60 * 1000L);
                main(() -> {
                    for (PlayerData d : reload.values()) if (d != null) apply(d);
                    for (UUID u : reload.keySet()) plugin.referrals().remoteChange(u);
                    for (Storage.Event e : fresh) plugin.referrals().remoteEvent(e.type, e.target, e.data);
                });
            } catch (Throwable t) {
                plugin.getLogger().warning("Network sync failed: " + t.getMessage());
            } finally {
                polling = false;
            }
        });
    }

    // ---------------------------------------------------------------- reads (memory)

    public PlayerData get(UUID uuid) {
        return uuid == null ? null : players.get(uuid);
    }

    /** Known player without data yet (admin commands): created in memory and saved. */
    public PlayerData getOrCreate(UUID uuid, String name) {
        PlayerData d = players.get(uuid);
        if (d == null) {
            d = new PlayerData(uuid, name);
            players.put(uuid, d);
            run(ops -> ops.touch(uuid, name, null, 0), null);
        }
        return d;
    }

    public PlayerData byName(String name) {
        PlayerData found = null;
        for (PlayerData d : players.values()) {
            if (d.rawName() != null && d.rawName().equalsIgnoreCase(name)) {
                // the same name can be on an old account (name changed): prefer the most recent one
                if (found == null || d.firstSeen() > found.firstSeen()) found = d;
            }
        }
        return found;
    }

    public int size() {
        return players.size();
    }

    public List<PlayerData> top(int limit) {
        List<PlayerData> list = new ArrayList<>();
        for (PlayerData d : players.values()) if (d.invites() > 0) list.add(d);
        list.sort((a, b) -> a.invites() != b.invites()
                ? Integer.compare(b.invites(), a.invites())
                : a.name().compareToIgnoreCase(b.name()));
        return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
    }

    /** Copies of every player (/referrals migrate). */
    /** Rewards of a milestone not given yet (waiting in the menu or in the queue), for every player. */
    public int waiting(int milestone) {
        int n = 0;
        for (PlayerData d : players.values()) {
            for (RewardEntry e : d.rewards()) {
                if (e.milestone() == milestone && e.state() != RewardEntry.State.DELIVERED) n++;
            }
        }
        return n;
    }

    public List<PlayerData> snapshot() {
        List<PlayerData> out = new ArrayList<>();
        for (PlayerData d : players.values()) out.add(d.copy());
        return out;
    }

    /** Runs a task on the database thread (or right away with data.yml). */
    public void background(Runnable r) {
        if (db != null && !db.isShutdown()) db.execute(r);
        else {
            Thread t = new Thread(r, "FlareReferrals-Migrate");
            t.setDaemon(true);
            t.start();
        }
    }

    public void callMain(Runnable r) {
        main(r);
    }

    /** Autosave (data.yml). */
    public void save() {
        if (storage != null && !storage.remote()) storage.flush();
    }

    public boolean network() { return network; }
    /** true with SQLite / MySQL (changes run on the database thread). */
    public boolean remote() { return storage != null && storage.remote(); }
    public String server() { return server; }
    public String type() { return storage == null ? "none" : storage.type(); }
}
