package dev.tmdevelopment.flarereferrals.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * e.getInventory() = the top inventory. (InventoryView is not used on purpose: it changed from class to
 * interface in 1.21, the same jar must work from 1.16.5 to the latest version.)
 */
public final class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) {
            Menu menu = (Menu) e.getInventory().getHolder();
            menu.handleClick(e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) {
            Menu menu = (Menu) e.getInventory().getHolder();
            menu.handleDrag(e);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) {
            Menu menu = (Menu) e.getInventory().getHolder();
            if (e.getPlayer() instanceof org.bukkit.entity.Player) Menu.closed((org.bukkit.entity.Player) e.getPlayer(), menu);
            menu.handleClose(e);
        }
    }
}
