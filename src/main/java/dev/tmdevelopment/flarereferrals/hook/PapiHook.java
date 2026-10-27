package dev.tmdevelopment.flarereferrals.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/** Only loaded when PlaceholderAPI is installed. */
public final class PapiHook {
    private PapiHook() {}

    public static String apply(Player player, String text) {
        try {
            return PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable t) {
            return text;
        }
    }

    /** Placeholders of an offline player (top lines, holograms...). */
    public static String apply(org.bukkit.OfflinePlayer player, String text) {
        try {
            return PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable t) {
            return text;
        }
    }
}
