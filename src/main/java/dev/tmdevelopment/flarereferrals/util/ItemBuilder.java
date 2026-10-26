package dev.tmdevelopment.flarereferrals.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;

public final class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this(new ItemStack(material));
    }

    public ItemBuilder(ItemStack base) {
        this.item = base.clone();
        this.meta = item.getItemMeta();
    }

    public ItemBuilder name(Component name) {
        if (meta != null) meta.setDisplayName(Platform.item(name));
        return this;
    }

    public ItemBuilder lore(List<Component> lore) {
        if (meta != null) meta.setLore(Platform.items(lore));
        return this;
    }

    /** Appends lines after the existing lore (keeps the lore of custom items). */
    public ItemBuilder addLore(List<Component> extra) {
        if (meta == null) return this;
        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<String>();
        lore.addAll(Platform.items(extra));
        meta.setLore(lore);
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize())));
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        if (meta != null && glow) Compat.glow(meta);
        return this;
    }

    public ItemBuilder customModelData(Integer data) {
        try {
            if (meta != null) meta.setCustomModelData(data);
        } catch (Throwable ignored) {
        }
        return this;
    }

    public ItemBuilder hideFlags() {
        if (meta != null) meta.addItemFlags(ItemFlag.values());
        return this;
    }

    public ItemBuilder skull(OfflinePlayer owner) {
        if (meta instanceof SkullMeta && owner != null) Heads.setOwner((SkullMeta) meta, owner);
        return this;
    }

    public ItemStack build() {
        if (meta != null) item.setItemMeta(meta);
        return item;
    }
}
