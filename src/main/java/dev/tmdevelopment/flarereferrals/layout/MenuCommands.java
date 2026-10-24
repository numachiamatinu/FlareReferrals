package dev.tmdevelopment.flarereferrals.layout;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Registers the "open-commands" of every menu file (e.g. /rewards opens the main menu). */
public final class MenuCommands {

    private final FlareReferrals plugin;
    private final List<Command> registered = new ArrayList<>();

    public MenuCommands(FlareReferrals plugin) {
        this.plugin = plugin;
    }

    /** Removes the old commands and registers the ones written in menus/*.yml now. */
    public void refresh() {
        CommandMap map = dev.tmdevelopment.flarereferrals.util.Compat.commandMap();
        unregisterAll();
        for (String id : LayoutManager.IDS) {
            MenuLayout layout = plugin.layouts().get(id);
            if (!layout.enabled()) continue; // menu turned off: its commands don't exist
            for (String raw : layout.openCommands()) {
                String name = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
                if (name.startsWith("/")) name = name.substring(1);
                if (name.isEmpty() || name.contains(" ") || name.contains(":")) continue;
                OpenCommand cmd = new OpenCommand(name, id, layout.openPermission());
                boolean own = map.register("flarereferrals", cmd);
                registered.add(cmd);
                if (!own) {
                    plugin.getLogger().warning("/" + name + " (menus/" + id + ".yml) is already used by another plugin: "
                            + "use /flarereferrals:" + name + " or pick another name.");
                }
            }
        }
        // runs on the next tick, when the server is fully started
        Bukkit.getScheduler().runTask(plugin, () -> {
            dev.tmdevelopment.flarereferrals.util.Compat.syncCommands();
            for (Player p : Bukkit.getOnlinePlayers()) p.updateCommands();
        });
    }

    public void unregisterAll() {
        for (Command c : registered) dev.tmdevelopment.flarereferrals.util.Compat.removeKnown(c);
        registered.clear();
    }

    private final class OpenCommand extends Command {
        private final String layoutId;
        private final String permission;

        OpenCommand(String name, String layoutId, String permission) {
            super(name, "Opens the " + layoutId + " menu of Flare Referrals", "/" + name, new ArrayList<>());
            this.layoutId = layoutId;
            this.permission = permission == null ? "" : permission.trim();
            if (!this.permission.isEmpty()) setPermission(this.permission);
        }

        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            if (!(sender instanceof Player)) {
                plugin.lang().send(sender, "general.players-only");
                return true;
            }
            Player p = (Player) sender;
            if (!permission.isEmpty() && !p.hasPermission(permission)) {
                plugin.lang().send(p, "general.no-permission");
                return true;
            }
            if (!plugin.canOpen(p, accessKey())) return true;
            LayoutMenu menu = LayoutMenus.create(plugin, layoutId, p, null);
            if (menu != null) menu.open();
            return true;
        }

        private String accessKey() {
            return ("main".equals(layoutId) ? "menu" : "top".equals(layoutId) ? "topmenu" : layoutId);
        }

        /**
         * Paper asks this to decide if the player "has" the command: false = the command is not sent to
         * the player at all (no tab completion, "Unknown command" like any command that doesn't exist).
         */
        @Override
        public boolean testPermissionSilent(@NotNull CommandSender target) {
            if (!(target instanceof Player)) return true;
            Player p = (Player) target;
            if (!permission.isEmpty() && !p.hasPermission(permission)) return false;
            return plugin.canOpen(p, accessKey());
        }

        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
            return new ArrayList<>();
        }
    }
}
