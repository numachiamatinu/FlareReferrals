package dev.tmdevelopment.flarereferrals.data;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DataManager {

    private final FlareReferrals plugin;
    private final File file;
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final Object ioLock = new Object();
    private final java.util.concurrent.atomic.AtomicLong saveSeq = new java.util.concurrent.atomic.AtomicLong();
    private long writtenSeq = -1;

    public DataManager(FlareReferrals plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        players.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yml.getConfigurationSection("players");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) continue;
            PlayerData d = new PlayerData(uuid, s.getString("name"));
            String ref = s.getString("referred-by");
            if (ref != null && !ref.isEmpty()) {
                try { d.referredBy(UUID.fromString(ref)); } catch (IllegalArgumentException ignored) {}
            }
            d.referredAt(s.getLong("referred-at"));
            d.invites(s.getInt("invites"));
            d.ipHash(s.getString("ip"));
            d.claimed().addAll(s.getIntegerList("claimed"));
            d.claimable().addAll(s.getIntegerList("claimable"));
            d.pending().addAll(s.getStringList("pending"));
            players.put(uuid, d);
        }
    }

    public PlayerData get(UUID uuid) {
        return players.get(uuid);
    }

    public PlayerData getOrCreate(UUID uuid, String name) {
        PlayerData d = players.computeIfAbsent(uuid, u -> new PlayerData(u, name));
        if (name != null) d.name(name);
        return d;
    }

    public PlayerData byName(String name) {
        for (PlayerData d : players.values()) {
            if (d.name().equalsIgnoreCase(name)) return d;
        }
        return null;
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

    private String serialize() {
        YamlConfiguration yml = new YamlConfiguration();
        for (PlayerData d : players.values()) {
            String base = "players." + d.uuid();
            yml.set(base + ".name", d.name());
            yml.set(base + ".referred-by", d.referredBy() == null ? null : d.referredBy().toString());
            if (d.referredAt() > 0) yml.set(base + ".referred-at", d.referredAt());
            yml.set(base + ".invites", d.invites());
            if (d.ipHash() != null) yml.set(base + ".ip", d.ipHash());
            if (!d.claimed().isEmpty()) yml.set(base + ".claimed", new ArrayList<>(d.claimed()));
            if (!d.claimable().isEmpty()) yml.set(base + ".claimable", new ArrayList<>(d.claimable()));
            if (!d.pending().isEmpty()) yml.set(base + ".pending", new ArrayList<>(d.pending()));
        }
        return yml.saveToString();
    }

    /** Serializes on the main thread, writes the file async. */
    public void save() {
        String content = serialize();
        long seq = saveSeq.incrementAndGet();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(content, seq));
    }

    public void saveSync() {
        write(serialize(), saveSeq.incrementAndGet());
    }

    private void write(String content, long seq) {
        synchronized (ioLock) {
            if (seq < writtenSeq) return; // a newer snapshot was already written
            writtenSeq = seq;
            try {
                File tmp = new File(file.getParentFile(), "data.yml.tmp");
                dev.tmdevelopment.flarereferrals.util.J8.write(tmp.toPath(), content, StandardCharsets.UTF_8);
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save data.yml: " + e.getMessage());
            }
        }
    }
}
