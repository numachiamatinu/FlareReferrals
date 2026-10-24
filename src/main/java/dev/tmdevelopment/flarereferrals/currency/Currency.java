package dev.tmdevelopment.flarereferrals.currency;

import org.bukkit.Material;

public final class Currency {
    private final String id;
    private final Type type;
    private final String command;
    private final String name;
    private final String display;
    private final Material icon;
    public Currency(String id, Type type, String command, String name, String display, Material icon) {
        this.id = id;
        this.type = type;
        this.command = command;
        this.name = name;
        this.display = display;
        this.icon = icon;
    }
    public String id() { return id; }
    public Type type() { return type; }
    public String command() { return command; }
    public String name() { return name; }
    public String display() { return display; }
    public Material icon() { return icon; }

    public enum Type { VAULT, COMMAND, EXP, EXP_LEVELS }
}
