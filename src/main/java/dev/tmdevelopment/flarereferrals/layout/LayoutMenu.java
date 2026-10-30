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
    /** Menu opened by [back] (the one this menu was opened from), or null. */
    protected Menu backTo;
    private org.bukkit.scheduler.BukkitTask updater;
    /** Version of the layout when the editor was opened: changed meanwhile (import, reset...) = don't save over it. */
    private int openedVersion = -1;

    protected LayoutMenu(FlareReferrals plugin, Player viewer, String layoutId) {
        super(plugin, viewer);
        this.layoutId = layoutId;
        if (keyElement == null) {
            keyElement = new NamespacedKey(plugin, "layout_element");
            keyDeco = new NamespacedKey(plugin, "layout_deco");
        }
        ItemCodec.init(plugin);
    }

    /** Menu opened by [back]. */
    public LayoutMenu backTo(Menu parent) {
        if (parent != null) this.backTo = parent;
        return this;
    }

    public String layoutId() {
        return layoutId;
    }

    /** True while the menu is open in the drag and drop editor. */
    public boolean editing() {
        return editMode;
    }

    /** Elements that fill many slots (milestones, top players...): one marker per slot in the editor and the chest. */
    public java.util.Set<String> lists() {
        return java.util.Collections.emptySet();
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

    /** Whose head "head:{player}" shows: the viewer, or the player the menu is about. */
    protected org.bukkit.OfflinePlayer headOwner() {
        return viewer;
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
            if (!editMode && d.permission != null && !d.permission.isEmpty() && !viewer.hasPermission(d.permission)) continue;
            ItemStack item = decorationItem(d);
            if (item == null) continue;
            if (editMode && d.adminOnly) item = markAdminOnly(item);
            if (editMode) item = tag(item, keyDeco, String.valueOf(i));
            List<String> actions = d.actions;
            for (int slot : d.slots) {
                if (slot >= rows() * 9) continue;
                set(slot, item.clone(), editMode || actions.isEmpty() ? null : e -> MenuActions.run(plugin, this, viewer, actions, e.getClick(), titlePlaceholders()));
            }
        }
    }

    private ItemStack decorationItem(MenuLayout.Decoration d) {
        if (d.item != null) return d.item.clone();
        ItemStack base = ItemSpec.build(plugin, d.material, viewer);
        if (base == null) return null;
        ItemBuilder b = new ItemBuilder(base).hideFlags().glow(d.glow).amount(d.amount);
        // no name in the file = the Minecraft name of the item
        if (d.name != null) {
            String name = d.name.isEmpty() ? " " : d.name.replace("{prefix}", lang().prefix());
            b.name(Text.item(Text.replace(name, titlePlaceholders()), viewer));
        }
        if (d.lore != null) b.lore(Text.items(Text.replace(d.lore, titlePlaceholders()), viewer));
        if (d.customModelData != null) b.customModelData(d.customModelData);
        return b.build();
    }

    protected List<Integer> slots(String id) {
        List<Integer> out = new ArrayList<>();
        MenuLayout.Element e = layout().elements().get(id);
        if (e == null) return out;
        for (int s : e.slots) if (s < rows() * 9) out.add(s);
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
        MenuLayout.Element e = layout().elements().get(id);
        if (e == null) return; // not in this menu (menus made with the chest builder have only some buttons)
        if (e.adminOnly && !editMode && !isAdmin()) return; // players never see admin-only buttons
        List<Integer> slots = slots(id);
        if (slots.isEmpty()) return;
        String[] ph = concat(titlePlaceholders(), placeholders);
        org.bukkit.OfflinePlayer owner = headOwner();
        ItemStack item;
        if (e.material != null && owner != null && !viewer.getUniqueId().equals(owner.getUniqueId())
                && e.material.trim().toLowerCase(java.util.Locale.ROOT).matches("head:\\s*(\\{player\\}|%player%)")) {
            item = dev.tmdevelopment.flarereferrals.util.Heads.of(owner);
        } else {
            item = e.material == null ? null : ItemSpec.build(plugin, e.material, viewer);
        }
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
                set(slots.get(i), tag(new ItemBuilder(Material.ORANGE_STAINED_GLASS_PANE)
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

    /** [open] &lt;menu&gt;: menus of the plugin and the ones made with the chest builder. */
    public void openOther(String id) {
        if (plugin.layouts().isCustom(id)) {
            MenuLayout l = plugin.layouts().get(id);
            if (!l.enabled() && !isAdmin()) return;
            if (!l.openPermission().isEmpty() && !viewer.hasPermission(l.openPermission())) return;
        } else {
            String key = ("main".equals(id) ? "menu" : "top".equals(id) ? "topmenu" : id);
            if (!plugin.checkOpen(viewer, key)) return;
        }
        LayoutMenu m = LayoutMenus.create(plugin, id, viewer, this);
        if (m != null) m.openLater();
    }

    /** [back]: the menu this one was opened from, or close. */
    public void goBack() {
        if (backTo == null) viewer.closeInventory();
        else backTo.openLater();
    }

    // ---------------------------------------------------------------- edit mode events

    @Override
    public void open() {
        if (editMode) openedVersion = plugin.layouts().version(layoutId);
        super.open();
        if (editMode) {
            lang().send(viewer, "admin.layout-editing", "%menu%", layoutId);
            return;
        }
        startUpdater();
    }

    /** Items with "update: true" ([update] in the chest builder) are drawn again every second while the menu is open. */
    private void startUpdater() {
        if (updater != null) updater.cancel();
        updater = null;
        boolean any = false;
        for (MenuLayout.Decoration d : layout().decorations()) any |= d.update;
        if (!any) return;
        updater = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isOpen()) {
                if (updater != null) updater.cancel();
                updater = null;
                return;
            }
            refresh();
        }, 20L, 20L);
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
            if (!saveFromInventory()) {
                viewer.closeInventory();
                return;
            }
            MenuLayout layout = layout();
            Boolean now = null;
            for (MenuLayout.Element el : layout.elements().values()) {
                if (el.slots.contains(raw)) { el.adminOnly = !el.adminOnly; now = el.adminOnly; break; }
            }
            if (now == null) {
                for (int i = 0; i < layout.decorations().size(); i++) {
                    MenuLayout.Decoration d = layout.decorations().get(i);
                    if (!d.slots.contains(raw)) continue;
                    if (d.slots.size() > 1) {
                        // only the clicked slot changes: it becomes a decoration of its own
                        MenuLayout.Decoration one = copyOf(d);
                        one.origin = null;
                        one.slots = new ArrayList<>(java.util.Collections.singletonList(raw));
                        d.slots.remove(Integer.valueOf(raw));
                        layout.decorations().add(i + 1, one);
                        d = one;
                    }
                    d.adminOnly = !d.adminOnly;
                    now = d.adminOnly;
                    break;
                }
            }
            plugin.layouts().save(layoutId);
            openedVersion = plugin.layouts().version(layoutId);
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
        if (!editMode) {
            if (updater != null) updater.cancel();
            updater = null;
            return;
        }
        boolean stale = openedVersion != plugin.layouts().version(layoutId);
        boolean saved = saveFromInventory();
        // layout copies must not stay in the admin's inventory
        Inventory inv = viewer.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (isLayoutItem(it)) inv.setItem(i, null);
        }
        if (isLayoutItem(viewer.getItemOnCursor())) viewer.setItemOnCursor(null);
        if (saved) lang().send(viewer, "admin.layout-saved", "%menu%", layoutId);
        else if (stale) lang().send(viewer, "admin.layout-changed", "%menu%", layoutId);
        else lang().send(viewer, "admin.layout-not-saved", "%menu%", layoutId);
    }

    /**
     * Writes what is in the editor into the layout.
     * @return false when nothing was saved: the menu was changed meanwhile (import, reset...) or its file has a mistake
     */
    private boolean saveFromInventory() {
        MenuLayout layout = layout();
        Inventory inv = getInventory();
        if (openedVersion != plugin.layouts().version(layoutId)) {
            // changed by an import / reset while this editor was open: the new layout stays, the admin's items come back
            List<ItemStack> mine = new ArrayList<>();
            for (int slot = 0; slot < inv.getSize(); slot++) {
                ItemStack it = inv.getItem(slot);
                if (it != null && !it.getType().isAir() && read(it, keyElement) == null && read(it, keyDeco) == null) {
                    mine.add(it.clone());
                    inv.setItem(slot, null);
                }
            }
            giveBack(mine);
            return false;
        }
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
            MenuLayout.Decoration described = ItemCodec.of(it);
            String key = keyOf(described);
            MenuLayout.Decoration target = null;
            for (MenuLayout.Decoration d : newDecos) {
                if (d.origin == null && keyOf(d).equals(key)) target = d;
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
        boolean ok = plugin.layouts().save(layoutId);
        // the menu keeps a decoration made from the item, the admin gets his item back
        for (int slot = 0; slot < size; slot++) {
            ItemStack it = inv.getItem(slot);
            if (it != null && !it.getType().isAir() && read(it, keyElement) == null && read(it, keyDeco) == null) inv.setItem(slot, null);
        }
        giveBack(placed);
        return ok;
    }

    /** Turns any item into an editable decoration: material (heads keep their texture), name, lore, glow. */
    static MenuLayout.Decoration describe(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        return ItemCodec.describe(it, m != null && m.hasLore() && m.getLore() != null ? m.getLore() : new ArrayList<String>());
    }

    private static String keyOf(MenuLayout.Decoration d) {
        return ItemCodec.key(d);
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
        d.adminOnly = old.adminOnly;
        d.permission = old.permission;
        d.update = old.update;
        d.origin = old;
        return d;
    }

    // ---------------------------------------------------------------- chest builder

    /**
     * The menu as chest items ("layout chest"): decorations with their options and actions in the lore,
     * plugin buttons with "[button] &lt;id&gt;". Placeholders are shown as they are written in the file.
     */
    public Map<Integer, ItemStack> chestItems() {
        Map<Integer, ItemStack> out = new LinkedHashMap<>();
        int rows = rows();
        int size = rows * 9;
        MenuLayout layout = layout();
        for (MenuLayout.Decoration d : layout.decorations()) {
            ItemStack item = chestItem(d);
            if (item == null) continue;
            item = ItemCodec.tagDecoration(item, d, rows);
            for (int slot : d.slots) if (slot < size) out.put(slot, item.clone());
        }
        Map<String, Material> palette = palette();
        for (Map.Entry<String, MenuLayout.Element> en : layout.elements().entrySet()) {
            String id = en.getKey();
            MenuLayout.Element e = en.getValue();
            boolean list = lists().contains(id);
            int n = 0;
            for (int slot : e.slots) {
                if (slot >= size) continue;
                n++;
                Material icon = palette.get(id) == null ? Material.STONE : palette.get(id);
                out.put(slot, ItemCodec.tagButton(buttonItem(id, e, icon, list ? n : 0), id, rows));
            }
        }
        return out;
    }

    private ItemStack buttonItem(String id, MenuLayout.Element e, Material icon, int listNumber) {
        ItemStack base = e.material == null || listNumber > 0 ? null : ItemSpec.build(plugin, e.material, viewer);
        if (base == null) base = new ItemStack(icon);
        ItemBuilder b = new ItemBuilder(base).hideFlags();
        if (listNumber > 0) {
            b.name(lang().item("gui.layout.list-slot.name", viewer, "%element%", elementName(id), "%number%", String.valueOf(listNumber)));
            b.amount(Math.min(64, listNumber));
        } else {
            b.name(Text.itemAbout(e.name != null ? e.name.replace("{prefix}", lang().prefix()) : elementName(id), null));
            if (e.lore != null) b.lore(Text.itemsAbout(e.lore, null));
            if (Boolean.TRUE.equals(e.glow)) b.glow(true);
            if (e.customModelData != null) b.customModelData(e.customModelData);
        }
        List<String> extra = new ArrayList<>();
        extra.add("");
        extra.add("[button] " + id);
        return withLines(b.build(), extra);
    }

    private ItemStack chestItem(MenuLayout.Decoration d) {
        ItemStack item;
        if (d.item != null) {
            item = d.item.clone();
        } else {
            ItemStack base = ItemSpec.build(plugin, d.material, viewer);
            if (base == null) return null;
            ItemBuilder b = new ItemBuilder(base).hideFlags().glow(d.glow).amount(d.amount);
            if (d.name != null) b.name(Text.itemAbout(d.name.isEmpty() ? " " : d.name.replace("{prefix}", lang().prefix()), null));
            if (d.lore != null) b.lore(Text.itemsAbout(d.lore, null));
            if (d.customModelData != null) b.customModelData(d.customModelData);
            item = b.build();
        }
        // a text line that starts with [ gets a \ so it is not read as an action
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasLore() && meta.getLore() != null) {
            List<String> lore = new ArrayList<>();
            for (String l : meta.getLore()) lore.add(ItemCodec.escapeLegacy(l));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return withLines(item, ItemCodec.optionLines(d));
    }

    /** Adds gray lines (options, actions, [button]) at the end of the lore, written as they are. */
    private static ItemStack withLines(ItemStack item, List<String> lines) {
        if (lines.isEmpty()) return item;
        ItemMeta m = item.getItemMeta();
        if (m == null) return item;
        List<String> lore = m.hasLore() && m.getLore() != null ? new ArrayList<>(m.getLore()) : new ArrayList<String>();
        for (String l : lines) lore.add(l.isEmpty() ? "" : "\u00a78" + l);
        m.setLore(lore);
        item.setItemMeta(m);
        return item;
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
