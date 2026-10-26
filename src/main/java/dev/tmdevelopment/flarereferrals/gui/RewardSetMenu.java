package dev.tmdevelopment.flarereferrals.gui;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.reward.Display;
import dev.tmdevelopment.flarereferrals.reward.Milestone;
import dev.tmdevelopment.flarereferrals.util.Heads;
import dev.tmdevelopment.flarereferrals.reward.RewardSet;
import dev.tmdevelopment.flarereferrals.util.ItemBuilder;
import dev.tmdevelopment.flarereferrals.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Edits one reward set: referral inviter, referral invited or a milestone.
 * Every button shows a short preview of its content, so you see everything without opening it.
 */
public final class RewardSetMenu extends Menu {

    private static final int PREVIEW = 4;

    private final RewardSet set;
    private final Menu parent;

    public RewardSetMenu(FlareReferrals plugin, Player viewer, RewardSet set, Menu parent) {
        super(plugin, viewer);
        this.set = set;
        this.parent = parent;
    }

    public static String displayName(FlareReferrals plugin, RewardSet set) {
        if (set instanceof Milestone) { Milestone m = (Milestone) set; return m.name(); }
        if (set == plugin.rewards().inviter()) return plugin.lang().raw("gui.names.inviter");
        if (set == plugin.rewards().invited()) return plugin.lang().raw("gui.names.invited");
        return set.id();
    }

    @Override
    protected Component title() {
        return lang().title("gui.rewardset.title", viewer, "%name%", displayName(plugin, set));
    }

    @Override protected int rows() { return 5; }

    private void save() {
        plugin.rewards().save();
    }

    // ---------------------------------------------------------------- previews

    private List<Component> itemsPreview() {
        List<Component> out = new ArrayList<>();
        int shown = 0;
        for (ItemStack it : set.items()) {
            if (shown++ >= PREVIEW) break;
            Component line = Text.item(Text.replace(lang().raw("gui.rewardset.preview-line"), "%text%", it.getAmount() + "x "), viewer);
            org.bukkit.inventory.meta.ItemMeta meta = it.hasItemMeta() ? it.getItemMeta() : null;
            if (meta != null && meta.hasDisplayName()) {
                line = line.append(Text.fromLegacy(meta.getDisplayName()));
            } else {
                line = line.append(Text.item("&f" + it.getType().name().toLowerCase().replace('_', ' '), viewer));
            }
            out.add(line);
        }
        more(out, set.items().size());
        return out;
    }

    private List<Component> currenciesPreview() {
        List<Component> out = new ArrayList<>();
        for (Map.Entry<String, Double> en : set.currencies().entrySet()) {
            out.add(Text.item(Text.replace(lang().raw("gui.rewardset.preview-line"), "%text%", plugin.currencies().display(en.getKey(), en.getValue())), viewer));
        }
        if (out.isEmpty()) out.add(Text.item(lang().raw("gui.rewardset.preview-empty"), viewer));
        return out;
    }

    private List<Component> linesPreview(List<String> lines, boolean command) {
        List<Component> out = new ArrayList<>();
        int shown = 0;
        for (String l : lines) {
            if (shown++ >= PREVIEW) break;
            List<Component> raw = Text.raw((command ? "/" : "") + (l.length() > 40 ? l.substring(0, 40) + "..." : l), 60);
            out.addAll(raw);
        }
        more(out, lines.size());
        return out;
    }

    private void more(List<Component> out, int total) {
        if (total == 0) out.add(Text.item(lang().raw("gui.rewardset.preview-empty"), viewer));
        else if (total > PREVIEW) out.add(Text.item(Text.replace(lang().raw("gui.rewardset.preview-more"), "%amount%", String.valueOf(total - PREVIEW)), viewer));
    }

    private ItemStack withPreview(ItemStack base, String langPath, List<Component> preview, String... repl) {
        List<Component> lore = new ArrayList<>(lang().lore(langPath + ".lore", viewer, repl));
        lore.addAll(preview);
        lore.addAll(lang().lore("gui.rewardset.click-to-edit", viewer));
        return new ItemBuilder(base).name(lang().item(langPath + ".name", viewer, repl)).lore(lore).hideFlags().build();
    }

    // ---------------------------------------------------------------- build

    @Override
    protected void build() {
        boolean milestone = set instanceof Milestone;

        // header: what it is + on/off (click to toggle)
        ItemStack head = milestone ? ((Milestone) set).icon() : new ItemStack(set == plugin.rewards().inviter()
                ? Material.PLAYER_HEAD : Material.EMERALD);
        set(4, new ItemBuilder(head)
                .name(Text.item(displayName(plugin, set), viewer))
                .lore(lang().lore("gui.rewardset.header.lore", viewer,
                        "%status%", status(set.enabled()),
                        "%type%", lang().raw(milestone ? "gui.rewardset.header.type-milestone"
                                : set == plugin.rewards().inviter() ? "gui.rewardset.header.type-inviter"
                                : "gui.rewardset.header.type-invited")))
                .glow(set.enabled()).hideFlags().build(), e -> {
            set.enabled(!set.enabled());
            save();
            if (set.enabled()) plugin.referrals().rewardTurnedOn(set);
            refresh();
        });

        // rewards row
        set(20, withPreview(new ItemStack(Material.CHEST), "gui.rewardset.items", itemsPreview(),
                        "%amount%", String.valueOf(set.items().size())),
                e -> new ItemsMenu(plugin, viewer, set, this).openLater());
        set(21, withPreview(new ItemStack(Material.GOLD_INGOT), "gui.rewardset.currencies", currenciesPreview()),
                e -> new CurrencyMenu(plugin, viewer, set, this).openLater());
        set(22, withPreview(new ItemStack(Material.COMMAND_BLOCK), "gui.rewardset.commands",
                        linesPreview(set.commands(), true), "%amount%", String.valueOf(set.commands().size())),
                e -> new StringListMenu(plugin, viewer, set.commands(), "commands", this::save, this).openLater());
        set(23, withPreview(new ItemStack(Material.PAPER), "gui.rewardset.messages",
                        linesPreview(set.messages(), false), "%amount%", String.valueOf(set.messages().size())),
                e -> new StringListMenu(plugin, viewer, set.messages(), "messages", this::save, this).openLater());
        set(24, withPreview(new ItemStack(Material.BELL), "gui.rewardset.broadcast",
                        linesPreview(set.broadcast(), false), "%amount%", String.valueOf(set.broadcast().size())),
                e -> new StringListMenu(plugin, viewer, set.broadcast(), "broadcast", this::save, this).openLater());

        if (set instanceof Milestone) {
            Milestone m = (Milestone) set;
            set(10, button(m.repeat() ? Material.CLOCK : Material.FIREWORK_STAR, "gui.rewardset.repeat",
                    "%status%", status(m.repeat()), "%amount%", String.valueOf(m.amount())), e -> {
                m.repeat(!m.repeat());
                save();
                refresh();
            });
            set(11, button(Material.OAK_SIGN, "gui.rewardset.amount", "%amount%", String.valueOf(m.amount())), e ->
                    plugin.chatInput().ask(viewer, "input.milestone-amount", String.valueOf(m.amount()), text -> {
                        try {
                            int v = Integer.parseInt(text.trim());
                            if (!plugin.rewards().changeAmount(m, v)) {
                                lang().send(viewer, "admin.milestone-exists", "%amount%", text);
                            }
                        } catch (NumberFormatException ex) {
                            lang().send(viewer, "input.not-a-number", "%input%", text);
                        }
                        open();
                    }, this::open));
            set(15, button(Material.NAME_TAG, "gui.rewardset.rename", "%name%", m.name()), e ->
                    plugin.chatInput().ask(viewer, "input.milestone-name", m.name(), text -> {
                        m.name(text);
                        save();
                        open();
                    }, this::open));
            set(16, button(m.icon(), "gui.rewardset.icon"), e -> {
                if (e.isRightClick()) {
                    plugin.chatInput().ask(viewer, "input.head", text -> Heads.create(plugin, text, skull -> {
                        if (skull == null) lang().send(viewer, "admin.head-invalid", "%input%", text);
                        else {
                            m.icon(skull);
                            save();
                        }
                        open();
                    }), this::open);
                    return;
                }
                ItemStack hand = viewer.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    lang().send(viewer, "admin.hold-item");
                    return;
                }
                m.icon(hand);
                save();
                refresh();
            });
            set(44, button(Material.BARRIER, "gui.rewardset.delete", "%amount%", String.valueOf(m.amount())),
                    e -> new ConfirmMenu(plugin, viewer, MilestoneListMenu.deleteQuestion(plugin, m.amount()), () -> {
                        plugin.rewards().delete(m.amount());
                        lang().send(viewer, "admin.milestone-deleted", "%amount%", String.valueOf(m.amount()));
                        (parent != null ? parent : new MilestoneListMenu(plugin, viewer, null)).openLater();
                    }, this).openLater());
        }

        Display d = set.display();
        List<Component> dsp = new ArrayList<>();
        dsp.add(Text.item(Text.replace(lang().raw("gui.rewardset.preview-line"), "%text%",
                lang().raw("gui.rewardset.screen.title-label") + " " + status(d.hasTitle())), viewer));
        dsp.add(Text.item(Text.replace(lang().raw("gui.rewardset.preview-line"), "%text%",
                lang().raw("gui.rewardset.screen.actionbar-label") + " " + status(d.hasActionbar())), viewer));
        dsp.add(Text.item(Text.replace(lang().raw("gui.rewardset.preview-line"), "%text%",
                lang().raw("gui.rewardset.screen.bossbar-label") + " " + status(d.hasBossbar())), viewer));
        set(31, withPreview(new ItemStack(Material.ENDER_EYE), "gui.rewardset.screen", dsp),
                e -> new DisplayMenu(plugin, viewer, set, this).openLater());

        // where the reward goes: default (config.yml rewards.collect-menu) -> menu -> auto
        String dl = set.delivery();
        String effective = plugin.referrals().inMenu(set) ? lang().raw("gui.rewardset.delivery.menu") : lang().raw("gui.rewardset.delivery.auto");
        set(30, button(Material.CHEST_MINECART, "gui.rewardset.delivery",
                "%mode%", dl.equals("default") ? Text.replace(lang().raw("gui.rewardset.delivery.default"), "%mode%", effective) : effective), e -> {
            set.delivery(dl.equals("default") ? "menu" : dl.equals("menu") ? "auto" : "default");
            save();
            refresh();
        });

        if (plugin.data().network()) {
            set(32, button(Material.COMPASS, "gui.rewardset.servers", "%servers%", String.join(", ", set.servers())), e ->
                    plugin.chatInput().ask(viewer, "input.servers", String.join(", ", set.servers()), text -> {
                        set.servers(java.util.Arrays.asList(text.split("[,\\s]+")));
                        save();
                        open();
                    }, this::open));
        }

        set(36, button(Material.ENDER_EYE, "gui.rewardset.preview"),
                e -> new PreviewMenu(plugin, viewer, set, this).openLater());
        backButton(40, parent);
        fillBorder();
    }
}
