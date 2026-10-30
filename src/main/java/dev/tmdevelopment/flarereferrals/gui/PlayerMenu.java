package dev.tmdevelopment.flarereferrals.gui;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.data.PlayerData;
import dev.tmdevelopment.flarereferrals.layout.LayoutMenu;
import dev.tmdevelopment.flarereferrals.reward.Milestone;
import dev.tmdevelopment.flarereferrals.util.Heads;
import dev.tmdevelopment.flarereferrals.util.ItemBuilder;
import dev.tmdevelopment.flarereferrals.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * /referrals menu - layout: menus/main.yml.
 * Also the menus made with the chest builder (menus/&lt;name&gt;.yml): they can use the same buttons with [button] &lt;id&gt;.
 */
public final class PlayerMenu extends LayoutMenu {

    public PlayerMenu(FlareReferrals plugin, Player viewer) {
        super(plugin, viewer, "main");
    }

    /** A menu made with /referrals layout add. */
    public PlayerMenu(FlareReferrals plugin, Player viewer, String layoutId, Menu parent) {
        super(plugin, viewer, layoutId);
        backTo(parent);
    }

    @Override
    protected Component defaultTitle() {
        if (!"main".equals(layoutId)) return Text.parse(dev.tmdevelopment.flarereferrals.layout.LayoutManager.prettyName(layoutId), viewer);
        return lang().title("gui.player.title", viewer);
    }

    @Override
    public java.util.Set<String> lists() {
        return java.util.Collections.singleton("milestones");
    }

    @Override
    public Map<String, Material> palette() {
        Map<String, Material> m = new LinkedHashMap<>();
        m.put("head", Material.PLAYER_HEAD);
        m.put("milestones", Material.NETHER_STAR);
        m.put("referral-rewards", Material.EMERALD);
        m.put("collect", Material.CHEST_MINECART);
        m.put("top", Material.KNOWLEDGE_BOOK);
        m.put("who-invited", Material.NAME_TAG);
        m.put("invited-list", Material.PLAYER_HEAD);
        m.put("settings", Material.COMPARATOR);
        m.put("close", Material.ARROW);
        if (!"main".equals(layoutId)) m.put("back", Material.SPECTRAL_ARROW);
        return m;
    }

    @Override
    protected void buildElements() {
        PlayerData d = plugin.data().get(viewer.getUniqueId());
        int invites = d == null ? 0 : d.invites();
        String referrer = lang().raw("info.nobody");
        if (d != null && d.referredBy() != null) {
            PlayerData r = plugin.data().get(d.referredBy());
            referrer = r == null ? "?" : r.name();
        }
        Milestone next = plugin.referrals().nextMilestone(invites);
        String nextStr = next == null ? lang().raw("info.no-next")
                : String.valueOf(plugin.referrals().nextTarget(next, invites));

        element("head", Heads.of(viewer), "gui.player.head", null, false, null,
                "%invites%", String.valueOf(invites), "%referrer%", referrer, "%next%", nextStr);

        boolean admin = dev.tmdevelopment.flarereferrals.util.Perms.has(viewer, "admin");
        List<ItemStack> items = new ArrayList<>();
        List<Consumer<InventoryClickEvent>> actions = new ArrayList<>();
        for (Milestone m : plugin.rewards().milestoneList()) {
            if (!m.enabled()) continue;
            String state;
            boolean waiting = d != null && d.claimable().contains(m.amount());
            // on a network: ready, but only on another server
            boolean ready = waiting && plugin.referrals().allowedHere(m);
            if (ready) state = "ready";
            else if (waiting) state = "elsewhere";
            else if (m.repeat()) state = "repeat";
            else if (invites >= m.amount()) {
                // reached: "claimed" only when he really has it (after admin set / resetclaimed / a new milestone
                // it comes with the next invite)
                state = d != null && d.hasUniq("milestone:" + m.amount()) ? "claimed" : "next-invite";
            }
            else state = "locked";
            int target = plugin.referrals().nextTarget(m, invites);
            List<Component> lore = new ArrayList<>(lang().lore("gui.player.milestone." + state, viewer,
                    "%amount%", String.valueOf(m.amount()),
                    "%invites%", String.valueOf(invites),
                    "%target%", String.valueOf(target),
                    "%remaining%", String.valueOf(Math.max(0, target - invites)),
                    "%servers%", plugin.referrals().serversText(m)));
            if (ready) lore.addAll(lang().lore("gui.player.milestone.claim-click", viewer));
            else if (plugin.canOpen(viewer, "preview")) lore.addAll(lang().lore("gui.player.milestone.click", viewer));
            if (admin) lore.addAll(lang().lore("gui.player.milestone.admin", viewer));
            items.add(new ItemBuilder(m.icon())
                    .name(Text.item(m.name(), viewer))
                    .lore(lore)
                    .glow(state.equals("ready") || state.equals("claimed"))
                    .hideFlags().build());
            actions.add(e -> {
                if (admin && e.isShiftClick()) new RewardSetMenu(plugin, viewer, m, this).openLater();
                else if (ready) {
                    plugin.referrals().claim(viewer, m.amount(), this::refresh);
                }
                else if (plugin.canOpen(viewer, "preview")) new PreviewMenu(plugin, viewer, m, this).openLater();
            });
        }
        list("milestones", items, actions, button(Material.BARRIER, "gui.player.no-milestones"));

        if (editMode || plugin.canOpen(viewer, "rewards")) {
            element("referral-rewards", new ItemStack(Material.EMERALD), "gui.player.referral-rewards", null, false,
                    e -> new ReferralRewardsMenu(plugin, viewer, this).openLater());
        }
        if (editMode || plugin.canOpen(viewer, "collect")) {
            int waiting = plugin.referrals().collectableHere(viewer);
            element("collect", new ItemStack(Material.CHEST_MINECART), "gui.player.collect", null, waiting > 0,
                    e -> new CollectMenu(plugin, viewer, this).openLater(), "%amount%", String.valueOf(waiting));
        }
        if (editMode || plugin.canOpen(viewer, "topmenu")) {
            element("top", new ItemStack(Material.KNOWLEDGE_BOOK), "gui.settings.top", null, false,
                    e -> new TopMenu(plugin, viewer, this).openLater());
        }
        whoInvited(d, referrer);
        if (editMode || plugin.canOpen(viewer, "invited")) {
            int count = plugin.data().invitedBy(viewer.getUniqueId()).size();
            element("invited-list", new ItemStack(Material.PLAYER_HEAD), "gui.player.invited-list", null, false,
                    e -> new InvitedMenu(plugin, viewer, viewer.getUniqueId(), this).openLater(), "%amount%", String.valueOf(count));
        }
        if (admin || editMode) {
            element("settings", new ItemStack(Material.COMPARATOR), "gui.player.settings", null, false,
                    e -> new SettingsMenu(plugin, viewer).openLater());
        }
        element("close", new ItemStack(Material.ARROW), "gui.common.close", null, false, e -> viewer.closeInventory());
        element("back", new ItemStack(Material.ARROW), backTo == null ? "gui.common.close" : "gui.common.back", null, false, e -> goBack());
    }

    /**
     * "Who invited you?": click and write the name in chat. After that it shows who it was; while the player
     * must still play a bit it says how long. Not in the menu by default: admins add it from the editor.
     */
    private void whoInvited(PlayerData d, String referrer) {
        if (editMode) {
            element("who-invited", new ItemStack(Material.NAME_TAG), "gui.player.who-invited.open", null, false, null);
            return;
        }
        if (d != null && d.referredBy() != null) {
            element("who-invited", new ItemStack(Material.NAME_TAG), "gui.player.who-invited.done", null, false, null,
                    "%referrer%", referrer);
            return;
        }
        int can = plugin.hints().canEnter(viewer);
        if (can == 2) {
            element("who-invited", new ItemStack(Material.NAME_TAG), "gui.player.who-invited.closed", null, false, null);
            return;
        }
        if (can == 1) {
            int min = plugin.getConfig().getInt("referral.min-playtime-minutes", 0);
            int left = Math.max(1, min - plugin.referrals().playtimeMinutes(viewer, d));
            element("who-invited", new ItemStack(Material.NAME_TAG), "gui.player.who-invited.wait", null, false, null,
                    "%minutes%", String.valueOf(left));
            return;
        }
        element("who-invited", new ItemStack(Material.NAME_TAG), "gui.player.who-invited.open", null, true, e ->
                plugin.chatInput().ask(viewer, "input.who-invited", text -> plugin.referrals().refer(viewer, text.trim()), () -> { },
                        "%days%", plugin.hints().daysLeft(viewer)));
    }
}
