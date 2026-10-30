package dev.tmdevelopment.flarereferrals.gui;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /referrals settings > Player access: what normal players can open. */
public final class PlayerAccessMenu extends Menu {

    private static final String[] KEYS = {"info", "menu", "rewards", "top", "preview", "collect", "invited"};
    private static final Material[] ICONS = {Material.WRITABLE_BOOK, Material.CHEST, Material.EMERALD,
            Material.KNOWLEDGE_BOOK, Material.ENDER_EYE, Material.CHEST_MINECART, Material.PLAYER_HEAD};

    private final Menu parent;

    public PlayerAccessMenu(FlareReferrals plugin, Player viewer, Menu parent) {
        super(plugin, viewer);
        this.parent = parent;
    }

    @Override protected Component title() { return lang().title("gui.access.title", viewer); }
    @Override protected int rows() { return 4; }

    @Override
    protected void build() {
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < KEYS.length; i++) {
            String key = KEYS[i];
            boolean on = cfg.getBoolean("player-access." + key, !"invited".equals(key));
            List<Component> lore = new ArrayList<>(lang().lore("gui.access.options." + key + ".lore", viewer));
            lore.addAll(lang().lore("gui.access.toggle-lore", viewer, "%status%", status(on)));
            set(slots[i], new ItemBuilder(ICONS[i])
                    .name(lang().item("gui.access.options." + key + ".name", viewer))
                    .lore(lore).glow(on).hideFlags().build(), e -> {
                cfg.set("player-access." + key, !on);
                plugin.saveConfig();
                plugin.menuCommands().refresh(); // players' command lists follow the new access
                refresh();
            });
        }
        set(4, button(Material.BOOK, "gui.access.help"));
        backButton(31, parent);
        fillBorder();
    }
}
