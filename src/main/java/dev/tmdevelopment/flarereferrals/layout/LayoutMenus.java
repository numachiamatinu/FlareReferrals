package dev.tmdevelopment.flarereferrals.layout;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.gui.Menu;
import dev.tmdevelopment.flarereferrals.gui.PlayerMenu;
import dev.tmdevelopment.flarereferrals.gui.PreviewMenu;
import dev.tmdevelopment.flarereferrals.gui.ReferralRewardsMenu;
import dev.tmdevelopment.flarereferrals.gui.TopMenu;
import org.bukkit.entity.Player;

/** Creates the customizable menus by layout id. */
public final class LayoutMenus {

    private LayoutMenus() {}

    public static LayoutMenu create(FlareReferrals plugin, String id, Player viewer, Menu parent) {
        return ("main".equals(id) ? new PlayerMenu(plugin, viewer) : "rewards".equals(id) ? new ReferralRewardsMenu(plugin, viewer, parent) : "top".equals(id) ? new TopMenu(plugin, viewer, parent) : "preview".equals(id) ? new PreviewMenu(plugin, viewer, plugin.rewards().invited(), parent) : null);
    }
}
