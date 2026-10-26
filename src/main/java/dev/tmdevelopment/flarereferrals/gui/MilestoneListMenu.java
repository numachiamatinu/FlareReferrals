package dev.tmdevelopment.flarereferrals.gui;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.reward.Milestone;
import dev.tmdevelopment.flarereferrals.util.ItemBuilder;
import dev.tmdevelopment.flarereferrals.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Admin list of all milestones (3 / 5 / 10 invites...). */
public final class MilestoneListMenu extends Menu {

    private final Menu parent;

    public MilestoneListMenu(FlareReferrals plugin, Player viewer, Menu parent) {
        super(plugin, viewer);
        this.parent = parent;
    }

    @Override protected Component title() { return lang().title("gui.milestones.title", viewer); }
    @Override protected int rows() { return 6; }

    @Override
    protected void build() {
        int slot = 0;
        for (Milestone m : plugin.rewards().milestoneList()) {
            if (slot >= 45) break;
            set(slot++, icon(m), e -> {
                if (e.isShiftClick() && e.isRightClick()) {
                    new ConfirmMenu(plugin, viewer, MilestoneListMenu.deleteQuestion(plugin, m.amount()), () -> {
                        plugin.rewards().delete(m.amount());
                        lang().send(viewer, "admin.milestone-deleted", "%amount%", String.valueOf(m.amount()));
                        openLater();
                    }, this).openLater();
                } else if (e.isRightClick()) {
                    m.enabled(!m.enabled());
                    plugin.rewards().save();
                    if (m.enabled()) plugin.referrals().rewardTurnedOn(m); // what waited while it was off
                    refresh();
                } else {
                    new RewardSetMenu(plugin, viewer, m, this).openLater();
                }
            });
        }

        set(45, button(Material.BOOK, "gui.milestones.help"));
        set(48, button(Material.LIME_DYE, "gui.milestones.add"), e ->
                plugin.chatInput().ask(viewer, "input.milestone-new", String.valueOf(suggestNext()), text -> {
                    try {
                        int v = Integer.parseInt(text);
                        if (v <= 0) throw new NumberFormatException();
                        if (plugin.rewards().milestone(v) != null) {
                            lang().send(viewer, "admin.milestone-exists", "%amount%", text);
                            open();
                            return;
                        }
                        Milestone m = plugin.rewards().create(v);
                        lang().send(viewer, "admin.milestone-created", "%amount%", text);
                        new RewardSetMenu(plugin, viewer, m, this).open();
                    } catch (NumberFormatException ex) {
                        lang().send(viewer, "input.not-a-number", "%input%", text);
                        open();
                    }
                }, this::open));
        backButton(49, parent);
        fillRow(5);
    }

    /** Suggests the next free amount: 3, 5, 10, 15, 20, 25... */
    private int suggestNext() {
        int[] base = {3, 5, 10};
        for (int b : base) if (plugin.rewards().milestone(b) == null) return b;
        int v = 15;
        while (plugin.rewards().milestone(v) != null) v += 5;
        return v;
    }

    private org.bukkit.inventory.ItemStack icon(Milestone m) {
        List<Component> lore = new ArrayList<>();
        lore.addAll(lang().lore("gui.milestones.entry.lore", viewer,
                "%amount%", String.valueOf(m.amount()),
                "%status%", status(m.enabled()),
                "%repeat%", status(m.repeat()),
                "%items%", String.valueOf(m.items().size()),
                "%commands%", String.valueOf(m.commands().size())));
        for (Map.Entry<String, Double> en : m.currencies().entrySet()) {
            lore.add(Text.item(Text.replace(lang().raw("gui.milestones.entry.currency-line"), "%display%", plugin.currencies().display(en.getKey(), en.getValue())), viewer));
        }
        lore.addAll(lang().lore("gui.milestones.entry.actions", viewer));
        return new ItemBuilder(m.icon())
                .name(Text.item(m.name(), viewer))
                .lore(lore)
                .amount(Math.min(64, m.amount()))
                .glow(m.enabled())
                .hideFlags()
                .build();
    }

    /** "Delete the 5 invites milestone?", with how many rewards of it players have not received yet. */
    static String deleteQuestion(FlareReferrals plugin, int amount) {
        int waiting = plugin.data().waiting(amount);
        String key = waiting > 0 ? "gui.confirm.delete-milestone-waiting" : "gui.confirm.delete-milestone";
        return Text.replace(plugin.lang().raw(key), "%amount%", String.valueOf(amount), "%waiting%", String.valueOf(waiting));
    }
}
