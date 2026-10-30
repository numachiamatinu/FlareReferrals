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
import java.util.UUID;

/**
 * /referrals invited - the players someone invited, with the date and whether the invite counts already.
 * Layout: menus/invited.yml (the "entries" slots decide how many players fit on a page).
 */
public final class InvitedMenu extends LayoutMenu {

    private final UUID owner;
    private int page;

    public InvitedMenu(FlareReferrals plugin, Player viewer, UUID owner, Menu parent) {
        super(plugin, viewer, "invited");
        this.owner = owner == null ? viewer.getUniqueId() : owner;
        backTo(parent);
    }

    private String ownerName() {
        PlayerData me = plugin.data().get(owner);
        return me != null ? me.name() : owner.equals(viewer.getUniqueId()) ? viewer.getName() : "?";
    }

    /** {player} is the player whose list it is (also when staff look at someone else's list). */
    @Override
    protected String[] titlePlaceholders() {
        return new String[]{"%player%", ownerName()};
    }

    @Override
    protected org.bukkit.OfflinePlayer headOwner() {
        return Bukkit.getOfflinePlayer(owner);
    }

    @Override
    protected Component defaultTitle() {
        return lang().title("gui.invited.title", viewer);
    }

    @Override
    public java.util.Set<String> lists() {
        return java.util.Collections.singleton("entries");
    }

    @Override
    public Map<String, Material> palette() {
        Map<String, Material> m = new LinkedHashMap<>();
        m.put("head", Material.PLAYER_HEAD);
        m.put("entries", Material.PLAYER_HEAD);
        m.put("previous", Material.ARROW);
        m.put("next", Material.ARROW);
        m.put("back", Material.ARROW);
        return m;
    }

    /** The date format of the texts, or a working one when it is written wrong. */
    public static java.text.SimpleDateFormat dateFormat(String pattern) {
        try {
            return new java.text.SimpleDateFormat(pattern);
        } catch (RuntimeException e) {
            return new java.text.SimpleDateFormat("dd/MM/yyyy");
        }
    }

    @Override
    protected void buildElements() {
        String ownerName = ownerName();
        List<PlayerData> all = plugin.data().invitedBy(owner);
        int verified = 0;
        for (PlayerData d : all) if (!d.pendingInvite()) verified++;
        int perPage = Math.max(1, slots("entries").size());
        int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        if (page >= pages) page = pages - 1;
        String[] sum = {"%player%", ownerName, "%amount%", String.valueOf(all.size()),
                "%verified%", String.valueOf(verified), "%pending%", String.valueOf(all.size() - verified),
                "%page%", String.valueOf(page + 1), "%pages%", String.valueOf(pages)};

        org.bukkit.OfflinePlayer ownerPlayer = Bukkit.getOfflinePlayer(owner);
        element("head", Heads.of(ownerPlayer), "gui.invited.head", null, false, null, sum);

        java.text.SimpleDateFormat df = dateFormat(lang().raw("gui.invited.date-format"));
        dev.tmdevelopment.flarereferrals.layout.MenuLayout.Element tpl = template("entries");
        String nameTpl = tpl.name != null ? tpl.name : lang().raw("gui.invited.entry.name");
        List<String> loreTpl = tpl.lore != null ? tpl.lore : lang().lines("gui.invited.entry.lore");
        List<ItemStack> items = new ArrayList<>();
        for (int i = page * perPage; i < all.size() && i < (page + 1) * perPage; i++) {
            PlayerData d = all.get(i);
            String[] ph = {"%number%", String.valueOf(i + 1), "%player%", d.name(),
                    "%date%", d.referredAt() > 0 ? df.format(new java.util.Date(d.referredAt())) : "-",
                    "%status%", lang().raw(d.pendingInvite() ? "gui.invited.status.pending" : "gui.invited.status.verified"),
                    "%online%", lang().raw(Bukkit.getPlayer(d.uuid()) != null ? "gui.invited.online" : "gui.invited.offline")};
            org.bukkit.OfflinePlayer who = Bukkit.getOfflinePlayer(d.uuid());
            items.add(new ItemBuilder(Heads.of(who))
                    .name(Text.itemAbout(Text.replace(nameTpl, ph), who))
                    .lore(Text.itemsAbout(Text.replace(loreTpl, ph), who))
                    .glow(!d.pendingInvite() && Bukkit.getPlayer(d.uuid()) != null)
                    .build());
        }
        list("entries", items, null, button(Material.BARRIER, "gui.invited.empty", sum));

        if (editMode || page > 0) {
            element("previous", new ItemStack(Material.ARROW), "gui.invited.previous", null, false, e -> {
                page = Math.max(0, page - 1);
                refresh();
            }, sum);
        }
        if (editMode || page < pages - 1) {
            element("next", new ItemStack(Material.ARROW), "gui.invited.next", null, false, e -> {
                page++;
                refresh();
            }, sum);
        }
        element("back", new ItemStack(Material.ARROW), backTo == null ? "gui.common.close" : "gui.common.back", null, false, e -> goBack());
    }
}
