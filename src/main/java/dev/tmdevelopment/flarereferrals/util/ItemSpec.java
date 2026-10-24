package dev.tmdevelopment.flarereferrals.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

/**
 * Item definitions used in menus/*.yml:
 * DIAMOND | head:{player} | head:&lt;name&gt; | texture:&lt;minecraft-heads value or id&gt; | basehead-&lt;value&gt;
 */
public final class ItemSpec {

    private ItemSpec() {}

    public static ItemStack build(Plugin plugin, String spec, Player viewer) {
        if (spec == null || dev.tmdevelopment.flarereferrals.util.J8.blank(spec)) return null;
        String s = spec.trim();
        String low = s.toLowerCase(Locale.ROOT);
        if (low.startsWith("head:")) {
            String name = s.substring(5).trim();
            if (name.equalsIgnoreCase("{player}") || name.equalsIgnoreCase("%player%")) {
                return viewer == null ? new ItemStack(Material.PLAYER_HEAD) : Heads.of(viewer);
            }
            ItemStack h = Heads.now(plugin, name);
            return h == null ? new ItemStack(Material.PLAYER_HEAD) : h;
        }
        if (low.startsWith("texture:") || low.startsWith("basehead-")) {
            String v = s.substring(low.startsWith("texture:") ? 8 : 9).trim();
            ItemStack h = Heads.now(plugin, v);
            return h == null ? new ItemStack(Material.PLAYER_HEAD) : h;
        }
        Material m = Material.matchMaterial(s);
        if (m == null || !m.isItem() || m.isAir()) return null;
        return new ItemStack(m);
    }
}
