package dev.tmdevelopment.flarereferrals.util;

import org.bukkit.configuration.ConfigurationSection;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Options added to a server file by a newer version get the explanation (comments) they have in the jar.
 * Bukkit keeps comments from 1.18.1: on older servers this does nothing.
 */
public final class Comments {

    private static Method get, set, getInline, setInline;
    private static boolean checked;

    private Comments() {}

    private static boolean ready() {
        if (!checked) {
            checked = true;
            try {
                get = ConfigurationSection.class.getMethod("getComments", String.class);
                set = ConfigurationSection.class.getMethod("setComments", String.class, List.class);
                getInline = ConfigurationSection.class.getMethod("getInlineComments", String.class);
                setInline = ConfigurationSection.class.getMethod("setInlineComments", String.class, List.class);
            } catch (Throwable t) {
                get = null;
            }
        }
        return get != null;
    }

    /** Writes the default value of {@code key} into {@code target}, with its comments and the ones of new parent sections. */
    public static void add(ConfigurationSection target, ConfigurationSection defaults, String key) {
        List<String> newParents = new ArrayList<>();
        int dot = key.indexOf('.');
        while (dot > 0) {
            String parent = key.substring(0, dot);
            if (!target.isSet(parent)) newParents.add(parent);
            dot = key.indexOf('.', dot + 1);
        }
        target.set(key, defaults.get(key));
        for (String p : newParents) copy(target, defaults, p);
        copy(target, defaults, key);
    }

    /** Copies the comments of one key from the defaults. */
    public static void copy(ConfigurationSection target, ConfigurationSection defaults, String key) {
        if (!ready()) return;
        try {
            Object c = get.invoke(defaults, key);
            if (c instanceof List && !((List<?>) c).isEmpty()) set.invoke(target, key, c);
            Object in = getInline.invoke(defaults, key);
            if (in instanceof List && !((List<?>) in).isEmpty()) setInline.invoke(target, key, in);
        } catch (Throwable ignored) {
            // comments are only a help: never stop the update for them
        }
    }
}
