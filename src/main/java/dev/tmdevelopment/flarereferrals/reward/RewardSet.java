package dev.tmdevelopment.flarereferrals.reward;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A group of rewards: items, currencies, commands, messages and broadcasts. */
public class RewardSet {

    private final String id;
    private boolean enabled = true;
    private final List<ItemStack> items = new ArrayList<>();
    private final Map<String, Double> currencies = new LinkedHashMap<>();
    private final List<String> commands = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private final List<String> broadcast = new ArrayList<>();
    private Display display = new Display();
    /** Network only: servers where this reward can be given ("all" = every server). */
    private final List<String> servers = new ArrayList<>(java.util.Collections.singletonList("all"));
    /** default = config.yml rewards.collect-menu, menu = waits in /referrals collect, auto = given right away. */
    private String delivery = "default";

    public RewardSet(String id) {
        this.id = id;
    }

    public void load(ConfigurationSection s) {
        items.clear();
        currencies.clear();
        commands.clear();
        messages.clear();
        broadcast.clear();
        display = new Display();
        if (s == null) return;
        enabled = s.getBoolean("enabled", true);
        List<?> list = s.getList("items");
        if (list != null) {
            for (Object o : list) {
                if (o instanceof ItemStack) {
                    ItemStack it = (ItemStack) o;
                    items.add(it);
                } else if (o instanceof String) {
                    String str = (String) o;
                    ItemStack it = parseShort(str);
                    if (it != null) items.add(it);
                }
            }
        }
        ConfigurationSection cur = s.getConfigurationSection("currencies");
        if (cur != null) {
            for (String k : cur.getKeys(false)) {
                double v = cur.getDouble(k);
                if (v > 0) currencies.put(k.toLowerCase(), v);
            }
        }
        commands.addAll(s.getStringList("commands"));
        messages.addAll(s.getStringList("messages"));
        broadcast.addAll(s.getStringList("broadcast"));
        display.load(s.getConfigurationSection("display"));
        List<String> sv = new ArrayList<>();
        if (s.isList("servers")) sv.addAll(s.getStringList("servers"));
        else if (s.isString("servers")) sv.add(s.getString("servers"));
        servers(sv);
        delivery(s.getString("delivery", "default"));
    }

    public void save(ConfigurationSection s) {
        s.set("enabled", enabled);
        // plain items stay short and readable ("DIAMOND:2"), items with name / lore / enchants are saved in full
        List<Object> out = new ArrayList<>();
        for (ItemStack it : items) {
            if (it == null) continue;
            out.add(it.hasItemMeta() ? it : it.getType().name() + ":" + it.getAmount());
        }
        s.set("items", out);
        s.set("currencies", null);
        ConfigurationSection cur = s.createSection("currencies");
        for (Map.Entry<String, Double> e : currencies.entrySet()) {
            double v = e.getValue();
            if (v == Math.rint(v)) cur.set(e.getKey(), (long) v);
            else cur.set(e.getKey(), v);
        }
        s.set("commands", new ArrayList<>(commands));
        s.set("messages", new ArrayList<>(messages));
        s.set("broadcast", new ArrayList<>(broadcast));
        display.save(s.createSection("display"));
        if (!allServers()) s.set("servers", new ArrayList<>(servers));
        if (!delivery.equals("default")) s.set("delivery", delivery);
    }

    /** "DIAMOND:5" or "DIAMOND". */
    private static ItemStack parseShort(String str) {
        String[] parts = str.trim().split(":");
        Material m = Material.matchMaterial(parts[0]);
        if (m == null || !m.isItem()) return null;
        int amount = 1;
        if (parts.length > 1) {
            try { amount = Math.max(1, Integer.parseInt(parts[1].trim())); } catch (NumberFormatException ignored) {}
        }
        return new ItemStack(m, amount);
    }

    public boolean isEmpty() {
        return items.isEmpty() && currencies.isEmpty() && commands.isEmpty();
    }

    public String id() { return id; }
    public boolean enabled() { return enabled; }
    public void enabled(boolean v) { enabled = v; }
    public List<ItemStack> items() { return items; }
    public Map<String, Double> currencies() { return currencies; }
    public List<String> commands() { return commands; }
    public List<String> messages() { return messages; }
    public List<String> broadcast() { return broadcast; }
    public Display display() { return display; }
    public List<String> servers() { return servers; }
    public String delivery() { return delivery; }

    public void delivery(String v) {
        String t = v == null ? "default" : v.trim().toLowerCase(java.util.Locale.ROOT);
        delivery = t.equals("menu") || t.equals("auto") ? t : "default";
    }
    public boolean allServers() { return servers.contains("all"); }

    public void servers(List<String> list) {
        servers.clear();
        for (String v : list) {
            if (v == null) continue;
            String t = v.trim().toLowerCase(java.util.Locale.ROOT);
            if (!t.isEmpty() && !servers.contains(t)) servers.add(t);
        }
        if (servers.isEmpty() || servers.contains("all")) {
            servers.clear();
            servers.add("all");
        }
    }
}
