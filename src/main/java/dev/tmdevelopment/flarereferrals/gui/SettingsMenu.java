package dev.tmdevelopment.flarereferrals.gui;

import dev.tmdevelopment.flarereferrals.util.Text;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /referrals settings - main admin menu. */
public final class SettingsMenu extends Menu {

    private static final class Toggle {
        private final int slot;
        private final String key;
        private final String id;
        private final Material icon;
        private final boolean def;
        Toggle(int slot, String key, String id, Material icon) {
            this(slot, key, id, icon, true);
        }
        Toggle(int slot, String key, String id, Material icon, boolean def) {
            this.slot = slot;
            this.key = key;
            this.id = id;
            this.icon = icon;
            this.def = def;
        }
        public int slot() { return slot; }
        public String key() { return key; }
        public String id() { return id; }
        public Material icon() { return icon; }
    }

    private static final Toggle[] TOGGLES = {
            new Toggle(10, "referral.enabled", "referral-enabled", Material.NAME_TAG),
            new Toggle(11, "referral.block-same-ip", "same-ip", Material.IRON_BARS),
            new Toggle(12, "referral.prevent-circular", "circular", Material.REPEATER),
            new Toggle(13, "referral.broadcast", "broadcast", Material.BELL),
            new Toggle(14, "milestones.enabled", "milestones-enabled", Material.EXPERIENCE_BOTTLE),
            new Toggle(15, "rewards.offline-queue", "offline-queue", Material.ENDER_CHEST),
            new Toggle(16, "rewards.drop-if-full", "drop-if-full", Material.HOPPER),
            new Toggle(20, "sounds.enabled", "sounds", Material.NOTE_BLOCK),
            new Toggle(22, "rewards.collect-menu", "collect-menu", Material.CHEST_MINECART),
            // off until the admin wants them
            new Toggle(19, "invite-hint.enabled", "invite-hint", Material.OAK_SIGN, false),
            new Toggle(25, "events.enabled", "events", Material.FIREWORK_ROCKET, false),
    };

    public SettingsMenu(FlareReferrals plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override protected Component title() { return lang().title("gui.settings.title", viewer); }
    @Override protected int rows() { return 6; }

    @Override
    protected void build() {
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        for (Toggle t : TOGGLES) {
            boolean on = cfg.getBoolean(t.key(), t.def);
            List<Component> lore = new ArrayList<>(lang().lore("gui.settings.options." + t.id() + ".lore", viewer));
            lore.addAll(lang().lore("gui.settings.toggle-lore", viewer, "%status%", status(on)));
            set(t.slot(), new ItemBuilder(t.icon())
                    .name(lang().item("gui.settings.options." + t.id() + ".name", viewer))
                    .lore(lore).glow(on).hideFlags().build(), e -> {
                cfg.set(t.key(), !on);
                plugin.saveConfig();
                if (t.key().startsWith("invite-hint.")) plugin.hints().reload();
                if (t.key().startsWith("events.")) plugin.events().reload();
                refresh();
            });
        }

        int minutes = cfg.getInt("referral.min-playtime-minutes", 0);
        set(21, button(Material.CLOCK, "gui.settings.playtime", "%value%", String.valueOf(minutes)), e -> {
            if (e.isShiftClick()) {
                plugin.chatInput().ask(viewer, "input.playtime", String.valueOf(minutes), text -> {
                    try {
                        cfg.set("referral.min-playtime-minutes", Math.max(0, Integer.parseInt(text)));
                        plugin.saveConfig();
                    } catch (NumberFormatException ex) {
                        lang().send(viewer, "input.not-a-number", "%input%", text);
                    }
                    open();
                }, this::open);
                return;
            }
            int v = minutes + (e.isLeftClick() ? 5 : -5);
            cfg.set("referral.min-playtime-minutes", Math.max(0, v));
            plugin.saveConfig();
            refresh();
        });

        number(23, Material.MAP, "referral.max-days-after-first-join", "gui.settings.max-days", "input.max-days", 1);
        number(24, Material.LANTERN, "referral.verify-after-minutes", "gui.settings.verify", "input.verify-minutes", 5);

        // storage / network: read-only here (changing it moves data: /referrals migrate), rewards on this server on/off
        if (plugin.data().network()) {
            boolean here = cfg.getBoolean("network.rewards-here", true);
            set(41, new ItemBuilder(Material.ENDER_PEARL)
                    .name(lang().item("gui.settings.storage-network.name", viewer))
                    .lore(lang().lore("gui.settings.storage-network.lore", viewer, "%storage%", plugin.data().type(),
                            "%server%", plugin.data().server(), "%rewards%", status(here)))
                    .glow(here).hideFlags().build(), e -> {
                cfg.set("network.rewards-here", !here);
                plugin.saveConfig();
                refresh();
            });
        } else {
            set(41, button(Material.ENDER_PEARL, "gui.settings.storage", "%storage%", plugin.data().type()));
        }

        set(30, button(Material.PLAYER_HEAD, "gui.settings.referral-rewards",
                        "%inviter%", status(plugin.rewards().inviter().enabled()),
                        "%invited%", status(plugin.rewards().invited().enabled())),
                e -> new ReferralRewardsMenu(plugin, viewer, this).openLater());
        set(31, button(Material.NETHER_STAR, "gui.settings.milestones",
                        "%amount%", String.valueOf(plugin.rewards().milestones().size())),
                e -> new MilestoneListMenu(plugin, viewer, this).openLater());
        set(32, button(Material.GOLD_INGOT, "gui.settings.currencies",
                "%amount%", String.valueOf(plugin.currencies().all().size())));
        set(33, button(Material.WRITABLE_BOOK, "gui.settings.messages"));
        set(42, button(Material.IRON_DOOR, "gui.settings.player-access"),
                e -> new PlayerAccessMenu(plugin, viewer, this).openLater());
        set(34, button(Material.PAINTING, "gui.settings.layouts"),
                e -> new dev.tmdevelopment.flarereferrals.layout.LayoutListMenu(plugin, viewer, this).openLater());
        set(40, button(Material.KNOWLEDGE_BOOK, "gui.settings.top"), e -> new TopMenu(plugin, viewer, this).openLater());

        set(48, button(Material.LIME_DYE, "gui.settings.reload"), e -> {
            boolean restart = plugin.reloadAll();
            lang().send(viewer, "admin.reloaded");
            if (restart) lang().send(viewer, "admin.restart-needed");
            openLater();
        });
        backButton(49, null);
        fillBorder();
    }

    /** Number option: left +step, right -step, shift = type it. 0 = off. */
    private void number(int slot, Material icon, String key, String text, String prompt, int step) {
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        int value = cfg.getInt(key, 0);
        set(slot, new ItemBuilder(icon)
                .name(lang().item(text + ".name", viewer))
                .lore(lang().lore(text + ".lore", viewer, "%current%", value <= 0 ? lang().raw(text + ".current-off")
                        : Text.replace(lang().raw(text + ".current"), "%value%", String.valueOf(value)), "%value%", String.valueOf(value)))
                .glow(value > 0).hideFlags().build(), e -> {
            if (e.isShiftClick()) {
                plugin.chatInput().ask(viewer, prompt, String.valueOf(value), t -> {
                    try {
                        cfg.set(key, Math.max(0, Integer.parseInt(t.trim())));
                        plugin.saveConfig();
                    } catch (NumberFormatException ex) {
                        lang().send(viewer, "input.not-a-number", "%input%", t);
                    }
                    open();
                }, this::open);
                return;
            }
            cfg.set(key, Math.max(0, value + (e.isLeftClick() ? step : -step)));
            plugin.saveConfig();
            refresh();
        });
    }
}
