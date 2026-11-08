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
    /** LuckPerms is asked once per open menu (a user it can't load must not reload the menu forever). */
    private boolean ranksLoaded;
    /** null = all the time, weekly, monthly (buttons all-time / weekly / monthly) */
    private String period;

    public TopMenu(FlareReferrals plugin, Player viewer, Menu parent) {
        super(plugin, viewer, "top");
        this.parent = parent;
    }

    @Override protected Component defaultTitle() { return lang().title("gui.top.title", viewer); }

    @Override
    public java.util.Set<String> lists() {
        return java.util.Collections.singleton("entries");
    }

    @Override
    public Map<String, Material> palette() {
        Map<String, Material> m = new LinkedHashMap<>();
        m.put("entries", Material.PLAYER_HEAD);
        m.put("back", Material.ARROW);
        m.put("all-time", Material.NETHER_STAR);
        m.put("weekly", Material.CLOCK);
        m.put("monthly", Material.BOOK);
        return m;
    }

    private static boolean usesPlaceholders(String name, List<String> lore) {
        if (name != null && name.indexOf('%') >= 0) return true;
        for (String l : lore) if (l.indexOf('%') >= 0) return true;
        return false;
    }

    private void periodButton(String id, String which, Material icon) {
        boolean shown = which == null ? period == null : which.equals(period);
        String time = which == null ? "" : plugin.events().duration(plugin.periods().millisLeft(which));
        element(id, new ItemStack(icon), "gui.top." + id, null, shown, e -> {
            if (shown) return;
            period = which;
            ranksLoaded = false;
            refresh();
        }, "%time%", time);
    }

    @Override
    protected void buildElements() {
        int amount = Math.max(1, slots("entries").size());
        List<dev.tmdevelopment.flarereferrals.service.PeriodTops.Entry> top = new ArrayList<>();
        if (period == null) {
            for (PlayerData d : plugin.data().top(amount)) top.add(plugin.periods().entry(d));
        } else {
            List<dev.tmdevelopment.flarereferrals.service.PeriodTops.Entry> all = plugin.periods().current(period);
            top.addAll(all.subList(0, Math.min(amount, all.size())));
        }
        List<ItemStack> items = new ArrayList<>();
        dev.tmdevelopment.flarereferrals.layout.MenuLayout.Element tpl = template("entries");
        String nameTpl = tpl.name != null ? tpl.name : lang().raw("gui.top.entry.name");
        List<String> loreTpl = tpl.lore != null ? tpl.lore : lang().lines("gui.top.entry.lore");
        List<java.util.UUID> offline = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            dev.tmdevelopment.flarereferrals.service.PeriodTops.Entry d = top.get(i);
            String[] ph = {"%position%", String.valueOf(i + 1), "%player%", d.name, "%invites%", String.valueOf(d.invites)};
            // PlaceholderAPI placeholders (%luckperms_prefix%...) are of the player of this entry, not of the viewer
            org.bukkit.OfflinePlayer who = Bukkit.getOfflinePlayer(d.uuid);
            if (!who.isOnline()) offline.add(d.uuid);
            items.add(new ItemBuilder(Heads.of(who))
                    .name(Text.itemAbout(Text.replace(nameTpl, ph), who))
                    .lore(Text.itemsAbout(Text.replace(loreTpl, ph), who))
                    .amount(Math.min(64, i + 1))
                    .build());
        }
        list("entries", items, null, button(Material.BARRIER, period == null ? "gui.top.empty" : "gui.top.period-empty",
                period == null ? new String[0] : new String[]{"%period%", lang().raw("period." + period + ".word")}));
        // all the time / this week / this month: the one shown glows
        periodButton("all-time", null, Material.NETHER_STAR);
        periodButton("weekly", "weekly", Material.CLOCK);
        periodButton("monthly", "monthly", Material.BOOK);
        // offline players: drawn again once LuckPerms has loaded their rank
        if (!editMode && !ranksLoaded && dev.tmdevelopment.flarereferrals.hook.LuckPermsHook.installed() && usesPlaceholders(nameTpl, loreTpl)) {
            if (dev.tmdevelopment.flarereferrals.hook.LuckPermsHook.load(plugin, offline, () -> {
                if (isOpen()) refresh();
            })) ranksLoaded = true;
        }
        element("back", new ItemStack(Material.ARROW), parent == null ? "gui.common.close" : "gui.common.back", null, false, e -> {
            if (parent == null) viewer.closeInventory();
            else parent.openLater();
        });
    }
}
