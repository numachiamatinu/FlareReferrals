package dev.tmdevelopment.flarereferrals.gui;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.reward.Display;
import dev.tmdevelopment.flarereferrals.reward.RewardSet;
import dev.tmdevelopment.flarereferrals.service.ScreenEffects;
import dev.tmdevelopment.flarereferrals.util.ItemBuilder;
import dev.tmdevelopment.flarereferrals.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Title / subtitle, action bar and boss bar shown to the player who receives the reward. */
public final class DisplayMenu extends Menu {

    private final RewardSet set;
    private final Menu parent;

    public DisplayMenu(FlareReferrals plugin, Player viewer, RewardSet set, Menu parent) {
        super(plugin, viewer);
        this.set = set;
        this.parent = parent;
    }

    private Display d() {
        return set.display();
    }

    @Override
    protected Component title() {
        return lang().title("gui.display.title", viewer, "%name%", RewardSetMenu.displayName(plugin, set));
    }

    @Override protected int rows() { return 5; }

    private void save() {
        plugin.rewards().save();
    }

    /** Placeholders filled with the admin's own name, used for previews and for "test on me". */
    private String[] sample() {
        return new String[]{"%player%", viewer.getName(), "%uuid%", viewer.getUniqueId().toString(),
                "%invites%", "3", "%milestone%", "3", "%inviter%", viewer.getName(), "%invited%", viewer.getName()};
    }

    private List<Component> current(String raw) {
        List<Component> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            out.add(Text.item(lang().raw("gui.display.off-text"), viewer));
            return out;
        }
        String s = raw.replace("%prefix%", lang().prefix()).replace("{prefix}", lang().prefix());
        out.add(Text.item(Text.replace(s, sample()), viewer));
        return out;
    }

    private ItemStack textButton(Material mat, String path, String raw) {
        List<Component> lore = new ArrayList<>(lang().lore(path + ".lore", viewer));
        lore.addAll(current(raw));
        lore.addAll(lang().lore("gui.display.text-actions", viewer));
        return new ItemBuilder(mat).name(lang().item(path + ".name", viewer)).lore(lore)
                .glow(raw != null && !raw.isEmpty()).hideFlags().build();
    }

    /** Left-click: type a new text (old one suggested). Right-click: turn off. */
    private void textAction(InventoryClickEvent e, String prompt, Supplier<String> getter, Consumer<String> setter) {
        if (e.isRightClick()) {
            setter.accept("");
            save();
            refresh();
            return;
        }
        String old = getter.get();
        plugin.chatInput().ask(viewer, prompt, old.isEmpty() ? "{prefix}" : old, text -> {
            setter.accept(text);
            save();
            open();
        }, this::open);
    }

    private static String sec(int ticks) {
        double s = ticks / 20.0;
        return s == Math.rint(s) ? String.valueOf((long) s) : String.format(Locale.US, "%.1f", s);
    }

    @Override
    protected void build() {
        Display d = d();

        // ---- title
        set(10, textButton(Material.NAME_TAG, "gui.display.title-text", d.title()),
                e -> textAction(e, "input.display-title", d::title, d::title));
        set(11, textButton(Material.PAPER, "gui.display.subtitle", d.subtitle()),
                e -> textAction(e, "input.display-subtitle", d::subtitle, d::subtitle));
        set(12, button(Material.CLOCK, "gui.display.times",
                "%in%", sec(d.fadeIn()), "%stay%", sec(d.stay()), "%out%", sec(d.fadeOut())), e ->
                plugin.chatInput().ask(viewer, "input.display-times",
                        sec(d.fadeIn()) + " " + sec(d.stay()) + " " + sec(d.fadeOut()), text -> {
                            String[] parts = text.trim().replace(",", ".").split("\\s+");
                            try {
                                if (parts.length != 3) throw new NumberFormatException();
                                d.times((int) Math.round(Double.parseDouble(parts[0]) * 20),
                                        (int) Math.round(Double.parseDouble(parts[1]) * 20),
                                        (int) Math.round(Double.parseDouble(parts[2]) * 20));
                                save();
                            } catch (NumberFormatException ex) {
                                lang().send(viewer, "input.not-a-number", "%input%", text);
                            }
                            open();
                        }, this::open));

        // ---- action bar
        set(14, textButton(Material.OAK_SIGN, "gui.display.actionbar", d.actionbar()),
                e -> textAction(e, "input.display-actionbar", d::actionbar, d::actionbar));

        // ---- boss bar
        set(28, textButton(Material.DRAGON_HEAD, "gui.display.bossbar", d.bossbar()),
                e -> textAction(e, "input.display-bossbar", d::bossbar, d::bossbar));
        Material dye = Material.matchMaterial(("GREEN".equals(d.bossbarColor()) ? "LIME_DYE" : d.bossbarColor() + "_DYE"));
        set(29, button(dye == null ? Material.WHITE_DYE : dye, "gui.display.bossbar-color",
                "%value%", d.bossbarColor().toLowerCase(Locale.ROOT)), e -> {
            d.bossbarColor(Display.next(d.bossbarColor(), Display.BOSSBAR_COLORS, e.isRightClick()));
            save();
            refresh();
        });
        set(30, button(Material.ITEM_FRAME, "gui.display.bossbar-style",
                "%value%", d.bossbarStyle().toLowerCase(Locale.ROOT).replace('_', ' ')), e -> {
            d.bossbarStyle(Display.next(d.bossbarStyle(), Display.BOSSBAR_STYLES, e.isRightClick()));
            save();
            refresh();
        });
        set(31, button(Material.CLOCK, "gui.display.bossbar-seconds", "%value%", String.valueOf(d.bossbarSeconds())), e -> {
            d.bossbarSeconds(d.bossbarSeconds() + (e.isRightClick() ? -1 : 1) * (e.isShiftClick() ? 5 : 1));
            save();
            refresh();
        });

        // ---- test
        set(16, button(Material.ENDER_EYE, "gui.display.test"), e -> {
            viewer.closeInventory();
            ScreenEffects.show(plugin, viewer, d, sample());
            if (!d.any()) lang().send(viewer, "admin.display-empty");
        });
        set(34, button(Material.BOOK, "gui.display.help"));

        backButton(40, parent);
        fillBorder();
    }
}
