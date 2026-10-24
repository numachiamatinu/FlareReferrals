package dev.tmdevelopment.flarereferrals.input;

import dev.tmdevelopment.flarereferrals.FlareReferrals;
import dev.tmdevelopment.flarereferrals.util.Text;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Asks an admin to type a value in chat (used by the menus).
 * The prompt has a clickable [Cancel] button and, when editing, a clickable
 * "edit current text" that puts the old value straight into the chat bar.
 */
public final class ChatInput implements Listener {

    private static final class Prompt {
        private final Consumer<String> onInput;
        private final Runnable onCancel;
        private final long expiresAt;
        Prompt(Consumer<String> onInput, Runnable onCancel, long expiresAt) {
            this.onInput = onInput;
            this.onCancel = onCancel;
            this.expiresAt = expiresAt;
        }
        public Consumer<String> onInput() { return onInput; }
        public Runnable onCancel() { return onCancel; }
        public long expiresAt() { return expiresAt; }
    }

    private final FlareReferrals plugin;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    public ChatInput(FlareReferrals plugin) {
        this.plugin = plugin;
    }

    public void ask(Player p, String langPath, Consumer<String> onInput, Runnable onCancel, String... replacements) {
        ask(p, langPath, null, onInput, onCancel, replacements);
    }

    /**
     * @param suggestion current value: shown as a clickable line that fills the chat bar (may be null)
     */
    public void ask(Player p, String langPath, String suggestion, Consumer<String> onInput, Runnable onCancel,
                    String... replacements) {
        int timeout = plugin.getConfig().getInt("input.timeout-seconds", 60);
        prompts.put(p.getUniqueId(), new Prompt(onInput, onCancel, System.currentTimeMillis() + timeout * 1000L));
        p.closeInventory();

        String[] repl = new String[replacements.length + 4];
        System.arraycopy(replacements, 0, repl, 0, replacements.length);
        repl[replacements.length] = "%cancel%";
        repl[replacements.length + 1] = cancelWord();
        repl[replacements.length + 2] = "%seconds%";
        repl[replacements.length + 3] = String.valueOf(timeout);

        plugin.lang().send(p, "input.header", repl);
        plugin.lang().send(p, langPath, repl);
        if (suggestion != null && !suggestion.isEmpty()) {
            for (String line : plugin.lang().lines("input.suggestion")) {
                dev.tmdevelopment.flarereferrals.util.Platform.send(p, Text.parse(Text.replace(line, repl), p)
                        .clickEvent(ClickEvent.suggestCommand(suggestion)));
            }
        }
        plugin.lang().send(p, "input.cancel-hint", repl);

        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Prompt pr = prompts.get(id);
            if (pr != null && System.currentTimeMillis() >= pr.expiresAt()) {
                prompts.remove(id);
                Player online = Bukkit.getPlayer(id);
                if (online != null) {
                    plugin.lang().send(online, "input.expired");
                    if (pr.onCancel() != null) pr.onCancel().run();
                }
            }
        }, timeout * 20L + 5L);
    }

    /** Cancels the prompt of a player (clickable [Cancel] runs /referrals cancel). */
    public boolean cancel(Player p) {
        Prompt pr = prompts.remove(p.getUniqueId());
        if (pr == null) return false;
        plugin.lang().send(p, "input.cancelled");
        if (pr.onCancel() != null) pr.onCancel().run();
        return true;
    }

    public boolean isWaiting(Player p) {
        return prompts.containsKey(p.getUniqueId());
    }

    private String cancelWord() {
        return plugin.getConfig().getString("input.cancel-word", "cancel");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Prompt pr = prompts.remove(e.getPlayer().getUniqueId());
        if (pr == null) return;
        e.setCancelled(true);
        String msg = e.getMessage() == null ? "" : e.getMessage().trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (msg.equalsIgnoreCase(cancelWord())) {
                plugin.lang().send(p, "input.cancelled");
                if (pr.onCancel() != null) pr.onCancel().run();
                return;
            }
            pr.onInput().accept(msg);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        prompts.remove(e.getPlayer().getUniqueId());
    }
}
