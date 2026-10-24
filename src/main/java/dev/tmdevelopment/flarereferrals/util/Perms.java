package dev.tmdevelopment.flarereferrals.util;

import org.bukkit.permissions.Permissible;

/**
 * Permissions are flarereferrals.<node>. The old flaresrewards.<node> (before the rename) still works,
 * so groups set up with the old name keep their rights.
 */
public final class Perms {

    private Perms() {
    }

    public static boolean has(Permissible who, String node) {
        if (who == null) return false;
        if (who.hasPermission("flarereferrals." + node)) return true;
        String old = "flaresrewards." + node;
        return who.isPermissionSet(old) && who.hasPermission(old);
    }
}
