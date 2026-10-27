package dev.tmdevelopment.flarereferrals.layout;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.gui.ConfirmMenu;
import dev.tmdevelopment.flarereferrals.gui.Menu;
import dev.tmdevelopment.flarereferrals.util.ItemBuilder;
import dev.tmdevelopment.flarereferrals.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/** /referrals settings > Menu layouts: pick a menu to customize. */
public final class LayoutListMenu extends Menu {

    private static final Map<String, Material> ICONS = dev.tmdevelopment.flarereferrals.util.J8.map(
            "main", Material.CHEST, "rewards", Material.EMERALD, "top", Material.KNOWLEDGE_BOOK, "preview", Material.ENDER_EYE, "collect", Material.CHEST_MINECART);

    private final Menu parent;

    public LayoutListMenu(FlareReferrals plugin, Player viewer, Menu parent) {
        super(plugin, viewer);
        this.parent = parent;
    }

    @Override protected Component title() { return lang().title("gui.layout.list-title", viewer); }
    @Override protected int rows() { return plugin.layouts().customIds().isEmpty() ? 4 : 6; }

    @Override
    protected void build() {
        int[] slots = {11, 12, 13, 14, 15};
        int i = 0;
        for (String id : LayoutManager.IDS) {
            MenuLayout layout = plugin.layouts().get(id);
            String title = layout.title() == null || dev.tmdevelopment.flarereferrals.util.J8.blank(layout.title()) ? lang().raw("gui.layout.default-title") : layout.title();
            set(slots[i++], new ItemBuilder(ICONS.getOrDefault(id, Material.CHEST))
                    .name(lang().item("gui.layout.menus." + id + ".name", viewer))
                    .lore(lang().lore("gui.layout.menu-entry", viewer,
                            "%description%", lang().raw("gui.layout.menus." + id + ".description"),
                            "%rows%", String.valueOf(layout.rows()),
                            "%slots%", String.valueOf(layout.rows() * 9),
                            "%title%", title,
                            "%file%", "menus/" + id + ".yml",
                            "%commands%", layout.openCommands().isEmpty() ? lang().raw("gui.common.none")
                                    : "/" + String.join(", /", layout.openCommands())))
                    .hideFlags().build(), e -> {
                if (e.isShiftClick() && e.isRightClick()) {
                    new ConfirmMenu(plugin, viewer, Text.replace(lang().raw("gui.layout.confirm-reset"), "%menu%", id), () -> {
                        plugin.layouts().reset(id);
                        plugin.menuCommands().refresh();
                        lang().send(viewer, "admin.layout-reset", "%menu%", id);
                        openLater();
                    }, this).openLater();
                } else if (e.isShiftClick()) {
                    plugin.chatInput().ask(viewer, "input.layout-title", layout.title(), text -> {
                        layout.title(text.equalsIgnoreCase("default") ? "" : text);
                        plugin.layouts().save(id);
                        open();
                    }, this::open);
                } else if (e.isRightClick()) {
                    layout.rows(layout.rows() >= 6 ? 1 : layout.rows() + 1); // 1 -> 6 rows, then back to 1
                    plugin.layouts().save(id);
                    refresh();
                } else {
                    LayoutMenu m = LayoutMenus.create(plugin, id, viewer, null);
                    if (m != null) m.editMode().openLater();
                }
            });
        }
        // on / off switch under every menu
        int k = 0;
        for (String id : LayoutManager.IDS) {
            MenuLayout layout = plugin.layouts().get(id);
            boolean on = layout.enabled();
            set(slots[k++] + 9, button(on ? Material.LIME_DYE : Material.GRAY_DYE, "gui.layout.toggle",
                    "%status%", status(on), "%menu%", id), e -> {
                layout.enabled(!layout.enabled());
                plugin.layouts().save(id);
                plugin.menuCommands().refresh();
                lang().send(viewer, layout.enabled() ? "admin.menu-enabled" : "admin.menu-disabled", "%menu%", id);
                refresh();
            });
        }
        // menus made with the chest builder (/referrals layout add <name>)
        List<String> custom = plugin.layouts().customIds();
        int c = 28;
        for (String id : custom) {
            if (c > 34) break;
            MenuLayout layout = plugin.layouts().get(id);
            String title = layout.title() == null || dev.tmdevelopment.flarereferrals.util.J8.blank(layout.title()) ? lang().raw("gui.layout.default-title") : layout.title();
            set(c, new ItemBuilder(Material.BARREL)
                    .name(Text.item("&f" + id, viewer))
                    .lore(lang().lore("gui.layout.custom-entry", viewer,
                            "%rows%", String.valueOf(layout.rows()),
                            "%slots%", String.valueOf(layout.rows() * 9),
                            "%title%", title,
                            "%file%", "menus/" + id + ".yml",
                            "%commands%", layout.openCommands().isEmpty() ? lang().raw("gui.common.none")
                                    : "/" + String.join(", /", layout.openCommands())))
                    .hideFlags().build(), e -> {
                if (e.isShiftClick() && e.isRightClick()) {
                    new ConfirmMenu(plugin, viewer, Text.replace(lang().raw("gui.layout.confirm-remove"), "%menu%", id), () -> {
                        String copy = plugin.layouts().removeCustom(id);
                        plugin.menuCommands().refresh();
                        if (copy != null) {
                            plugin.getLogger().info(viewer.getName() + " deleted the menu " + id + " (copy in backups/" + copy + ").");
                            lang().send(viewer, "builder.removed", "%menu%", id, "%file%", copy);
                        }
                        openLater();
                    }, this).openLater();
                } else if (e.isShiftClick()) {
                    plugin.chatInput().ask(viewer, "input.layout-title", layout.title(), text -> {
                        layout.title(text.equalsIgnoreCase("default") ? "" : text);
                        plugin.layouts().save(id);
                        open();
                    }, this::open);
                } else if (e.isRightClick()) {
                    layout.rows(layout.rows() >= 6 ? 1 : layout.rows() + 1);
                    plugin.layouts().save(id);
                    refresh();
                } else {
                    LayoutMenu m = LayoutMenus.create(plugin, id, viewer, null);
                    if (m != null) m.editMode().openLater();
                }
            });
            boolean on = layout.enabled();
            set(c + 9, button(on ? Material.LIME_DYE : Material.GRAY_DYE, "gui.layout.toggle",
                    "%status%", status(on), "%menu%", id), e -> {
                layout.enabled(!layout.enabled());
                plugin.layouts().save(id);
                plugin.menuCommands().refresh();
                lang().send(viewer, layout.enabled() ? "admin.menu-enabled" : "admin.menu-disabled", "%menu%", id);
                refresh();
            });
            c++;
        }
        if (custom.size() > 7) {
            set(35, button(Material.PAPER, "gui.layout.more", "%amount%", String.valueOf(custom.size() - 7)));
        }
        int last = (rows() - 1) * 9;
        set(last + 4, button(Material.BOOK, "gui.layout.help"));
        backButton(last, parent);
        fillBorder();
    }
}
