package dev.tmdevelopment.flarereferrals.gui;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.config.Lang;
import dev.tmdevelopment.flarereferrals.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class Menu implements InventoryHolder {

    protected final FlareReferrals plugin;
    protected final Player viewer;
    private Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected Menu(FlareReferrals plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    protected abstract Component title();

    protected abstract int rows();

    protected abstract void build();

    protected Lang lang() {
        return plugin.lang();
    }

    public void open() {
        inventory = Bukkit.createInventory(this, rows() * 9, dev.tmdevelopment.flarereferrals.util.Platform.legacy(title()));
        actions.clear();
        build();
        viewer.openInventory(inventory);
    }

    /** Opens on the next tick (safe to call from click / close handlers). */
    public void openLater() {
        Bukkit.getScheduler().runTask(plugin, this::open);
    }

    public void refresh() {
        if (inventory == null) return;
        inventory.clear();
        actions.clear();
        build();
    }

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
    }

    protected void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    protected ItemStack filler() {
        return new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(Component.text(" ")).hideFlags().build();
    }

    protected void fillBorder() {
        int size = inventory.getSize();
        for (int i = 0; i < size; i++) {
            int row = i / 9, col = i % 9;
            if (row == 0 || row == rows() - 1 || col == 0 || col == 8) {
                if (inventory.getItem(i) == null) inventory.setItem(i, filler());
            }
        }
    }

    protected void fillRow(int row) {
        for (int i = row * 9; i < row * 9 + 9; i++) {
            if (inventory.getItem(i) == null) inventory.setItem(i, filler());
        }
    }

    /** Standard button from lang: gui.&lt;path&gt;.name / gui.&lt;path&gt;.lore */
    protected ItemStack button(Material mat, String langPath, String... repl) {
        return new ItemBuilder(mat)
                .name(lang().item(langPath + ".name", viewer, repl))
                .lore(lang().lore(langPath + ".lore", viewer, repl))
                .hideFlags()
                .build();
    }

    protected ItemStack button(ItemStack base, String langPath, String... repl) {
        return new ItemBuilder(base)
                .name(lang().item(langPath + ".name", viewer, repl))
                .lore(lang().lore(langPath + ".lore", viewer, repl))
                .hideFlags()
                .build();
    }

    protected ItemStack back(Menu parent) {
        return button(Material.ARROW, parent == null ? "gui.common.close" : "gui.common.back");
    }

    protected void backButton(int slot, Menu parent) {
        set(slot, back(parent), e -> {
            if (parent == null) viewer.closeInventory();
            else parent.openLater();
        });
    }

    protected String status(boolean on) {
        return lang().raw(on ? "gui.common.enabled" : "gui.common.disabled");
    }

    protected List<String> list(String path) {
        return lang().lines(path);
    }

    // ---------------------------------------------------------------- events

    public void handleClick(InventoryClickEvent e) {
        e.setCancelled(true);
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= inventory.getSize()) return;
        // only left / right clicks (with or without shift): Q, number keys, F, middle and double click do nothing
        org.bukkit.event.inventory.ClickType c = e.getClick();
        if (c != org.bukkit.event.inventory.ClickType.LEFT && c != org.bukkit.event.inventory.ClickType.RIGHT
                && c != org.bukkit.event.inventory.ClickType.SHIFT_LEFT && c != org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
            return;
        }
        Consumer<InventoryClickEvent> a = actions.get(raw);
        if (a != null) {
            plugin.referrals().sound(viewer, "click");
            a.accept(e);
        }
    }

    public void handleDrag(InventoryDragEvent e) {
        e.setCancelled(true);
    }

    /** Gives copies back to the viewer (an item put in an editor stays in the admin's inventory too). */
    protected void giveBack(java.util.List<ItemStack> items) {
        for (ItemStack it : items) {
            if (it == null || it.getType().isAir()) continue;
            for (ItemStack left : viewer.getInventory().addItem(it.clone()).values()) {
                viewer.getWorld().dropItemNaturally(viewer.getLocation(), left);
            }
        }
    }

    public void handleClose(InventoryCloseEvent e) {
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
