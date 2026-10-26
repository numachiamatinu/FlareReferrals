package dev.tmdevelopment.flarereferrals.layout;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.gui.Menu;
import dev.tmdevelopment.flarereferrals.util.ItemBuilder;
import dev.tmdevelopment.flarereferrals.util.ItemSpec;
import dev.tmdevelopment.flarereferrals.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A menu whose layout comes from menus/&lt;id&gt;.yml.
 * In edit mode the same menu opens with free drag and drop: move buttons, add decorations from your
 * inventory (glass, heads, anything), remove what you don't want; closing saves the layout.
 */
public abstract class LayoutMenu extends Menu {

    private static NamespacedKey keyElement;
    private static NamespacedKey keyDeco;

    protected final String layoutId;
    protected boolean editMode = false;

    protected LayoutMenu(FlareReferrals plugin, Player viewer, String layoutId) {
        super(plugin, viewer);
        this.layoutId = layoutId;
        if (keyElement == null) {
            keyElement = new NamespacedKey(plugin, "layout_element");
            keyDeco = new NamespacedKey(plugin, "layout_deco");
        }
    }

    /** Opens this menu in edit mode. */
    public LayoutMenu editMode() {
        this.editMode = true;
        return this;
    }

    protected MenuLayout layout() {
        return plugin.layouts().get(layoutId);
    }

    /** Title from lang, used when the layout file has no title. */
    protected abstract Component defaultTitle();

    /** Builds the buttons with {@link #element} and {@link #list}. */
    protected abstract void buildElements();

    /** Element id -> icon shown in the editor palette. */
    public abstract Map<String, Material> palette();

    protected String[] titlePlaceholders() {
        return new String[]{"%player%", viewer.getName()};
    }

    @Override
    protected Component title() {
        if (editMode) return lang().title("gui.layout.editor-title", viewer, "%menu%", layoutId);
        String t = layout().title();
        if (t == null || dev.tmdevelopment.flarereferrals.util.J8.blank(t)) return defaultTitle();
        return Text.parse(Text.replace(t, titlePlaceholders()), viewer);
    }

    @Override
    protected int rows() {
        return layout().rows();
    }

    @Override
    protected final void build() {
        placeDecorations();
        buildElements();
    }

    // ---------------------------------------------------------------- placing

    private void placeDecorations() {
        List<MenuLayout.Decoration> decos = layout().decorations();
        for (int i = 0; i < decos.size(); i++) {
            MenuLayout.Decoration d = decos.get(i);
            if (d.adminOnly && !editMode && !isAdmin()) continue; // players never see admin-only items
            ItemStack item = decorationItem(d);
            if (item == null) continue;
            if (editMode && d.adminOnly) item = markAdminOnly(item);
            if (editMode) item = tag(item, keyDeco, String.valueOf(i));
            List<String> actions = d.actions;
            for (int slot : d.slots) {
                if (slot >= rows() * 9) continue;
                set(slot, item.clone(), editMode || actions.isEmpty() ? null : e -> runActions(actions));
            }
        }
    }

    private ItemStack decorationItem(MenuLayout.Decoration d) {
        if (d.item != null) return d.item.clone();
        ItemStack base = ItemSpec.build(plugin, d.material, viewer);
        if (base == null) return null;
        ItemBuilder b = new ItemBuilder(base).hideFlags().glow(d.glow).amount(d.amount);
        String name = d.name == null || d.name.isEmpty() ? " " : d.name.replace("{prefix}", lang().prefix());
        b.name(Text.item(Text.replace(name, titlePlaceholders()), viewer));
        if (d.lore != null) b.lore(Text.items(Text.replace(d.lore, titlePlaceholders()), viewer));
        if (d.customModelData != null) b.customModelData(d.customModelData);
        return b.build();
    }

    protected List<Integer> slots(String id) {
        List<Integer> out = new ArrayList<>();
        for (int s : layout().element(id).slots) if (s < rows() * 9) out.add(s);
        return out;
    }

    /**
     * Places a button on every slot of the element. Material, name and lore come from the layout file
     * (menus/&lt;id&gt;.yml); when the file has none, from lang/en.yml (langPath.name / langPath.lore).
     * @param extraLore dynamic lines added after the lore (reward summaries...), may be null
     * @param placeholders "%key%", value pairs, usable as {key} in the file
     */
    protected void element(String id, ItemStack base, String langPath, List<Component> extraLore, boolean glow,
                           Consumer<InventoryClickEvent> action, String... placeholders) {
        MenuLayout.Element e = layout().element(id);
        if (e.adminOnly && !editMode && !isAdmin()) return; // players never see admin-only buttons
        List<Integer> slots = slots(id);
        if (slots.isEmpty()) return;
        String[] ph = concat(titlePlaceholders(), placeholders);
        ItemStack item = e.material == null ? null : ItemSpec.build(plugin, e.material, viewer);
        if (item == null) item = base;
        String name = e.name != null ? e.name : lang().raw(langPath + ".name");
        List<String> lore = e.lore != null ? e.lore : lang().lines(langPath + ".lore");
        ItemBuilder b = new ItemBuilder(item)
                .name(Text.item(Text.replace(name.replace("{prefix}", lang().prefix()), ph), viewer))
                .lore(Text.items(Text.replace(lore, ph), viewer))
                .glow(glow || Boolean.TRUE.equals(e.glow))
                .hideFlags();
        if (e.customModelData != null) b.customModelData(e.customModelData);
        if (extraLore != null && !extraLore.isEmpty()) b.addLore(extraLore);
        ItemStack built = b.build();
        if (editMode && e.adminOnly) built = markAdminOnly(built);
        if (editMode) built = tag(built, keyElement, id);
        for (int s : slots) set(s, built.clone(), editMode ? null : action);
    }

    /** Template (name / lore) written in the file for the entries of a list element, or null. */
    protected MenuLayout.Element template(String id) {
        return layout().element(id);
    }

    /** Slot closest to the visual center (average row / column) of a group of slots. */
    static int centerOf(List<Integer> slots) {
        double r = 0, c = 0;
        for (int s : slots) { r += s / 9; c += s % 9; }
        r /= slots.size();
        c /= slots.size();
        int best = slots.get(0);
        double bestDist = Double.MAX_VALUE;
        for (int s : slots) {
            double d = Math.pow(s / 9 - r, 2) + Math.pow(s % 9 - c, 2);
            if (d < bestDist - 1e-9) { bestDist = d; best = s; }
        }
        return best;
    }

    private static String[] concat(String[] a, String[] b) {
        String[] out = new String[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /**
     * Fills the slots of a list element in order (milestones, top players, reward items...).
     * In edit mode every slot of the list shows a marker: move it to move the slot, take it out to remove it.
     * @return how many entries were placed
     */
    protected int list(String id, List<ItemStack> items, List<Consumer<InventoryClickEvent>> actions, ItemStack whenEmpty) {
        List<Integer> slots = slots(id);
        if (editMode) {
            for (int i = 0; i < slots.size(); i++) {
                set(slots.get(i), tag(new ItemBuilder(Material.LIGHT_BLUE_STAINED_GLASS_PANE)
                        .name(lang().item("gui.layout.list-slot.name", viewer,
                                "%element%", elementName(id), "%number%", String.valueOf(i + 1)))
                        .lore(lang().lore("gui.layout.list-slot.lore", viewer))
                        .amount(Math.min(64, i + 1)).hideFlags().build(), keyElement, id));
            }
            return 0;
        }
        if (items.isEmpty() && whenEmpty != null && !slots.isEmpty()) {
            // nothing to show: put the "empty" item in the visual center of the list area
            set(centerOf(slots), whenEmpty);
            return 0;
        }
        int placed = 0;
        for (int i = 0; i < slots.size() && i < items.size(); i++) {
            set(slots.get(i), items.get(i), actions == null || i >= actions.size() ? null : actions.get(i));
            placed++;
        }
        return placed;
    }

    protected boolean isAdmin() {
        return dev.tmdevelopment.flarereferrals.util.Perms.has(viewer, "admin");
    }

    /** Editor only: shows that an item is visible to admins only. */
    private ItemStack markAdminOnly(ItemStack item) {
        return new ItemBuilder(item).addLore(lang().lore("gui.layout.admin-only-tag", viewer)).build();
    }

        public String elementName(String id) {
        String raw = lang().raw("gui.layout.elements." + id);
        if (raw.startsWith("<red>Missing")) {
            String p = id.replace('-', ' ');
            return "&f" + Character.toUpperCase(p.charAt(0)) + p.substring(1);
        }
        return raw;
    }

    // ---------------------------------------------------------------- actions

    private void runActions(List<String> actions) {
        for (String a : actions) {
            String s = Text.replace(a.trim(), titlePlaceholders());
            String low = s.toLowerCase();
            if (low.startsWith("[close]")) {
                viewer.closeInventory();
            } else if (low.startsWith("[open]")) {
                String id = s.substring(6).trim().toLowerCase();
                String key = ("main".equals(id) ? "menu" : "top".equals(id) ? "topmenu" : id);
                if (!plugin.checkOpen(viewer, key)) continue;
                LayoutMenu m = LayoutMenus.create(plugin, id, viewer, this);
                if (m != null) m.openLater();
            } else if (low.startsWith("[player]")) {
                String cmd = s.substring(8).trim();
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                viewer.performCommand(cmd);
            } else if (low.startsWith("[console]")) {
                String cmd = s.substring(9).trim();
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } else if (low.startsWith("[message]")) {
                dev.tmdevelopment.flarereferrals.util.Platform.send(viewer, Text.parse(s.substring(9).trim().replace("{prefix}", lang().prefix()), viewer));
            } else if (low.startsWith("[sound]")) {
                try {
                    viewer.playSound(viewer.getLocation(), s.substring(7).trim().toLowerCase(), 1f, 1f);
                } catch (Exception ignored) {
                }
            }
        }
    }

    // ---------------------------------------------------------------- edit mode events

    @Override
    public void open() {
        super.open();
        if (editMode) lang().send(viewer, "admin.layout-editing", "%menu%", layoutId);
    }

    @Override
    public void handleClick(InventoryClickEvent e) {
        if (!editMode) {
            super.handleClick(e);
            return;
        }
        if (e.getAction().name().startsWith("DROP")) {
            e.setCancelled(true); // layout items can't be thrown out of the editor
            return;
        }
        int raw = e.getRawSlot();
        int size = getInventory().getSize();
        ItemStack current = e.getCurrentItem();
        boolean emptySlot = current == null || current.getType().isAir();
        if (raw >= 0 && raw < size && emptySlot && e.isShiftClick() && e.isRightClick()) {
            e.setCancelled(true);
            new LayoutPaletteMenu(plugin, viewer, layoutId, raw).openLater();
            return;
        }
        if (raw >= 0 && raw < size && !emptySlot && e.isShiftClick() && e.isRightClick()) {
            // toggle "admin only" for the clicked button / decoration
            e.setCancelled(true);
            saveFromInventory();
            MenuLayout layout = layout();
            Boolean now = null;
            for (MenuLayout.Element el : layout.elements().values()) {
                if (el.slots.contains(raw)) { el.adminOnly = !el.adminOnly; now = el.adminOnly; break; }
            }
            if (now == null) {
                for (MenuLayout.Decoration d : layout.decorations()) {
                    if (d.slots.contains(raw)) { d.adminOnly = !d.adminOnly; now = d.adminOnly; break; }
                }
            }
            plugin.layouts().save(layoutId);
            if (now != null) lang().send(viewer, now ? "admin.layout-admin-only-on" : "admin.layout-admin-only-off");
            refresh();
        }
        // anything else: free drag and drop
    }

    @Override
    public void handleDrag(InventoryDragEvent e) {
        if (!editMode) super.handleDrag(e);
    }

    @Override
    public void handleClose(InventoryCloseEvent e) {
        if (!editMode) return;
        saveFromInventory();
        // layout copies must not stay in the admin's inventory
        Inventory inv = viewer.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (isLayoutItem(it)) inv.setItem(i, null);
        }
        if (isLayoutItem(viewer.getItemOnCursor())) viewer.setItemOnCursor(null);
        lang().send(viewer, "admin.layout-saved", "%menu%", layoutId);
    }

    private void saveFromInventory() {
        MenuLayout layout = layout();
        Inventory inv = getInventory();
        List<MenuLayout.Decoration> oldDecos = new ArrayList<>(layout.decorations());
        Map<String, List<Integer>> newSlots = new LinkedHashMap<>();
        for (String id : layout.elements().keySet()) newSlots.put(id, new ArrayList<>());
        // keep slots outside the current size (rows were reduced) as they were
        int size = inv.getSize();
        for (Map.Entry<String, MenuLayout.Element> en : layout.elements().entrySet()) {
            for (int s : en.getValue().slots) if (s >= size) newSlots.get(en.getKey()).add(s);
        }
        List<MenuLayout.Decoration> newDecos = new ArrayList<>();
        List<ItemStack> placed = new ArrayList<>();

        for (int slot = 0; slot < size; slot++) {
            ItemStack it = inv.getItem(slot);
            if (it == null || it.getType().isAir()) continue;
            String el = read(it, keyElement);
            if (el != null) {
                newSlots.computeIfAbsent(el, k -> new ArrayList<>()).add(slot);
                continue;
            }
            String decoIdx = read(it, keyDeco);
            MenuLayout.Decoration old = null;
            if (decoIdx != null) {
                try { old = oldDecos.get(Integer.parseInt(decoIdx)); } catch (Exception ignored) {}
            }
            if (old != null) {
                // moved decoration: keep its original definition and actions
                MenuLayout.Decoration target = null;
                for (MenuLayout.Decoration d : newDecos) if (d.origin == old) target = d;
                if (target == null) {
                    target = copyOf(old);
                    newDecos.add(target);
                }
                target.slots.add(slot);
                continue;
            }
            // new item placed by the admin: written in a readable form (material, name, lore, glow,
            // actions) so it can be edited later in menus/<menu>.yml
            placed.add(it.clone());
            MenuLayout.Decoration described = describe(it);
            String key = keyOf(described);
            MenuLayout.Decoration target = null;
            for (MenuLayout.Decoration d : newDecos) {
                if (d.origin == null && d.item == null && keyOf(d).equals(key)) target = d;
            }
            if (target == null) {
                target = described;
                newDecos.add(target);
            }
            target.slots.add(slot);
        }
        // decorations outside the visible area stay
        for (MenuLayout.Decoration d : oldDecos) {
            List<Integer> outside = new ArrayList<>();
            for (int s : d.slots) if (s >= size) outside.add(s);
            if (!outside.isEmpty()) {
                MenuLayout.Decoration c = copyOf(d);
                c.slots.addAll(outside);
                newDecos.add(c);
            }
        }

        for (Map.Entry<String, List<Integer>> en : newSlots.entrySet()) layout.element(en.getKey()).slots = en.getValue();
        layout.decorations().clear();
        for (MenuLayout.Decoration d : newDecos) {
            d.origin = null;
            layout.decorations().add(d);
        }
        plugin.layouts().save(layoutId);
        // the menu keeps a decoration made from the item, the admin gets his item back
        for (int slot = 0; slot < size; slot++) {
            ItemStack it = inv.getItem(slot);
            if (it != null && !it.getType().isAir() && read(it, keyElement) == null && read(it, keyDeco) == null) inv.setItem(slot, null);
        }
        giveBack(placed);
    }

    /** Turns any item into an editable decoration: material (heads keep their texture), name, lore, glow. */
    static MenuLayout.Decoration describe(ItemStack it) {
        MenuLayout.Decoration d = new MenuLayout.Decoration();
        ItemMeta m = it.getItemMeta();
        String material = it.getType().name();
        if (m instanceof org.bukkit.inventory.meta.SkullMeta) {
            String head = dev.tmdevelopment.flarereferrals.util.Compat.describeHead((org.bukkit.inventory.meta.SkullMeta) m);
            if (head != null) material = head;
        }
        d.material = material;
        d.name = m != null && m.hasDisplayName() ? dev.tmdevelopment.flarereferrals.util.Platform.toEditable(m.getDisplayName()) : " ";
        d.lore = new ArrayList<>();
        if (m != null && m.hasLore() && m.getLore() != null) for (String c : m.getLore()) d.lore.add(dev.tmdevelopment.flarereferrals.util.Platform.toEditable(c));
        if (m != null) {
            d.glow = dev.tmdevelopment.flarereferrals.util.Compat.hasGlow(m);
            try {
                if (m.hasCustomModelData()) d.customModelData = m.getCustomModelData();
            } catch (Throwable ignored) {
            }
        }
        d.amount = Math.max(1, it.getAmount());
        return d;
    }

    private static String keyOf(MenuLayout.Decoration d) {
        return d.material + "|" + d.name + "|" + d.lore + "|" + d.glow + "|" + d.amount + "|" + d.customModelData + "|" + d.actions;
    }

    private static MenuLayout.Decoration copyOf(MenuLayout.Decoration old) {
        MenuLayout.Decoration d = new MenuLayout.Decoration();
        d.item = old.item == null ? null : old.item.clone();
        d.material = old.material;
        d.name = old.name;
        d.lore = old.lore == null ? null : new ArrayList<>(old.lore);
        d.glow = old.glow;
        d.amount = old.amount;
        d.customModelData = old.customModelData;
        d.actions = new ArrayList<>(old.actions);
        d.origin = old;
        return d;
    }

    // ---------------------------------------------------------------- tags

    private static ItemStack tag(ItemStack item, NamespacedKey key, String value) {
        ItemStack c = item.clone();
        ItemMeta m = c.getItemMeta();
        if (m == null) return c;
        m.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        c.setItemMeta(m);
        return c;
    }

    private static String read(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta m = item.getItemMeta();
        return m == null ? null : m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private static boolean isLayoutItem(ItemStack it) {
        return keyElement != null && (read(it, keyElement) != null || read(it, keyDeco) != null);
    }
}
