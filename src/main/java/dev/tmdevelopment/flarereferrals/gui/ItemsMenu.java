package dev.tmdevelopment.flarereferrals.gui;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.reward.RewardSet;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Editable chest: drag any item (custom names, lore, enchants, NBT...) into the top 5 rows.
 * Saved when the menu is closed.
 */
public final class ItemsMenu extends Menu {

    private static final int STORAGE = 45;

    private final RewardSet set;
    private final Menu parent;
    private boolean saved = false;

    public ItemsMenu(FlareReferrals plugin, Player viewer, RewardSet set, Menu parent) {
        super(plugin, viewer);
        this.set = set;
        this.parent = parent;
    }

    @Override
    protected Component title() {
        return lang().title("gui.items.title", viewer, "%name%", RewardSetMenu.displayName(plugin, set));
    }

    @Override protected int rows() { return 6; }

    @Override
    protected void build() {
        List<ItemStack> items = set.items();
        for (int i = 0; i < items.size() && i < STORAGE; i++) {
            getInventory().setItem(i, items.get(i).clone());
        }
        set(45, button(Material.BOOK, "gui.items.help"));
        set(47, button(Material.PLAYER_HEAD, "gui.items.add-head"), e -> {
            saveItems();
            plugin.chatInput().ask(viewer, "input.head", text -> dev.tmdevelopment.flarereferrals.util.Heads.create(plugin, text, head -> {
                if (head == null) {
                    lang().send(viewer, "admin.head-invalid", "%input%", text);
                } else {
                    set.items().add(head);
                    plugin.rewards().save();
                    lang().send(viewer, "admin.head-added");
                }
                new ItemsMenu(plugin, viewer, set, parent).open();
            }), () -> new ItemsMenu(plugin, viewer, set, parent).open());
        });
        set(49, button(Material.LIME_CONCRETE, "gui.items.save"), e -> {
            saveItems();
            if (parent != null) parent.openLater();
            else viewer.closeInventory();
        });
        set(53, button(Material.RED_CONCRETE, "gui.items.clear"), e -> {
            for (int i = 0; i < STORAGE; i++) getInventory().setItem(i, null);
        });
        fillRow(5);
    }

    @Override
    public void handleClick(InventoryClickEvent e) {
        int raw = e.getRawSlot();
        int topSize = getInventory().getSize();
        if (raw >= STORAGE && raw < topSize) {
            super.handleClick(e); // control row
            return;
        }
        if (raw >= topSize && e.isShiftClick()) {
            // shift-click from the player inventory: only allow if there is room in the storage area
            ItemStack moving = e.getCurrentItem();
            e.setCancelled(true);
            if (moving == null || moving.getType().isAir()) return;
            for (int i = 0; i < STORAGE; i++) {
                if (getInventory().getItem(i) == null) {
                    getInventory().setItem(i, moving.clone());
                    e.setCurrentItem(null);
                    return;
                }
            }
            return;
        }
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            e.setCancelled(true); // would steal the control buttons
        }
        // everything else (storage slots / player inventory) is allowed
    }

    @Override
    public void handleDrag(InventoryDragEvent e) {
        for (int slot : e.getRawSlots()) {
            if (slot >= STORAGE && slot < getInventory().getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @Override
    public void handleClose(InventoryCloseEvent e) {
        saveItems();
    }

    private void saveItems() {
        if (saved) return;
        saved = true;
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < STORAGE; i++) {
            ItemStack it = getInventory().getItem(i);
            if (it != null && !it.getType().isAir()) list.add(it.clone());
        }
        set.items().clear();
        set.items().addAll(list);
        plugin.rewards().save();
        lang().send(viewer, "admin.items-saved", "%amount%", String.valueOf(list.size()));
    }
}
