package dev.tmdevelopment.flarereferrals.gui;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.data.PlayerData;
import dev.tmdevelopment.flarereferrals.data.RewardEntry;
import dev.tmdevelopment.flarereferrals.layout.LayoutMenu;
import dev.tmdevelopment.flarereferrals.reward.Milestone;
import dev.tmdevelopment.flarereferrals.reward.RewardSet;
import dev.tmdevelopment.flarereferrals.util.ItemBuilder;
import dev.tmdevelopment.flarereferrals.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** /referrals collect - rewards waiting to be collected. Layout: menus/collect.yml */
public final class CollectMenu extends LayoutMenu {

    private static final int PREVIEW_LINES = 4;

    private final Menu parent;
    private int page;

    public CollectMenu(FlareReferrals plugin, Player viewer, Menu parent) {
        super(plugin, viewer, "collect");
        this.parent = parent;
    }

    @Override protected Component defaultTitle() { return lang().title("gui.collect.title", viewer); }

    @Override
    public java.util.Set<String> lists() {
        return java.util.Collections.singleton("rewards");
    }

    @Override
    public Map<String, Material> palette() {
        Map<String, Material> m = new LinkedHashMap<>();
        m.put("info", Material.CHEST_MINECART);
        m.put("rewards", Material.CHEST);
        m.put("collect-all", Material.HOPPER);
        m.put("previous-page", Material.SPECTRAL_ARROW);
        m.put("next-page", Material.SPECTRAL_ARROW);
        m.put("back", Material.ARROW);
        return m;
    }

    @Override
    protected void buildElements() {
        PlayerData d = plugin.data().get(viewer.getUniqueId());
        List<RewardEntry> waiting = d == null ? new ArrayList<RewardEntry>() : d.claimableEntries();
        int here = plugin.referrals().collectableHere(viewer);

        element("info", new ItemStack(Material.CHEST_MINECART), "gui.collect.info", null, here > 0, null,
                "%amount%", String.valueOf(here), "%total%", String.valueOf(waiting.size()));

        dev.tmdevelopment.flarereferrals.layout.MenuLayout.Element tpl = template("rewards");
        String nameTpl = tpl.name != null ? tpl.name : lang().raw("gui.collect.rewards.name");
        List<String> loreTpl = tpl.lore != null ? tpl.lore : lang().lines("gui.collect.rewards.lore");
        List<String> elsewhereTpl = lang().lines("gui.collect.elsewhere");
        java.text.SimpleDateFormat dates;
        try {
            dates = new java.text.SimpleDateFormat(lang().raw("gui.collect.date-format"));
        } catch (IllegalArgumentException ex) {
            dates = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm");
        }
        final java.text.SimpleDateFormat df = dates;

        List<ItemStack> items = new ArrayList<>();
        List<Consumer<InventoryClickEvent>> actions = new ArrayList<>();
        for (RewardEntry e : waiting) {
            RewardSet set = plugin.rewards().resolve(e.key());
            if (set == null || !set.enabled()) continue;
            boolean ok = plugin.referrals().allowedHere(set);
            String[] parts = e.reward().split("\\|", -1);
            String[] ph = {
                    "%reward%", plugin.referrals().rewardName(e),
                    "%inviter%", parts.length > 1 ? parts[1] : "",
                    "%invited%", parts.length > 2 ? parts[2] : "",
                    "%servers%", plugin.referrals().serversText(set),
                    "%date%", df.format(new java.util.Date(e.created()))
            };
            List<Component> lore = new ArrayList<>();
            for (String line : ok ? loreTpl : elsewhereTpl) {
                // unknown date (rewards converted by older versions): no "Since" line
                if (e.created() <= 0 && (line.contains("{date}") || line.contains("%date%"))) continue;
                if (line.trim().equals("{preview}") || line.trim().equals("%preview%")) lore.addAll(preview(set));
                else lore.add(Text.item(Text.replace(line, ph), viewer));
            }
            items.add(new ItemBuilder(icon(e, set, ok))
                    .name(Text.item(Text.replace(nameTpl, ph), viewer))
                    .lore(lore).glow(ok).hideFlags().build());
            actions.add(click -> {
                if (click.isRightClick()) {
                    new PreviewMenu(plugin, viewer, set, this).openLater();
                    return;
                }
                if (!ok) {
                    lang().send(viewer, "collect.other-server", "%servers%", plugin.referrals().serversText(set));
                    plugin.referrals().sound(viewer, "error");
                    return;
                }
                plugin.referrals().collectEntry(viewer, e, this::refresh);
            });
        }
        // pages when there are more rewards than slots
        int per = Math.max(1, slots("rewards").size());
        int pages = Math.max(1, (items.size() + per - 1) / per);
        if (page >= pages) page = pages - 1;
        int from = page * per, to = Math.min(items.size(), from + per);
        list("rewards", new ArrayList<>(items.subList(from, to)), new ArrayList<>(actions.subList(from, to)),
                button(Material.BARRIER, "gui.collect.empty"));
        String[] pagePh = {"%page%", String.valueOf(page + 1), "%pages%", String.valueOf(pages)};
        if (editMode || page > 0) {
            element("previous-page", new ItemStack(Material.SPECTRAL_ARROW), "gui.collect.previous-page", null, false, e -> {
                page--;
                refresh();
            }, pagePh);
        }
        if (editMode || page < pages - 1) {
            element("next-page", new ItemStack(Material.SPECTRAL_ARROW), "gui.collect.next-page", null, false, e -> {
                page++;
                refresh();
            }, pagePh);
        }

        if (editMode || here > 0) {
            element("collect-all", new ItemStack(Material.HOPPER), "gui.collect.collect-all", null, false, e -> {
                if (plugin.referrals().claimAll(viewer, this::refresh) == 0) lang().send(viewer, "collect.nothing");
            }, "%amount%", String.valueOf(here));
        }
        element("back", new ItemStack(Material.ARROW), parent == null ? "gui.common.close" : "gui.common.back", null, false, e -> {
            if (parent == null) viewer.closeInventory();
            else parent.openLater();
        });
    }

    private ItemStack icon(RewardEntry e, RewardSet set, boolean ok) {
        if (!ok) return new ItemStack(Material.GRAY_DYE);
        if (set instanceof Milestone) return ((Milestone) set).icon();
        if (e.key().equals("referral:inviter")) return new ItemStack(Material.PLAYER_HEAD);
        return new ItemStack(Material.EMERALD);
    }

    /** Short list of what the reward gives: items, currencies, extra rewards. */
    private List<Component> preview(RewardSet set) {
        List<Component> out = new ArrayList<>();
        int shown = 0;
        for (ItemStack it : set.items()) {
            if (it == null || it.getType().isAir()) continue;
            if (shown++ >= PREVIEW_LINES) break;
            ItemMeta meta = it.hasItemMeta() ? it.getItemMeta() : null;
            String name = meta != null && meta.hasDisplayName() ? meta.getDisplayName()
                    : it.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
            out.add(Text.item(Text.replace(lang().raw("gui.collect.preview-item"), "%amount%", String.valueOf(it.getAmount()),
                    "%item%", dev.tmdevelopment.flarereferrals.util.Platform.toEditable(name)), viewer));
        }
        int items = 0;
        for (ItemStack it : set.items()) if (it != null && !it.getType().isAir()) items++;
        if (items > PREVIEW_LINES) {
            out.add(Text.item(Text.replace(lang().raw("gui.collect.preview-more"), "%amount%", String.valueOf(items - PREVIEW_LINES)), viewer));
        }
        for (Map.Entry<String, Double> c : set.currencies().entrySet()) {
            if (plugin.currencies().get(c.getKey()) == null) continue; // turned off: not given, not shown
            out.add(Text.item(Text.replace(lang().raw("gui.collect.preview-currency"), "%currency%",
                    plugin.currencies().display(c.getKey(), c.getValue())), viewer));
        }
        if (!set.commands().isEmpty()) out.add(Text.item(lang().raw("gui.collect.preview-extra"), viewer));
        if (out.isEmpty()) out.add(Text.item(lang().raw("gui.collect.preview-empty"), viewer));
        return out;
    }
}
