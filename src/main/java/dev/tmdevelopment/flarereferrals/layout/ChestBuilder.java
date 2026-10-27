package dev.tmdevelopment.flarereferrals.layout;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.config.Lang;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Chest builder: build a menu in a real chest, look at it and type one command.
 *
 *   /referrals layout add &lt;name&gt;      new menu from the chest (menus/&lt;name&gt;.yml, opened with /&lt;name&gt;)
 *   /referrals layout import &lt;menu&gt;   the chest replaces the items of a menu (plugin buttons stay where they are)
 *   /referrals layout chest &lt;menu&gt;    puts a menu in the empty chest you look at
 *   /referrals layout remove &lt;name&gt;   deletes a menu made with add (a copy goes in backups/)
 */
public final class ChestBuilder {

    /** Words that can't be menu names (they are sub commands of /referrals layout). */
    public static final List<String> WORDS = dev.tmdevelopment.flarereferrals.util.J8.list("add", "import", "chest", "remove", "list");

    private final FlareReferrals plugin;
    private final String label;

    public ChestBuilder(FlareReferrals plugin, String label) {
        this.plugin = plugin;
        this.label = label;
    }

    private Lang lang() {
        return plugin.lang();
    }

    public void run(Player p, String[] args) {
        String sub = args[1].toLowerCase(Locale.ROOT);
        String name = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : null;
        if (sub.equals("list")) {
            list(p);
            return;
        }
        if (name == null) {
            lang().send(p, "builder.usage", "%label%", label);
            return;
        }
        switch (sub) {
            case "add":
                add(p, name);
                break;
            case "import":
                importTo(p, name);
                break;
            case "chest":
                toChest(p, name);
                break;
            case "remove":
                remove(p, name, args.length > 3 && args[3].equalsIgnoreCase("confirm"));
                break;
            default:
                lang().send(p, "builder.usage", "%label%", label);
        }
    }

    // ------------------------------------------------------------------ container

    /** The chest / barrel / shulker box the player looks at, or null (the reason is already sent). */
    private Inventory container(Player p) {
        Block b;
        try {
            b = p.getTargetBlockExact(5);
        } catch (Throwable t) {
            b = null;
        }
        if (b == null) {
            lang().send(p, "builder.no-container");
            return null;
        }
        BlockState state = b.getState();
        if (!(state instanceof Container)) {
            lang().send(p, "builder.no-container");
            return null;
        }
        if (!(state instanceof Chest) && !(state instanceof Barrel) && !(state instanceof ShulkerBox)) {
            lang().send(p, "builder.unsupported", "%block%", LayoutManager.prettyName(b.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ')));
            return null;
        }
        // a double chest gives the whole 54 slots
        return ((Container) state).getInventory();
    }

    private static String customName(Player p) {
        try {
            Block b = p.getTargetBlockExact(5);
            BlockState state = b == null ? null : b.getState();
            if (state instanceof org.bukkit.Nameable) {
                String n = ((org.bukkit.Nameable) state).getCustomName();
                if (n != null && !n.trim().isEmpty()) return dev.tmdevelopment.flarereferrals.util.Platform.toEditable(n);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ------------------------------------------------------------------ reading the chest

    /** Everything found in the chest. */
    private static final class Scan {
        int rows;
        final List<MenuLayout.Decoration> decorations = new ArrayList<>();
        final Map<String, List<Integer>> buttons = new LinkedHashMap<>();
        final Map<String, MenuLayout.Decoration> looks = new LinkedHashMap<>();
        final Map<String, Boolean> adminOnly = new LinkedHashMap<>();
        final List<String> warnings = new ArrayList<>();
        int items;
        int actions;
        /** The chest was filled by "layout chest": buttons taken out of it are removed from the menu. */
        boolean fromChest;
    }

    private Scan scan(Player p, Inventory inv, LayoutMenu sample) {
        Scan s = new Scan();
        java.util.Set<String> valid = sample.palette().keySet();
        int size = inv.getSize();
        int containerRows = Math.max(1, size / 9);
        Map<String, MenuLayout.Decoration> byKey = new LinkedHashMap<>();
        TreeSet<Integer> taggedRows = new TreeSet<>();
        int lastUsedRow = 0;
        for (int slot = 0; slot < size && slot < 54; slot++) {
            ItemStack it = inv.getItem(slot);
            if (ItemCodec.isAir(it)) continue;
            lastUsedRow = Math.max(lastUsedRow, slot / 9 + 1);
            ItemCodec.Read r = ItemCodec.read(it);
            if (r.rows > 0) taggedRows.add(r.rows);
            if (r.button != null) {
                if (!valid.contains(r.button)) {
                    s.warnings.add(text("builder.warn-unknown-button", "%button%", r.button, "%slot%", String.valueOf(slot),
                            "%buttons%", String.join(", ", valid)));
                    continue;
                }
                s.buttons.computeIfAbsent(r.button, k -> new ArrayList<>()).add(slot);
                if (r.look != null && !sample.lists().contains(r.button)) s.looks.putIfAbsent(r.button, r.look);
                if (r.buttonAdminOnly != null) s.adminOnly.putIfAbsent(r.button, r.buttonAdminOnly);
                continue;
            }
            for (String u : r.unknown) {
                s.warnings.add(text("builder.warn-unknown-action", "%action%", u, "%slot%", String.valueOf(slot)));
            }
            s.items++;
            String key = ItemCodec.key(r.deco);
            MenuLayout.Decoration d = byKey.get(key);
            if (d == null) {
                d = r.deco;
                d.slots = new ArrayList<>();
                byKey.put(key, d);
                s.decorations.add(d);
                s.actions += d.actions.size();
            }
            d.slots.add(slot);
        }
        // put in the chest by "layout chest": same rows as the menu had; otherwise the size of the container
        s.fromChest = !taggedRows.isEmpty();
        s.rows = taggedRows.size() == 1 && taggedRows.first() <= containerRows && taggedRows.first() >= lastUsedRow
                ? taggedRows.first() : containerRows;
        return s;
    }

    private String text(String path, String... pairs) {
        return dev.tmdevelopment.flarereferrals.util.Text.replace(lang().raw(path), pairs);
    }

    /** Applies what was found: decorations replaced, buttons of the chest moved, the other buttons stay. */
    private void apply(MenuLayout layout, Scan s) {
        layout.rows(s.rows);
        layout.decorations().clear();
        layout.decorations().addAll(s.decorations);
        for (Map.Entry<String, List<Integer>> en : s.buttons.entrySet()) layout.element(en.getKey()).slots = en.getValue();
        for (Map.Entry<String, MenuLayout.Decoration> en : s.looks.entrySet()) {
            // [button] on an item with its own name: material, name and lore of the item ({placeholders} work)
            MenuLayout.Element e = layout.element(en.getKey());
            MenuLayout.Decoration look = en.getValue();
            e.material = look.material;
            e.name = look.name;
            e.lore = look.lore;
            e.glow = look.glow ? Boolean.TRUE : null;
            e.customModelData = look.customModelData;
        }
        // [admin-only] on a button made by hand
        for (Map.Entry<String, Boolean> en : s.adminOnly.entrySet()) layout.element(en.getKey()).adminOnly = en.getValue();
        if (s.fromChest) {
            // the chest had the whole menu: a button taken out of it is taken out of the menu
            List<String> removed = new ArrayList<>();
            for (Map.Entry<String, MenuLayout.Element> en : layout.elements().entrySet()) {
                if (s.buttons.containsKey(en.getKey()) || en.getValue().slots.isEmpty()) continue;
                en.getValue().slots = new ArrayList<>();
                removed.add(en.getKey());
            }
            if (!removed.isEmpty()) s.warnings.add(text("builder.warn-removed", "%buttons%", String.join(", ", removed)));
        }
        // buttons drawn over decorations: the decoration is not seen there
        TreeSet<Integer> covered = new TreeSet<>();
        for (MenuLayout.Element e : layout.elements().values()) {
            for (MenuLayout.Decoration d : layout.decorations()) {
                for (int slot : e.slots) if (d.slots.contains(slot)) covered.add(slot);
            }
        }
        if (!covered.isEmpty()) {
            s.warnings.add(text("builder.warn-covered", "%amount%", String.valueOf(covered.size()),
                    "%slots%", MenuLayout.formatSlots(new ArrayList<>(covered))));
        }
        List<String> outside = new ArrayList<>();
        for (Map.Entry<String, MenuLayout.Element> en : layout.elements().entrySet()) {
            for (int slot : en.getValue().slots) {
                if (slot >= s.rows * 9) {
                    outside.add(en.getKey());
                    break;
                }
            }
        }
        if (!outside.isEmpty()) {
            s.warnings.add(text("builder.warn-outside", "%buttons%", String.join(", ", outside), "%rows%", String.valueOf(s.rows)));
        }
    }

    private void summary(Player p, MenuLayout layout, Scan s, String backup) {
        int buttons = 0;
        for (List<Integer> l : s.buttons.values()) buttons += l.size();
        lang().send(p, "builder.summary", "%rows%", String.valueOf(layout.rows()), "%items%", String.valueOf(s.items),
                "%actions%", String.valueOf(s.actions), "%buttons%", String.valueOf(buttons), "%file%", "menus/" + layout.id() + ".yml");
        if (backup != null) lang().send(p, "builder.backup", "%file%", backup);
        for (String w : s.warnings) dev.tmdevelopment.flarereferrals.util.Platform.send(p, dev.tmdevelopment.flarereferrals.util.Text.parse(w, p));
    }

    private void openEditor(Player p, String id) {
        LayoutMenu m = LayoutMenus.create(plugin, id, p, null);
        if (m != null) m.editMode().openLater();
    }

    // ------------------------------------------------------------------ add

    private void add(Player p, String name) {
        if (!LayoutManager.validName(name) || WORDS.contains(name)) {
            lang().send(p, "builder.bad-name");
            return;
        }
        if (LayoutManager.RESERVED.contains(name)) {
            lang().send(p, "builder.reserved", "%name%", name, "%label%", label);
            return;
        }
        if (plugin.layouts().isCustom(name) || plugin.layouts().fileExists(name)) {
            lang().send(p, "builder.exists", "%name%", name, "%label%", label);
            return;
        }
        String owner = commandOwner(name);
        if (owner != null) {
            lang().send(p, "builder.command-taken", "%name%", name, "%plugin%", owner);
            return;
        }
        Inventory inv = container(p);
        if (inv == null) return;
        if (isEmpty(inv)) {
            lang().send(p, "builder.empty");
            return;
        }
        MenuLayout layout = new MenuLayout(name);
        String title = customName(p);
        layout.title(title != null ? title : "<gradient:#ff5f6d:#ffc371>" + LayoutManager.prettyName(name) + "</gradient>");
        layout.openCommands().clear();
        layout.openCommands().add(name);
        layout.openPermission("");
        layout.enabled(true);
        // the buttons a menu of the chest builder can have: the ones of the main menu, plus back
        LayoutMenu sample = new dev.tmdevelopment.flarereferrals.gui.PlayerMenu(plugin, p, name, null);
        Scan s = scan(p, inv, sample);
        apply(layout, s);
        plugin.layouts().addCustom(layout);
        plugin.menuCommands().refresh();
        plugin.getLogger().info(p.getName() + " made the menu " + name + " with the chest builder (menus/" + name + ".yml).");
        dev.tmdevelopment.flarereferrals.util.ReferralLog.log("MENU", p.getName() + ": layout add " + name);
        lang().send(p, "builder.created", "%menu%", name, "%label%", label);
        summary(p, layout, s, null);
        openEditor(p, name);
    }

    /** Name of the plugin that already has /name, or null when it is free. */
    private String commandOwner(String name) {
        String menu = plugin.menuCommands().menuOf(name);
        if (menu != null) return "FlareReferrals (menus/" + menu + ".yml)";
        org.bukkit.command.CommandMap map = dev.tmdevelopment.flarereferrals.util.Compat.commandMap();
        Command c = map == null ? null : map.getCommand(name);
        if (c == null || MenuCommands.isMenuCommand(c)) return null; // free, or a menu command left by an old copy
        if (c instanceof PluginCommand) return ((PluginCommand) c).getPlugin().getName();
        return "the server";
    }

    private static boolean isEmpty(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) if (!ItemCodec.isAir(inv.getItem(i))) return false;
        return true;
    }

    // ------------------------------------------------------------------ import

    private void importTo(Player p, String id) {
        if (!plugin.layouts().exists(id)) {
            unknown(p, id);
            return;
        }
        Inventory inv = container(p);
        if (inv == null) return;
        if (isEmpty(inv)) {
            lang().send(p, "builder.empty");
            return;
        }
        LayoutMenu sample = LayoutMenus.create(plugin, id, p, null);
        if (sample == null) {
            unknown(p, id);
            return;
        }
        // editors of this menu open right now save first, then the chest replaces the menu
        plugin.layouts().closeEditors(id);
        MenuLayout layout = plugin.layouts().get(id);
        String backup = plugin.layouts().backup(id);
        if (backup != null) plugin.getLogger().info("menus/" + id + ".yml copied to backups/" + backup + " before the import of " + p.getName() + ".");
        Scan s = scan(p, inv, sample);
        apply(layout, s);
        String title = customName(p);
        if (title != null) layout.title(title);
        plugin.layouts().changed(id);
        plugin.layouts().save(id);
        plugin.menuCommands().refresh();
        dev.tmdevelopment.flarereferrals.util.ReferralLog.log("MENU", p.getName() + ": layout import " + id + (backup == null ? "" : " (old file: backups/" + backup + ")"));
        lang().send(p, "builder.imported", "%menu%", id, "%label%", label);
        summary(p, layout, s, backup);
        openEditor(p, id);
    }

    private void unknown(Player p, String id) {
        lang().send(p, "builder.unknown", "%menu%", id, "%menus%", String.join(", ", plugin.layouts().allIds()));
    }

    // ------------------------------------------------------------------ chest

    private void toChest(Player p, String id) {
        if (!plugin.layouts().exists(id)) {
            unknown(p, id);
            return;
        }
        Inventory inv = container(p);
        if (inv == null) return;
        if (!isEmpty(inv)) {
            lang().send(p, "builder.not-empty", "%menu%", id);
            return;
        }
        MenuLayout layout = plugin.layouts().get(id);
        int chestRows = Math.max(1, inv.getSize() / 9);
        if (layout.rows() > chestRows) {
            lang().send(p, "builder.too-small", "%menu%", id, "%rows%", String.valueOf(layout.rows()));
            return;
        }
        LayoutMenu sample = LayoutMenus.create(plugin, id, p, null);
        if (sample == null) {
            unknown(p, id);
            return;
        }
        Map<Integer, ItemStack> items = sample.chestItems();
        for (Map.Entry<Integer, ItemStack> en : items.entrySet()) {
            if (en.getKey() < inv.getSize()) inv.setItem(en.getKey(), en.getValue());
        }
        lang().send(p, "builder.exported", "%menu%", id, "%items%", String.valueOf(items.size()), "%label%", label);
        if (layout.rows() < chestRows) {
            lang().send(p, "builder.exported-rows", "%rows%", String.valueOf(layout.rows()), "%chest%", String.valueOf(chestRows));
        }
    }

    // ------------------------------------------------------------------ remove / list

    private void remove(Player p, String id, boolean confirmed) {
        if (LayoutManager.RESERVED.contains(id)) {
            lang().send(p, "builder.not-custom", "%menu%", id, "%label%", label);
            return;
        }
        if (!plugin.layouts().isCustom(id)) {
            unknown(p, id);
            return;
        }
        if (!confirmed) {
            lang().send(p, "builder.remove-confirm", "%menu%", id, "%label%", label);
            return;
        }
        String copy = plugin.layouts().removeCustom(id);
        if (copy == null) {
            lang().send(p, "builder.remove-failed", "%menu%", id);
            return;
        }
        plugin.menuCommands().refresh();
        plugin.getLogger().info(p.getName() + " deleted the menu " + id + " (copy in backups/" + copy + ").");
        dev.tmdevelopment.flarereferrals.util.ReferralLog.log("MENU", p.getName() + ": layout remove " + id + " (copy: backups/" + copy + ")");
        lang().send(p, "builder.removed", "%menu%", id, "%file%", copy);
    }

    private void list(Player p) {
        lang().send(p, "builder.list-header");
        for (String id : plugin.layouts().allIds()) {
            MenuLayout l = plugin.layouts().get(id);
            String type = plugin.layouts().isCustom(id)
                    ? text("builder.type-custom", "%commands%", l.openCommands().isEmpty() ? "-" : "/" + String.join(", /", l.openCommands()))
                    : lang().raw("builder.type-plugin");
            lang().send(p, "builder.list-line", "%label%", label, "%menu%", id, "%rows%", String.valueOf(l.rows()),
                    "%type%", type, "%state%", l.enabled() ? "" : lang().raw("builder.disabled"));
        }
    }
}
