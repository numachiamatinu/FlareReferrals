package dev.tmdevelopment.flarereferrals.layout;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.gui.Menu;
import dev.tmdevelopment.flarereferrals.util.ItemBuilder;
import dev.tmdevelopment.flarereferrals.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Opened with shift + right-click on an empty slot in the editor: put a button or a glass pane there. */
public final class LayoutPaletteMenu extends Menu {

    private static final Material[] GLASS = {
            Material.BLACK_STAINED_GLASS_PANE, Material.GRAY_STAINED_GLASS_PANE, Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            Material.WHITE_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE, Material.PINK_STAINED_GLASS_PANE
    };

    private final String layoutId;
    private final int targetSlot;

    public LayoutPaletteMenu(FlareReferrals plugin, Player viewer, String layoutId, int targetSlot) {
        super(plugin, viewer);
        this.layoutId = layoutId;
        this.targetSlot = targetSlot;
    }

    @Override protected Component title() { return lang().title("gui.layout.palette-title", viewer, "%slot%", String.valueOf(targetSlot)); }
    @Override protected int rows() { return 5; }

    private void reopenEditor() {
        LayoutMenu m = LayoutMenus.create(plugin, layoutId, viewer, null);
        if (m != null) m.editMode().openLater();
    }

    /** Frees the target slot from any element / decoration. */
    private void clearTarget(MenuLayout layout) {
        for (MenuLayout.Element e : layout.elements().values()) e.slots.remove(Integer.valueOf(targetSlot));
        layout.decorations().removeIf(d -> {
            d.slots.remove(Integer.valueOf(targetSlot));
            return d.slots.isEmpty();
        });
    }

    @Override
    protected void build() {
        LayoutMenu sample = LayoutMenus.create(plugin, layoutId, viewer, null);
        if (sample == null) return;
        MenuLayout layout = plugin.layouts().get(layoutId);

        int slot = 10;
        for (Map.Entry<String, Material> en : sample.palette().entrySet()) {
            if (slot == 17) slot = 19;
            if (slot >= 26) break;
            String id = en.getKey();
            int used = layout.elements().get(id) == null ? 0 : layout.elements().get(id).slots.size();
            set(slot++, new ItemBuilder(en.getValue())
                    .name(Text.item(sample.elementName(id), viewer))
                    .lore(lang().lore("gui.layout.palette-element", viewer, "%amount%", String.valueOf(used)))
                    .glow(used == 0).hideFlags().build(), e -> {
                clearTarget(layout);
                layout.element(id).slots.add(targetSlot);
                plugin.layouts().save(layoutId);
                reopenEditor();
            });
        }

        int g = 28;
        for (Material glass : GLASS) {
            if (g == 35) g = 37;
            set(g++, new ItemBuilder(glass).name(lang().item("gui.layout.palette-glass.name", viewer))
                    .lore(lang().lore("gui.layout.palette-glass.lore", viewer)).build(), e -> {
                clearTarget(layout);
                MenuLayout.Decoration d = new MenuLayout.Decoration();
                d.material = glass.name();
                d.name = " ";
                d.lore = new java.util.ArrayList<>();
                d.slots.add(targetSlot);
                layout.decorations().add(d);
                plugin.layouts().save(layoutId);
                reopenEditor();
            });
        }

        set(4, button(Material.BOOK, "gui.layout.palette-help"));
        set(40, button(Material.ARROW, "gui.common.back"), e -> reopenEditor());
        for (int i = 0; i < 45; i++) if (getInventory().getItem(i) == null) getInventory().setItem(i, filler());
    }
}
