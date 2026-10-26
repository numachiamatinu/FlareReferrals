package dev.tmdevelopment.flarereferrals.reward;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

public final class Milestone extends RewardSet {

    private int amount;
    private boolean repeat;
    private String name;
    private ItemStack icon;

    public Milestone(int amount) {
        super("milestone:" + amount);
        this.amount = amount;
        this.name = "&#ff7a6b" + amount + " Invites";
        this.icon = new ItemStack(Material.CHEST);
    }

    @Override
    public void load(ConfigurationSection s) {
        super.load(s);
        if (s == null) return;
        repeat = s.getBoolean("repeat", false);
        name = s.getString("name", name);
        Object ic = s.get("icon");
        if (ic instanceof ItemStack) {
            ItemStack it = (ItemStack) ic;
            icon = it;
        } else if (ic instanceof String) {
            String str = (String) ic;
            Material m = Material.matchMaterial(str);
            if (m != null && m.isItem()) icon = new ItemStack(m);
        }
    }

    @Override
    public void save(ConfigurationSection s) {
        s.set("repeat", repeat);
        s.set("name", name);
        ItemStack plain = new ItemStack(icon.getType());
        // Keep a simple material name when the icon has no custom data, otherwise the full item.
        s.set("icon", icon.isSimilar(plain) ? icon.getType().name() : icon);
        super.save(s);
    }

    public int amount() { return amount; }
    public void amount(int v) { amount = v; }
    public boolean repeat() { return repeat; }
    public void repeat(boolean v) { repeat = v; }
    public String name() { return name; }
    public void name(String v) { name = v; }
    public ItemStack icon() { return icon.clone(); }
    public void icon(ItemStack v) {
        ItemStack c = v.clone();
        c.setAmount(1);
        icon = c;
    }
}
