package dev.tmdevelopment.flarereferrals.gui;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.currency.Currency;
import dev.tmdevelopment.flarereferrals.layout.LayoutMenu;
import dev.tmdevelopment.flarereferrals.reward.Milestone;
import dev.tmdevelopment.flarereferrals.reward.RewardSet;
import dev.tmdevelopment.flarereferrals.util.ItemBuilder;
import dev.tmdevelopment.flarereferrals.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only view of what a reward set gives - layout: menus/preview.yml */
public final class PreviewMenu extends LayoutMenu {

    private final RewardSet set;
    private final Menu parent;

    public PreviewMenu(FlareReferrals plugin, Player viewer, RewardSet set, Menu parent) {
        super(plugin, viewer, "preview");
        this.set = set;
        this.parent = parent;
    }

    @Override
    protected Component defaultTitle() {
        return lang().title("gui.preview.title", viewer, "%name%", RewardSetMenu.displayName(plugin, set));
    }

    @Override
    protected String[] titlePlaceholders() {
        return new String[]{"%player%", viewer.getName(), "%name%", RewardSetMenu.displayName(plugin, set)};
    }

    @Override
    public Map<String, Material> palette() {
        Map<String, Material> m = new LinkedHashMap<>();
        m.put("items", Material.CHEST);
        m.put("edit", Material.ANVIL);
        m.put("back", Material.ARROW);
        return m;
    }

    @Override
    protected void buildElements() {
        List<ItemStack> items = new ArrayList<>();
        if (!editMode) for (ItemStack it : set.items()) items.add(it.clone()); // editor shows only slot markers
        for (Map.Entry<String, Double> en : editMode ? java.util.Collections.<String, Double>emptyMap().entrySet() : set.currencies().entrySet()) {
            Currency c = plugin.currencies().get(en.getKey());
            Material icon = c == null ? Material.GOLD_NUGGET : c.icon();
            items.add(new ItemBuilder(icon)
                    .name(Text.item(plugin.currencies().display(en.getKey(), en.getValue()), viewer))
                    .lore(lang().lore("gui.preview.currency-lore", viewer))
                    .hideFlags().build());
        }
        list("items", items, null, button(Material.BARRIER, "gui.preview.empty"));

        if (editMode || (set instanceof Milestone && dev.tmdevelopment.flarereferrals.util.Perms.has(viewer, "admin"))) {
            element("edit", new ItemStack(Material.ANVIL), "gui.preview.edit", null, false, e -> {
                if (set instanceof Milestone) { Milestone m = (Milestone) set; new RewardSetMenu(plugin, viewer, m, this).openLater(); }
            });
        }
        element("back", new ItemStack(Material.ARROW), parent == null ? "gui.common.close" : "gui.common.back", null, false, e -> {
            if (parent == null) viewer.closeInventory();
            else parent.openLater();
        });
    }
}
