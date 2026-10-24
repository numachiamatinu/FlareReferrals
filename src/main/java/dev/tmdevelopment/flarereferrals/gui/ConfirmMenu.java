package dev.tmdevelopment.flarereferrals.gui;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class ConfirmMenu extends Menu {

    private final String question;
    private final Runnable onYes;
    private final Menu parent;

    public ConfirmMenu(FlareReferrals plugin, Player viewer, String question, Runnable onYes, Menu parent) {
        super(plugin, viewer);
        this.question = question;
        this.onYes = onYes;
        this.parent = parent;
    }

    @Override protected Component title() { return lang().title("gui.confirm.title", viewer); }
    @Override protected int rows() { return 3; }

    @Override
    protected void build() {
        set(11, button(Material.LIME_CONCRETE, "gui.confirm.accept", "%question%", question), e -> onYes.run());
        set(13, button(Material.PAPER, "gui.confirm.info", "%question%", question));
        set(15, button(Material.RED_CONCRETE, "gui.confirm.deny"), e -> {
            if (parent != null) parent.openLater();
            else viewer.closeInventory();
        });
        fillBorder();
        for (int i = 0; i < 27; i++) if (getInventory().getItem(i) == null) getInventory().setItem(i, filler());
    }
}
