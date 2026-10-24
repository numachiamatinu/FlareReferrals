package dev.tmdevelopment.flarereferrals.gui;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.currency.Currency;
import dev.tmdevelopment.flarereferrals.reward.RewardSet;
import dev.tmdevelopment.flarereferrals.util.ItemBuilder;
import dev.tmdevelopment.flarereferrals.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Money, MobCoins, Tokens, private coins, XP... for one reward set. */
public final class CurrencyMenu extends Menu {

    private final RewardSet set;
    private final Menu parent;

    public CurrencyMenu(FlareReferrals plugin, Player viewer, RewardSet set, Menu parent) {
        super(plugin, viewer);
        this.set = set;
        this.parent = parent;
    }

    @Override
    protected Component title() {
        return lang().title("gui.currencies.title", viewer, "%name%", RewardSetMenu.displayName(plugin, set));
    }

    @Override
    protected int rows() {
        int n = plugin.currencies().all().size();
        return Math.min(6, Math.max(3, (n + 8) / 9 + 2));
    }

    @Override
    protected void build() {
        List<Currency> list = new ArrayList<>(plugin.currencies().all());
        int slot = 9;
        for (Currency c : list) {
            if (slot >= (rows() - 1) * 9) break;
            double amount = set.currencies().getOrDefault(c.id(), 0d);
            boolean active = amount > 0;
            set(slot++, new ItemBuilder(c.icon())
                    .name(Text.item(c.name(), viewer))
                    .lore(lang().lore("gui.currencies.entry.lore", viewer,
                            "%amount%", Text.formatAmount(amount),
                            "%display%", plugin.currencies().display(c.id(), amount),
                            "%type%", c.type().name(),
                            "%id%", c.id()))
                    .addLore(plugin.currencies().problem(c) == null ? java.util.Collections.emptyList()
                            : lang().lore("gui.currencies.unavailable", viewer, "%reason%", plugin.currencies().problem(c)))
                    .glow(active).hideFlags().build(), e -> {
                if (e.isRightClick()) {
                    set.currencies().remove(c.id());
                    plugin.rewards().save();
                    refresh();
                    return;
                }
                plugin.chatInput().ask(viewer, "input.currency-amount", active ? Text.formatAmount(amount).replace(",", "") : "", text -> {
                    try {
                        double v = Double.parseDouble(text.replace(",", "."));
                        if (v <= 0) set.currencies().remove(c.id());
                        else set.currencies().put(c.id(), v);
                        plugin.rewards().save();
                    } catch (NumberFormatException ex) {
                        lang().send(viewer, "input.not-a-number", "%input%", text);
                    }
                    open();
                }, this::open, "%currency%", c.name());
            });
        }
        if (list.isEmpty()) {
            set(13, button(org.bukkit.Material.BARRIER, "gui.currencies.empty"));
        }
        int last = (rows() - 1) * 9;
        set(last, button(org.bukkit.Material.BOOK, "gui.currencies.help"));
        backButton(last + 4, parent);
        fillRow(0);
        fillRow(rows() - 1);
    }
}
