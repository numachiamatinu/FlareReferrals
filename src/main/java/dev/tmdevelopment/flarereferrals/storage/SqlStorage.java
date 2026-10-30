package dev.tmdevelopment.flarereferrals.storage;

import dev.tmdevelopment.flarereferrals.data.PlayerData;
import dev.tmdevelopment.flarereferrals.data.RewardEntry;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * sqlite (one server, file data/data.db) or mysql / mariadb (one database shared by every server of a network).
 * Every change is one transaction; the checks that matter (inviter already set, reward already given...)
 * are done by the database, so two servers can never both succeed.
 * Not thread safe on purpose: the plugin calls it from one database thread.
 */
public final class SqlStorage implements Storage {

    /** Version of the tables. Raised only with a migration that keeps the old data. */
    public static final int SCHEMA = 1;

    private final Logger log;
    private final boolean mysql;
    private final String url;
    private final String user;
    private final String password;
    private final String server;
    private final boolean network;
    private final String tMeta;
    private final String tPlayers;
    private final String tRewards;
    private final String tEvents;
    private Connection conn;

    private SqlStorage(Logger log, boolean mysql, String url, String user, String password, String prefix,
                       String server, boolean network) {
        this.log = log;
        this.mysql = mysql;
        this.url = url;
        this.user = user;
        this.password = password;
        this.server = server;
        this.network = network;
        String p = prefix == null ? "fr_" : prefix.replaceAll("[^A-Za-z0-9_]", "");
        this.tMeta = p + "meta";
        this.tPlayers = p + "players";
        this.tRewards = p + "rewards";
        this.tEvents = p + "events";
    }

    public static SqlStorage sqlite(Logger log, File db, String server) throws ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        File dir = db.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
        return new SqlStorage(log, false, "jdbc:sqlite:" + db.getAbsolutePath(), null, null, "fr_", server, false);
    }

    /**
     * @param properties extra connection settings, e.g. "useSSL=false&amp;allowPublicKeyRetrieval=true"
     */
    public static SqlStorage mysql(Logger log, String host, int port, String database, String user, String password,
                                   String properties, String prefix, String server, boolean network) throws ClassNotFoundException {
        String scheme = "mysql";
        if (!loadDriver("com.mysql.cj.jdbc.Driver") && !loadDriver("com.mysql.jdbc.Driver")) {
            if (!loadDriver("org.mariadb.jdbc.Driver")) {
                throw new ClassNotFoundException("no MySQL / MariaDB driver found on this server");
            }
            scheme = "mariadb";
        }
        String props = properties == null ? "" : properties.trim();
        if (props.startsWith("?")) props = props.substring(1);
        String u = "jdbc:" + scheme + "://" + host + ":" + port + "/" + database + (props.isEmpty() ? "" : "?" + props);
        return new SqlStorage(log, true, u, user, password, prefix, server, network);
    }

    /** Any JDBC url (tests). mysql = MySQL syntax. */
    public static SqlStorage custom(Logger log, boolean mysql, String url, String user, String password, String prefix,
                                    String server, boolean network) {
        return new SqlStorage(log, mysql, url, user, password, prefix, server, network);
    }

    private static boolean loadDriver(String cls) {
        try {
            Class.forName(cls);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override public String type() { return mysql ? "mysql" : "sqlite"; }
    @Override public boolean remote() { return true; }

    // ---------------------------------------------------------------- connection

    private Connection connection() throws SQLException {
        if (conn != null) {
            try {
                if (!conn.isClosed() && conn.isValid(3)) return conn;
            } catch (SQLException ignored) {
            }
            try { conn.close(); } catch (SQLException ignored) {}
            conn = null;
        }
        conn = user == null ? DriverManager.getConnection(url) : DriverManager.getConnection(url, user, password);
        if (!mysql) {
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA busy_timeout=5000");
            }
        }
        return conn;
    }

    @Override
    public void open() throws Exception {
        Connection c = connection();
        String id = mysql ? "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";
        String tail = mysql ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : "";
        try (Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tMeta + " (k VARCHAR(64) NOT NULL PRIMARY KEY, v VARCHAR(255) NOT NULL)" + tail);
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tPlayers + " ("
                    + "uuid CHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(32), referred_by CHAR(36), "
                    + "referred_at BIGINT NOT NULL DEFAULT 0, status VARCHAR(16) NOT NULL DEFAULT 'none', "
                    + "invites INT NOT NULL DEFAULT 0, ip_hash VARCHAR(64), first_seen BIGINT NOT NULL DEFAULT 0, "
                    + "playtime BIGINT NOT NULL DEFAULT 0, code VARCHAR(32))" + tail);
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tRewards + " (id " + id + ", "
                    + "uuid CHAR(36) NOT NULL, uniq VARCHAR(128) NOT NULL, reward VARCHAR(255) NOT NULL, "
                    + "state VARCHAR(16) NOT NULL, created_at BIGINT NOT NULL DEFAULT 0, "
                    + "delivered_at BIGINT NOT NULL DEFAULT 0, delivered_on VARCHAR(64), "
                    + "UNIQUE (uuid, uniq))" + tail);
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tEvents + " (id " + id + ", "
                    + "created_at BIGINT NOT NULL, origin VARCHAR(64), type VARCHAR(16) NOT NULL, "
                    + "target CHAR(36), data VARCHAR(1024))" + tail);
        }
        String v = null;
        try (PreparedStatement ps = c.prepareStatement("SELECT v FROM " + tMeta + " WHERE k = 'schema'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) v = rs.getString(1);
        }
        if (v == null) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + tMeta + " (k, v) VALUES ('schema', ?)")) {
                ps.setString(1, String.valueOf(SCHEMA));
                ps.executeUpdate();
            } catch (SQLException e) {
                if (!duplicate(e)) throw e; // two servers starting together on an empty database
            }
        } else {
            int found;
            try {
                found = Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                found = SCHEMA;
            }
            if (found > SCHEMA) {
                throw new IllegalStateException("the database was set up by a newer FlareReferrals (schema " + found
                        + "). Update the plugin on this server too: this version would damage the data.");
            }
        }
    }

    @Override
    public void close() {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
            conn = null;
        }
    }

    // ---------------------------------------------------------------- reading

    @Override
    public Map<UUID, PlayerData> loadAll() throws Exception {
        Connection c = connection();
        Map<UUID, PlayerData> out = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM " + tPlayers); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                PlayerData d = readPlayer(rs);
                if (d != null) out.put(d.uuid(), d);
            }
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM " + tRewards + " ORDER BY id"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID u = uuid(rs.getString("uuid"));
                PlayerData d = u == null ? null : out.get(u);
                if (d != null) d.rewards().add(readReward(rs));
            }
        }
        return out;
    }

    @Override
    public PlayerData load(UUID uuid) throws Exception {
        return read(connection(), uuid);
    }

    private PlayerData read(Connection c, UUID uuid) throws SQLException {
        PlayerData d = null;
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM " + tPlayers + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) d = readPlayer(rs);
            }
        }
        if (d == null) return null;
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM " + tRewards + " WHERE uuid = ? ORDER BY id")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) d.rewards().add(readReward(rs));
            }
        }
        return d;
    }

    private static UUID uuid(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static PlayerData readPlayer(ResultSet rs) throws SQLException {
        UUID u = uuid(rs.getString("uuid"));
        if (u == null) return null;
        PlayerData d = new PlayerData(u, rs.getString("name"));
        d.referredBy(uuid(rs.getString("referred_by")));
        d.referredAt(rs.getLong("referred_at"));
        d.status(rs.getString("status"));
        d.invites(rs.getInt("invites"));
        d.ipHash(rs.getString("ip_hash"));
        d.firstSeen(rs.getLong("first_seen"));
        d.playtime(rs.getLong("playtime"));
        d.code(rs.getString("code"));
        return d;
    }

    private static RewardEntry readReward(ResultSet rs) throws SQLException {
        return new RewardEntry(rs.getLong("id"), rs.getString("uniq"), rs.getString("reward"),
                RewardEntry.State.of(rs.getString("state")), rs.getLong("created_at"), rs.getLong("delivered_at"),
                rs.getString("delivered_on"));
    }

    @Override
    public int count() throws Exception {
        try (PreparedStatement ps = connection().prepareStatement("SELECT COUNT(*) FROM " + tPlayers);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // ---------------------------------------------------------------- writing

    @Override
    public <T> Result<T> transaction(Function<Ops, T> work) throws Exception {
        for (int attempt = 1; ; attempt++) {
            try {
                return once(work);
            } catch (SQLException e) {
                // two servers changed the same rows at the same moment: the database cancelled one, try again
                boolean retry = "40001".equals(e.getSQLState()) || e.getErrorCode() == 1213 || e.getErrorCode() == 1205
                        || (!mysql && String.valueOf(e.getMessage()).contains("SQLITE_BUSY"));
                if (!retry || attempt >= 3) throw e;
                Thread.sleep(50L * attempt);
            }
        }
    }

    private <T> Result<T> once(Function<Ops, T> work) throws Exception {
        Connection c = connection();
        c.setAutoCommit(false);
        try {
            SqlOps ops = new SqlOps(c);
            T value;
            try {
                value = work.apply(ops);
            } catch (Fail f) {
                throw f.cause;
            }
            if (network) {
                for (UUID u : ops.touched) insertEvent(c, "sync", u, null);
            }
            c.commit();
            // committed: from here an error must not report the change as failed
            List<PlayerData> changed = new ArrayList<>();
            try {
                for (UUID u : ops.touched) {
                    PlayerData d = read(c, u);
                    if (d != null) changed.add(d);
                }
                c.commit();
            } catch (SQLException e) {
                log.warning("Saved, but could not read the data back: " + e.getMessage());
            }
            return new Result<>(value, changed, new ArrayList<>(ops.touched));
        } catch (Exception e) {
            try { c.rollback(); } catch (SQLException ignored) {}
            throw e;
        } finally {
            try { c.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    @Override public void flush() { }
    @Override public void flushSync() { }

    /** Replaces everything in this database with the given players (/referrals migrate). */
    @Override
    public void importAll(Collection<PlayerData> list) throws Exception {
        Connection c = connection();
        c.setAutoCommit(false);
        try {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM " + tRewards);
                st.executeUpdate("DELETE FROM " + tPlayers);
            }
            try (PreparedStatement pp = c.prepareStatement("INSERT INTO " + tPlayers
                    + " (uuid, name, referred_by, referred_at, status, invites, ip_hash, first_seen, playtime, code)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                 PreparedStatement pr = c.prepareStatement("INSERT INTO " + tRewards
                         + " (uuid, uniq, reward, state, created_at, delivered_at, delivered_on) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                for (PlayerData d : list) {
                    pp.setString(1, d.uuid().toString());
                    str(pp, 2, cut(d.rawName(), 32));
                    str(pp, 3, d.referredBy() == null ? null : d.referredBy().toString());
                    pp.setLong(4, d.referredAt());
                    pp.setString(5, d.status());
                    pp.setInt(6, d.invites());
                    str(pp, 7, d.ipHash());
                    pp.setLong(8, d.firstSeen());
                    pp.setLong(9, d.playtime());
                    str(pp, 10, d.code());
                    pp.executeUpdate();
                    Set<String> seen = new java.util.HashSet<>();
                    for (RewardEntry e : d.rewards()) {
                        if (!seen.add(e.uniq())) continue;
                        pr.setString(1, d.uuid().toString());
                        pr.setString(2, cut(e.uniq(), 128));
                        pr.setString(3, cut(e.reward(), 255));
                        pr.setString(4, e.state().id());
                        pr.setLong(5, e.created());
                        pr.setLong(6, e.delivered());
                        str(pr, 7, e.deliveredOn());
                        pr.executeUpdate();
                    }
                }
            }
            c.commit();
        } catch (Exception e) {
            try { c.rollback(); } catch (SQLException ignored) {}
            throw e;
        } finally {
            try { c.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    private static void str(PreparedStatement ps, int i, String v) throws SQLException {
        if (v == null) ps.setNull(i, Types.VARCHAR);
        else ps.setString(i, v);
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private void insertEvent(Connection c, String type, UUID target, String data) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + tEvents
                + " (created_at, origin, type, target, data) VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, System.currentTimeMillis());
            str(ps, 2, server);
            ps.setString(3, type);
            str(ps, 4, target == null ? null : target.toString());
            str(ps, 5, cut(data, 1024));
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- network events

    @Override
    public long lastEventId() throws Exception {
        try (PreparedStatement ps = connection().prepareStatement("SELECT MAX(id) FROM " + tEvents);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    @Override
    public List<Event> events(long afterId) throws Exception {
        List<Event> out = new ArrayList<>();
        try (PreparedStatement ps = connection().prepareStatement("SELECT id, origin, type, target, data FROM " + tEvents
                + " WHERE id > ? ORDER BY id")) {
            ps.setLong(1, afterId);
            ps.setMaxRows(1000);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Event(rs.getLong(1), rs.getString(2), rs.getString(3), uuid(rs.getString(4)), rs.getString(5)));
                }
            }
        }
        return out;
    }

    @Override
    public void purgeEvents(long olderThan) throws Exception {
        try (PreparedStatement ps = connection().prepareStatement("DELETE FROM " + tEvents + " WHERE created_at < ?")) {
            ps.setLong(1, olderThan);
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- operations

    /** Carries an SQLException through Function.apply. */
    private static final class Fail extends RuntimeException {
        final SQLException cause;
        Fail(SQLException cause) {
            super(cause);
            this.cause = cause;
        }
    }

    private static boolean duplicate(SQLException e) {
        String state = e.getSQLState();
        String msg = String.valueOf(e.getMessage()).toLowerCase(java.util.Locale.ROOT);
        return (state != null && state.startsWith("23")) || e.getErrorCode() == 1062 || e.getErrorCode() == 19
                || msg.contains("unique") || msg.contains("duplicate");
    }

    private final class SqlOps implements Ops {

        private final Connection c;
        final Set<UUID> touched = new LinkedHashSet<>();

        SqlOps(Connection c) {
            this.c = c;
        }

        private int update(String sql, Object... args) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) {
                    Object a = args[i];
                    if (a == null) ps.setNull(i + 1, Types.VARCHAR);
                    else if (a instanceof Integer) ps.setInt(i + 1, (Integer) a);
                    else if (a instanceof Long) ps.setLong(i + 1, (Long) a);
                    else ps.setString(i + 1, a.toString());
                }
                return ps.executeUpdate();
            } catch (SQLException e) {
                throw new Fail(e);
            }
        }

        @Override
        public PlayerData player(UUID uuid) {
            try {
                return read(c, uuid);
            } catch (SQLException e) {
                throw new Fail(e);
            }
        }

        private void ensure(UUID uuid, String name) {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM " + tPlayers + " WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return;
                }
            } catch (SQLException e) {
                throw new Fail(e);
            }
            try {
                update("INSERT INTO " + tPlayers + " (uuid, name) VALUES (?, ?)", uuid.toString(), cut(name, 32));
            } catch (Fail f) {
                if (!duplicate(f.cause)) throw f; // created at the same moment by another server
            }
        }

        @Override
        public PlayerData touch(UUID uuid, String name, String ipHash, long firstSeen) {
            ensure(uuid, name);
            if (name != null) update("UPDATE " + tPlayers + " SET name = ? WHERE uuid = ?", cut(name, 32), uuid.toString());
            if (ipHash != null) update("UPDATE " + tPlayers + " SET ip_hash = ? WHERE uuid = ?", ipHash, uuid.toString());
            if (firstSeen > 0) {
                update("UPDATE " + tPlayers + " SET first_seen = ? WHERE uuid = ? AND (first_seen = 0 OR first_seen > ?)",
                        firstSeen, uuid.toString(), firstSeen);
            }
            touched.add(uuid);
            return player(uuid);
        }

        @Override
        public UUID lockPair(UUID user, UUID target) {
            if (!mysql) return super_lockPair(target); // SQLite: one server, one writer at a time
            // always the same order, so two servers locking the same two players wait instead of blocking each other
            String a = user.toString(), b = target.toString();
            String first = a.compareTo(b) <= 0 ? a : b, second = a.compareTo(b) <= 0 ? b : a;
            UUID ref = null;
            try (PreparedStatement ps = c.prepareStatement("SELECT uuid, referred_by FROM " + tPlayers
                    + " WHERE uuid IN (?, ?) ORDER BY uuid FOR UPDATE")) {
                ps.setString(1, first);
                ps.setString(2, second);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (b.equals(rs.getString(1))) ref = uuid(rs.getString(2));
                    }
                }
            } catch (SQLException e) {
                throw new Fail(e);
            }
            return ref;
        }

        private UUID super_lockPair(UUID target) {
            PlayerData t = player(target);
            return t == null ? null : t.referredBy();
        }

        @Override
        public boolean setReferrer(UUID user, UUID inviter, long at, String status) {
            ensure(user, null);
            touched.add(user);
            return update("UPDATE " + tPlayers + " SET referred_by = ?, referred_at = ?, status = ? WHERE uuid = ? AND referred_by IS NULL",
                    inviter.toString(), at, status, user.toString()) == 1;
        }

        @Override
        public void clearReferrer(UUID user) {
            touched.add(user);
            update("UPDATE " + tPlayers + " SET referred_by = NULL, referred_at = 0, status = 'none' WHERE uuid = ?", user.toString());
        }

        @Override
        public boolean verify(UUID user) {
            touched.add(user);
            return update("UPDATE " + tPlayers + " SET status = 'verified' WHERE uuid = ? AND status = 'pending'", user.toString()) == 1;
        }

        @Override
        public int addInvites(UUID uuid, int delta) {
            ensure(uuid, null);
            touched.add(uuid);
            update("UPDATE " + tPlayers + " SET invites = CASE WHEN invites + ? < 0 THEN 0 ELSE invites + ? END WHERE uuid = ?",
                    delta, delta, uuid.toString());
            try (PreparedStatement ps = c.prepareStatement("SELECT invites FROM " + tPlayers + " WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            } catch (SQLException e) {
                throw new Fail(e);
            }
        }

        @Override
        public void setInvites(UUID uuid, int value) {
            ensure(uuid, null);
            touched.add(uuid);
            update("UPDATE " + tPlayers + " SET invites = ? WHERE uuid = ?", Math.max(0, value), uuid.toString());
        }

        private long rewardId(UUID uuid, String uniq) throws SQLException {
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM " + tRewards + " WHERE uuid = ? AND uniq = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, uniq);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : -1;
                }
            }
        }

        @Override
        public RewardEntry addReward(UUID uuid, String uniq, String reward, RewardEntry.State state) {
            String u = cut(uniq, 128);
            String r = cut(reward, 255);
            long now = System.currentTimeMillis();
            try {
                if (rewardId(uuid, u) >= 0) return null;
                try {
                    update("INSERT INTO " + tRewards + " (uuid, uniq, reward, state, created_at) VALUES (?, ?, ?, ?, ?)",
                            uuid.toString(), u, r, state.id(), now);
                } catch (Fail f) {
                    if (duplicate(f.cause)) return null; // another server created it first
                    throw f;
                }
                touched.add(uuid);
                return new RewardEntry(rewardId(uuid, u), u, r, state, now, 0, null);
            } catch (SQLException e) {
                throw new Fail(e);
            }
        }

        @Override
        public boolean moveReward(UUID uuid, long id, RewardEntry.State from, RewardEntry.State to, String srv) {
            touched.add(uuid);
            String srvName = srv == null ? "" : srv;
            // a reward being given can only be moved on by the server that is giving it
            boolean mine = from == RewardEntry.State.DELIVERING;
            String where = " WHERE id = ? AND uuid = ? AND state = ?" + (!mine ? "" : srvName.isEmpty()
                    ? " AND (delivered_on = ? OR delivered_on IS NULL)" : " AND delivered_on = ?");
            if (to == RewardEntry.State.DELIVERED) {
                return mine
                        ? update("UPDATE " + tRewards + " SET state = ?, delivered_on = ?, delivered_at = ?" + where,
                                to.id(), srvName, System.currentTimeMillis(), id, uuid.toString(), from.id(), srvName) == 1
                        : update("UPDATE " + tRewards + " SET state = ?, delivered_on = ?, delivered_at = ?" + where,
                                to.id(), srvName, System.currentTimeMillis(), id, uuid.toString(), from.id()) == 1;
            }
            return mine
                    ? update("UPDATE " + tRewards + " SET state = ?, delivered_on = ?" + where,
                            to.id(), srvName, id, uuid.toString(), from.id(), srvName) == 1
                    : update("UPDATE " + tRewards + " SET state = ?, delivered_on = ?" + where,
                            to.id(), srvName, id, uuid.toString(), from.id()) == 1;
        }

        @Override
        public void deleteRewards(UUID uuid, String uniqPrefix) {
            touched.add(uuid);
            if (uniqPrefix == null) update("DELETE FROM " + tRewards + " WHERE uuid = ?", uuid.toString());
            else update("DELETE FROM " + tRewards + " WHERE uuid = ? AND uniq LIKE ?", uuid.toString(), uniqPrefix + "%");
        }

        @Override
        public void deleteReward(UUID uuid, long id) {
            touched.add(uuid);
            update("DELETE FROM " + tRewards + " WHERE uuid = ? AND id = ?", uuid.toString(), id);
        }

        @Override
        public void addPlaytime(UUID uuid, long seconds) {
            // not "touched": other servers don't need a refresh for playtime
            update("UPDATE " + tPlayers + " SET playtime = playtime + ? WHERE uuid = ?", seconds, uuid.toString());
        }

        @Override
        public String meta(String key) {
            try (PreparedStatement ps = c.prepareStatement("SELECT v FROM " + tMeta + " WHERE k = ?")) {
                ps.setString(1, cut(key, 64));
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            } catch (SQLException e) {
                throw new Fail(e);
            }
        }

        @Override
        public void meta(String key, String value) {
            String v = cut(value == null ? "" : value, 255);
            if (update("UPDATE " + tMeta + " SET v = ? WHERE k = ?", v, cut(key, 64)) > 0) return;
            try {
                update("INSERT INTO " + tMeta + " (k, v) VALUES (?, ?)", cut(key, 64), v);
            } catch (Fail f) {
                if (!duplicate(f.cause)) throw f; // written at the same moment by another server
                update("UPDATE " + tMeta + " SET v = ? WHERE k = ?", v, cut(key, 64));
            }
        }

        @Override
        public void event(String type, UUID target, String data) {
            if (!network) return;
            try {
                insertEvent(c, type, target, data);
            } catch (SQLException e) {
                throw new Fail(e);
            }
        }
    }
}
