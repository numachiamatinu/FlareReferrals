package dev.tmdevelopment.flarereferrals.gui;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.data.PlayerData;
import dev.tmdevelopment.flarereferrals.layout.LayoutMenu;
import dev.tmdevelopment.flarereferrals.util.Heads;
import dev.tmdevelopment.flarereferrals.util.ItemBuilder;
import dev.tmdevelopment.flarereferrals.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Top inviters - layout: menus/top.yml (the "entries" slots decide how many players are shown and where). */
public final class TopMenu extends LayoutMenu {

    private final Menu parent;

    public TopMenu(FlareReferrals plugin, Player viewer, Menu parent) {
        super(plugin, viewer, "top");
        this.parent = parent;
    }

    @Override protected Component defaultTitle() { return lang().title("gui.top.title", viewer); }

    @Override
    public Map<String, Material> palette() {
        Map<String, Material> m = new LinkedHashMap<>();
        m.put("entries", Material.PLAYER_HEAD);
        m.put("back", Material.ARROW);
        return m;
    }

    @Override
    protected void buildElements() {
        int amount = Math.max(1, slots("entries").size());
        List<PlayerData> top = plugin.data().top(amount);
        List<ItemStack> items = new ArrayList<>();
        dev.tmdevelopment.flarereferrals.layout.MenuLayout.Element tpl = template("entries");
        String nameTpl = tpl.name != null ? tpl.name : lang().raw("gui.top.entry.name");
        List<String> loreTpl = tpl.lore != null ? tpl.lore : lang().lines("gui.top.entry.lore");
        for (int i = 0; i < top.size(); i++) {
            PlayerData d = top.get(i);
            String[] ph = {"%position%", String.valueOf(i + 1), "%player%", d.name(), "%invites%", String.valueOf(d.invites())};
            items.add(new ItemBuilder(Heads.of(Bukkit.getOfflinePlayer(d.uuid())))
                    .name(Text.item(Text.replace(nameTpl, ph), viewer))
                    .lore(Text.items(Text.replace(loreTpl, ph), viewer))
                    .amount(Math.min(64, i + 1))
                    .build());
        }
        list("entries", items, null, button(Material.BARRIER, "gui.top.empty"));
        element("back", new ItemStack(Material.ARROW), parent == null ? "gui.common.close" : "gui.common.back", null, false, e -> {
            if (parent == null) viewer.closeInventory();
            else parent.openLater();
        });
    }
}
