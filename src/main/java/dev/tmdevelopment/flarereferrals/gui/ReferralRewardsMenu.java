package dev.tmdevelopment.flarereferrals.gui;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.layout.LayoutMenu;
import dev.tmdevelopment.flarereferrals.reward.RewardSet;
import dev.tmdevelopment.flarereferrals.util.ItemBuilder;
import dev.tmdevelopment.flarereferrals.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What the inviter and the invited player get on every referral, separate from milestones.
 * Admins: left-click edit, right-click on/off. Players: click to see the rewards.
 */
public final class ReferralRewardsMenu extends LayoutMenu {

    private final Menu parent;

    public ReferralRewardsMenu(FlareReferrals plugin, Player viewer, Menu parent) {
        super(plugin, viewer, "rewards");
        this.parent = parent;
    }

    @Override protected Component defaultTitle() { return lang().title("gui.referral-rewards.title", viewer); }

    @Override
    public Map<String, Material> palette() {
        Map<String, Material> m = new java.util.LinkedHashMap<>();
        m.put("info", Material.BOOK);
        m.put("inviter", Material.PLAYER_HEAD);
        m.put("invited", Material.EMERALD);
        m.put("milestones", Material.NETHER_STAR);
        m.put("back", Material.ARROW);
        return m;
    }

    private boolean admin() {
        return dev.tmdevelopment.flarereferrals.util.Perms.has(viewer, "admin");
    }

    /** Short summary of a reward set: items, currencies, commands. */
    private List<Component> summary(RewardSet set) {
        List<Component> out = new ArrayList<>();
        String fmt = lang().raw("gui.rewardset.preview-line");
        int shown = 0;
        for (ItemStack it : set.items()) {
            if (shown++ >= 4) break;
            Component line = Text.item(Text.replace(fmt, "%text%", it.getAmount() + "x "), viewer);
            org.bukkit.inventory.meta.ItemMeta meta = it.hasItemMeta() ? it.getItemMeta() : null;
            if (meta != null && meta.hasDisplayName()) line = line.append(Text.fromLegacy(meta.getDisplayName()));
            else line = line.append(Text.item("&f" + it.getType().name().toLowerCase().replace('_', ' '), viewer));
            out.add(line);
        }
        if (set.items().size() > 4) {
            out.add(Text.item(Text.replace(lang().raw("gui.rewardset.preview-more"),
                    "%amount%", String.valueOf(set.items().size() - 4)), viewer));
        }
        for (Map.Entry<String, Double> en : set.currencies().entrySet()) {
            out.add(Text.item(Text.replace(fmt, "%text%", plugin.currencies().display(en.getKey(), en.getValue())), viewer));
        }
        if (admin() && !set.commands().isEmpty()) {
            out.add(Text.item(Text.replace(fmt, "%text%", "&d" + set.commands().size() + " command(s)"), viewer));
        }
        if (out.isEmpty()) out.add(Text.item(lang().raw("gui.rewardset.preview-empty"), viewer));
        return out;
    }

    private List<Component> cardExtra(RewardSet set) {
        List<Component> extra = new ArrayList<>(summary(set));
        if (admin()) extra.addAll(lang().lore("gui.referral-rewards.click-admin", viewer));
        else if (plugin.canOpen(viewer, "preview")) extra.addAll(lang().lore("gui.referral-rewards.click-player", viewer));
        return extra;
    }

    private void handle(RewardSet set, org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!admin()) {
            if (plugin.canOpen(viewer, "preview")) new PreviewMenu(plugin, viewer, set, this).openLater();
            return;
        }
        if (e.isRightClick()) {
            set.enabled(!set.enabled());
            plugin.rewards().save();
            refresh();
        } else {
            new RewardSetMenu(plugin, viewer, set, this).openLater();
        }
    }

    @Override
    protected void buildElements() {
        RewardSet inviter = plugin.rewards().inviter();
        RewardSet invited = plugin.rewards().invited();

        element("info", new ItemStack(Material.BOOK), "gui.referral-rewards.info", null, false, null);
        element("inviter", new ItemStack(Material.PLAYER_HEAD), "gui.referral-rewards.inviter", cardExtra(inviter),
                inviter.enabled(), e -> handle(inviter, e), "%status%", status(inviter.enabled()));
        element("invited", new ItemStack(Material.EMERALD), "gui.referral-rewards.invited", cardExtra(invited),
                invited.enabled(), e -> handle(invited, e), "%status%", status(invited.enabled()));
        if (editMode || admin() || plugin.canOpen(viewer, "menu")) {
            element("milestones", new ItemStack(Material.NETHER_STAR), "gui.referral-rewards.milestones", null, false, e -> {
                if (admin()) new MilestoneListMenu(plugin, viewer, this).openLater();
                else new PlayerMenu(plugin, viewer).openLater();
            });
        }
        element("back", new ItemStack(Material.ARROW), parent == null ? "gui.common.close" : "gui.common.back", null, false, e -> {
            if (parent == null) viewer.closeInventory();
            else parent.openLater();
        });
    }
}
