package dev.tmdevelopment.flarereferrals.storage;

import dev.tmdevelopment.flarereferrals.data.PlayerData;
import dev.tmdevelopment.flarereferrals.data.RewardEntry;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Default storage: everything in plugins/FlareReferrals/data.yml (single server).
 * The data lives in memory and the file is written in the background after every change.
 */
public final class YamlStorage implements Storage {

    /** Version of the data.yml format. 1 = up to 1.0.20, 2 = reward list (1.0.21). */
    public static final int VERSION = 2;

    private final Plugin plugin;
    private final File file;
    private final Map<UUID, PlayerData> players;
    private final Object ioLock = new Object();
    private final AtomicLong saveSeq = new AtomicLong();
    private long writtenSeq = -1;
    private long nextId = 1;
    private boolean loaded;

    /** @param players the map the plugin works on: this storage changes it directly. */
    public YamlStorage(Plugin plugin, File file, Map<UUID, PlayerData> players) {
        this.plugin = plugin;
        this.file = file;
        this.players = players;
    }

    @Override public String type() { return "yaml"; }
    @Override public boolean remote() { return false; }

    @Override
    public void open() {
        File dir = file.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
    }

    @Override
    public void close() {
    }

    @Override
    public Map<UUID, PlayerData> loadAll() throws Exception {
        players.clear();
        nextId = 1;
        loaded = true;
        if (!file.exists()) return players;
        YamlConfiguration yml = new YamlConfiguration();
        try {
            yml.load(file);
        } catch (Exception e) {
            // a broken file must stop the plugin: running empty would save an empty data.yml over it
            throw new IllegalStateException("data.yml can't be read (" + e.getMessage() + "). Fix the file or put back "
                    + "a copy from plugins/FlareReferrals/backups/. Nothing was changed.");
        }
        int version = yml.getInt("data-version", 1);
        if (version > VERSION) {
            throw new IllegalStateException("data.yml was saved by a newer FlareReferrals (data version " + version
                    + "). Install that version again: this one would damage the file.");
        }
        ConfigurationSection sec = yml.getConfigurationSection("players");
        boolean convert = version < VERSION && sec != null && !sec.getKeys(false).isEmpty();
        if (convert) backup("data-v" + version);
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                ConfigurationSection s = sec.getConfigurationSection(key);
                if (s == null) continue;
                PlayerData d = version < 2 ? readV1(uuid, s) : readV2(uuid, s);
                players.put(uuid, d);
            }
        }
        long maxId = 0;
        for (PlayerData d : players.values()) for (RewardEntry e : d.rewards()) maxId = Math.max(maxId, e.id());
        nextId = Math.max(maxId + 1, yml.getLong("next-reward-id", 1));
        if (convert) {
            flushSync();
            plugin.getLogger().info("data.yml converted to the new format (" + players.size()
                    + " players). A copy of the old file is in plugins/FlareReferrals/backups/.");
        }
        return players;
    }

    /** Format up to 1.0.20: lists claimed / claimable / pending. */
    private PlayerData readV1(UUID uuid, ConfigurationSection s) {
        PlayerData d = readBase(uuid, s);
        if (d.referredBy() != null) d.status(PlayerData.VERIFIED);
        List<Integer> claimable = new ArrayList<>(s.getIntegerList("claimable"));
        // the old format has no date for milestones: the day of the update
        long now = System.currentTimeMillis();
        for (Integer amount : s.getIntegerList("claimed")) {
            boolean ready = claimable.remove(amount);
            d.rewards().add(new RewardEntry(nextId++, "milestone:" + amount, "milestone:" + amount + "|-|-",
                    ready ? RewardEntry.State.CLAIMABLE : RewardEntry.State.DELIVERED, now, 0, null));
        }
        int i = 0;
        for (Integer amount : claimable) { // repeating milestones waiting to be collected
            d.rewards().add(new RewardEntry(nextId++, "milestone:" + amount + "@old" + (i++), "milestone:" + amount + "|-|-",
                    RewardEntry.State.CLAIMABLE, now, 0, null));
        }
        i = 0;
        for (String p : s.getStringList("pending")) {
            d.rewards().add(new RewardEntry(nextId++, "old-pending:" + (i++), p, RewardEntry.State.PENDING,
                    System.currentTimeMillis(), 0, null));
        }
        return d;
    }

    private PlayerData readV2(UUID uuid, ConfigurationSection s) {
        PlayerData d = readBase(uuid, s);
        d.status(s.getString("status", d.referredBy() == null ? PlayerData.NONE : PlayerData.VERIFIED));
        d.firstSeen(s.getLong("first-seen"));
        d.playtime(s.getLong("playtime"));
        d.code(s.getString("code"));
        ConfigurationSection rs = s.getConfigurationSection("rewards");
        if (rs != null) {
            for (String id : rs.getKeys(false)) {
                ConfigurationSection r = rs.getConfigurationSection(id);
                if (r == null) continue;
                long n;
                try {
                    n = Long.parseLong(id);
                } catch (NumberFormatException e) {
                    continue;
                }
                String uniq = r.getString("uniq");
                String reward = r.getString("reward");
                if (uniq == null || reward == null) continue;
                d.rewards().add(new RewardEntry(n, uniq, reward, RewardEntry.State.of(r.getString("state")),
                        r.getLong("created"), r.getLong("delivered"), r.getString("on")));
            }
        }
        return d;
    }

    private PlayerData readBase(UUID uuid, ConfigurationSection s) {
        PlayerData d = new PlayerData(uuid, s.getString("name"));
        String ref = s.getString("referred-by");
        if (ref != null && !ref.isEmpty()) {
            try { d.referredBy(UUID.fromString(ref)); } catch (IllegalArgumentException ignored) {}
        }
        d.referredAt(s.getLong("referred-at"));
        d.invites(s.getInt("invites"));
        d.ipHash(s.getString("ip"));
        return d;
    }

    private void backup(String name) {
        try {
            File dir = new File(file.getParentFile(), "backups");
            dir.mkdirs();
            String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
            Files.copy(file.toPath(), new File(dir, name + "-" + stamp + ".yml").toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not back up data.yml before converting it: " + e.getMessage()
                    + ". Nothing was changed.");
        }
    }

    @Override
    public PlayerData load(UUID uuid) {
        return players.get(uuid);
    }

    @Override
    public <T> Result<T> transaction(Function<Ops, T> work) {
        MemoryOps ops = new MemoryOps();
        T value = work.apply(ops);
        if (ops.urgent) flushNow();
        else flushSoon();
        return new Result<>(value, Collections.<PlayerData>emptyList(), Collections.<UUID>emptyList());
    }

    @Override
    public int count() throws Exception {
        if (!loaded) loadAll();
        return players.size();
    }

    @Override
    public void importAll(Collection<PlayerData> list) {
        if (file.exists()) backup("data-before-import");
        players.clear();
        long maxId = 0;
        for (PlayerData d : list) {
            players.put(d.uuid(), d.copy());
            for (RewardEntry e : d.rewards()) maxId = Math.max(maxId, e.id());
        }
        nextId = maxId + 1;
        flushSync();
    }

    // ---------------------------------------------------------------- saving

    private String serialize(Collection<PlayerData> list, long next) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("data-version", VERSION);
        yml.set("next-reward-id", next);
        for (PlayerData d : list) {
            String base = "players." + d.uuid();
            yml.set(base + ".name", d.rawName());
            if (d.referredBy() != null) {
                yml.set(base + ".referred-by", d.referredBy().toString());
                yml.set(base + ".referred-at", d.referredAt());
                yml.set(base + ".status", d.status());
            }
            yml.set(base + ".invites", d.invites());
            if (d.ipHash() != null) yml.set(base + ".ip", d.ipHash());
            if (d.firstSeen() > 0) yml.set(base + ".first-seen", d.firstSeen());
            if (d.playtime() > 0) yml.set(base + ".playtime", d.playtime());
            if (d.code() != null) yml.set(base + ".code", d.code());
            for (RewardEntry e : d.rewards()) {
                String r = base + ".rewards." + e.id();
                yml.set(r + ".uniq", e.uniq());
                yml.set(r + ".reward", e.reward());
                yml.set(r + ".state", e.state().id());
                yml.set(r + ".created", e.created());
                if (e.delivered() > 0) yml.set(r + ".delivered", e.delivered());
                if (e.deliveredOn() != null) yml.set(r + ".on", e.deliveredOn());
            }
        }
        return yml.saveToString();
    }

    private boolean flushScheduled;
    private boolean urgentScheduled;

    /** Many changes in a row (players joining) = one save one second later. */
    private void flushSoon() {
        if (!plugin.isEnabled()) {
            flushSync();
            return;
        }
        if (flushScheduled) return;
        flushScheduled = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            flushScheduled = false;
            flush();
        }, 20L);
    }

    /** Rewards and inviters: saved at the next tick, once for all the changes of this tick (a referral makes several). */
    private void flushNow() {
        if (!plugin.isEnabled()) {
            flushSync();
            return;
        }
        if (urgentScheduled) return;
        urgentScheduled = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            urgentScheduled = false;
            flush();
        });
    }

    /**
     * Copies the data on the calling (main) thread, then builds the file and writes it in the background:
     * the server doesn't wait for a big data.yml to be written.
     */
    @Override
    public void flush() {
        List<PlayerData> copy = new ArrayList<>(players.size());
        for (PlayerData d : players.values()) copy.add(d.copy());
        long next = nextId;
        long seq = saveSeq.incrementAndGet();
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(serialize(copy, next), seq));
        } else {
            write(serialize(copy, next), seq);
        }
    }

    @Override
    public void flushSync() {
        write(serialize(players.values(), nextId), saveSeq.incrementAndGet());
    }

    private void write(String content, long seq) {
        synchronized (ioLock) {
            if (seq < writtenSeq) return; // a newer copy was already written
            writtenSeq = seq;
            try {
                File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
                dev.tmdevelopment.flarereferrals.util.J8.write(tmp.toPath(), content, StandardCharsets.UTF_8);
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------- operations on memory

    private final class MemoryOps implements Ops {

        /** true = saved right away (rewards, inviters); false = within a second (join, playtime). */
        boolean urgent;

        private PlayerData create(UUID uuid, String name) {
            PlayerData d = players.get(uuid);
            if (d == null) {
                d = new PlayerData(uuid, name);
                players.put(uuid, d);
            }
            return d;
        }

        @Override public PlayerData player(UUID uuid) { return players.get(uuid); }

        @Override
        public PlayerData touch(UUID uuid, String name, String ipHash, long firstSeen) {
            PlayerData d = create(uuid, name);
            d.name(name);
            if (ipHash != null) d.ipHash(ipHash);
            if (firstSeen > 0 && (d.firstSeen() == 0 || d.firstSeen() > firstSeen)) d.firstSeen(firstSeen);
            return d;
        }

        @Override
        public boolean setReferrer(UUID user, UUID inviter, long at, String status) {
            urgent = true;
            PlayerData d = create(user, null);
            if (d.referredBy() != null) return false;
            d.referredBy(inviter);
            d.referredAt(at);
            d.status(status);
            return true;
        }

        @Override
        public void clearReferrer(UUID user) {
            urgent = true;
            PlayerData d = players.get(user);
            if (d == null) return;
            d.referredBy(null);
            d.referredAt(0);
            d.status(PlayerData.NONE);
        }

        @Override
        public boolean verify(UUID user) {
            urgent = true;
            PlayerData d = players.get(user);
            if (d == null || !d.pendingInvite()) return false;
            d.status(PlayerData.VERIFIED);
            return true;
        }

        @Override
        public int addInvites(UUID uuid, int delta) {
            urgent = true;
            PlayerData d = create(uuid, null);
            d.invites(d.invites() + delta);
            return d.invites();
        }

        @Override
        public void setInvites(UUID uuid, int value) {
            urgent = true;
            create(uuid, null).invites(value);
        }

        @Override
        public RewardEntry addReward(UUID uuid, String uniq, String reward, RewardEntry.State state) {
            urgent = true;
            PlayerData d = create(uuid, null);
            if (d.hasUniq(uniq)) return null;
            RewardEntry e = new RewardEntry(nextId++, uniq, reward, state, System.currentTimeMillis(), 0, null);
            d.rewards().add(e);
            return e;
        }

        @Override
        public boolean moveReward(UUID uuid, long id, RewardEntry.State from, RewardEntry.State to, String server) {
            urgent = true;
            PlayerData d = players.get(uuid);
            RewardEntry e = d == null ? null : d.reward(id);
            if (e == null || e.state() != from) return false;
            e.state(to);
            e.deliveredOn(server);
            if (to == RewardEntry.State.DELIVERED) e.delivered(System.currentTimeMillis());
            return true;
        }

        @Override
        public void deleteRewards(UUID uuid, String uniqPrefix) {
            urgent = true;
            PlayerData d = players.get(uuid);
            if (d == null) return;
            if (uniqPrefix == null) d.rewards().clear();
            else d.rewards().removeIf(e -> e.uniq().startsWith(uniqPrefix));
        }

        @Override
        public void deleteReward(UUID uuid, long id) {
            urgent = true;
            PlayerData d = players.get(uuid);
            if (d != null) d.rewards().removeIf(e -> e.id() == id);
        }

        @Override
        public void addPlaytime(UUID uuid, long seconds) {
            PlayerData d = players.get(uuid);
            if (d != null) d.playtime(d.playtime() + seconds);
        }

        @Override
        public void event(String type, UUID target, String data) {
        }
    }
}
