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
    private final List<OpenCommand> registered = new ArrayList<>();

    public MenuCommands(FlareReferrals plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers the commands written in menus/*.yml now. A command that is still needed is kept and only pointed
     * to its menu again (removing and adding it back made Paper see it as taken by "another plugin");
     * the ones not needed any more are removed for real, so their name is free at once.
     */
    public void refresh() {
        CommandMap map = dev.tmdevelopment.flarereferrals.util.Compat.commandMap();
        java.util.Map<String, String[]> wanted = new java.util.LinkedHashMap<>();
        for (String id : plugin.layouts().allIds()) {
            MenuLayout layout = plugin.layouts().get(id);
            if (!layout.enabled()) continue; // menu turned off: its commands don't exist
            for (String raw : layout.openCommands()) {
                String name = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
                if (name.startsWith("/")) name = name.substring(1);
                if (name.isEmpty() || name.contains(" ") || name.contains(":")) continue;
                if (wanted.containsKey(name)) {
                    plugin.getLogger().warning("/" + name + " is in the open-commands of two menus (" + wanted.get(name)[0]
                            + " and " + id + "): it opens " + wanted.get(name)[0] + ".");
                    continue;
                }
                wanted.put(name, new String[]{id, layout.openPermission()});
            }
        }
        // not needed any more: removed
        for (java.util.Iterator<OpenCommand> it = registered.iterator(); it.hasNext(); ) {
            OpenCommand c = it.next();
            if (!wanted.containsKey(c.getName().toLowerCase(Locale.ROOT))) {
                dev.tmdevelopment.flarereferrals.util.Compat.removeKnown(c, "flarereferrals");
                it.remove();
            }
        }
        for (java.util.Map.Entry<String, String[]> en : wanted.entrySet()) {
            String name = en.getKey();
            OpenCommand cmd = null;
            for (OpenCommand c : registered) if (c.getName().equalsIgnoreCase(name)) cmd = c;
            if (cmd != null) {
                cmd.point(en.getValue()[0], en.getValue()[1]);
                continue;
            }
            cmd = new OpenCommand(name, en.getValue()[0], en.getValue()[1]);
            boolean own = map.register("flarereferrals", cmd);
            registered.add(cmd);
            if (!own) {
                plugin.getLogger().warning("/" + name + " (menus/" + en.getValue()[0] + ".yml) is already used by another plugin: "
                        + "use /flarereferrals:" + name + " or pick another name.");
            }
        }
        // runs on the next tick, when the server is fully started
        Bukkit.getScheduler().runTask(plugin, () -> {
            dev.tmdevelopment.flarereferrals.util.Compat.syncCommands();
            for (Player p : Bukkit.getOnlinePlayers()) p.updateCommands();
        });
    }

    /** The menu that opens with /name, or null. */
    public String menuOf(String name) {
        for (OpenCommand c : registered) {
            if (c.getName().equalsIgnoreCase(name)) return c.layoutId;
        }
        return null;
    }

    /** True for a command made by this class (also by an older copy of the plugin, after a plugin reload). */
    public static boolean isMenuCommand(Command c) {
        return c != null && c.getClass().getName().equals(OpenCommand.class.getName());
    }

    public void unregisterAll() {
        for (OpenCommand c : registered) dev.tmdevelopment.flarereferrals.util.Compat.removeKnown(c, "flarereferrals");
        registered.clear();
    }

    private final class OpenCommand extends Command {
        private String layoutId;
        private String permission;

        OpenCommand(String name, String layoutId, String permission) {
            super(name, "Opens the " + layoutId + " menu of FlareReferrals", "/" + name, new ArrayList<>());
            point(layoutId, permission);
        }

        /** The menu this command opens (changed by a reload without registering the command again). */
        void point(String layoutId, String permission) {
            this.layoutId = layoutId;
            this.permission = permission == null ? "" : permission.trim();
            setPermission(this.permission.isEmpty() ? null : this.permission);
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
            if (!allowed(p)) return true;
            LayoutMenu menu = LayoutMenus.create(plugin, layoutId, p, null);
            if (menu != null) menu.open();
            return true;
        }

        /** Menus of the chest builder: only enabled + open-permission. The others: player-access too. */
        private boolean allowed(Player p) {
            if (plugin.layouts().isCustom(layoutId)) return plugin.layouts().get(layoutId).enabled();
            return plugin.canOpen(p, accessKey());
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
            return allowed(p);
        }

        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
            return new ArrayList<>();
        }
    }
}
