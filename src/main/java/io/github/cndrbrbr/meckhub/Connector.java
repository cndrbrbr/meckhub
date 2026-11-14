package io.github.cndrbrbr.meckhub;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Schickt einen Spieler zum Ziel eines Spiels. */
public final class Connector {

    static final String CHANNEL = "BungeeCord";
    private static final long COOLDOWN_MS = 2000;

    private final Plugin plugin;
    private final Map<UUID, Long> last = new HashMap<>();

    public Connector(Plugin plugin) {
        this.plugin = plugin;
    }

    public void go(Player p, Game g) {
        long now = System.currentTimeMillis();
        Long before = last.get(p.getUniqueId());
        if (before != null && now - before < COOLDOWN_MS) {
            return;
        }
        last.put(p.getUniqueId(), now);
        String name = HubPlugin.color(g.color()) + ChatColor.BOLD + g.name() + ChatColor.RESET;
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
        switch (g.target().kind()) {
            case SERVER -> {
                p.sendMessage(ChatColor.GRAY + "Verbinde mit " + name + ChatColor.GRAY + " …");
                try {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    DataOutputStream out = new DataOutputStream(b);
                    out.writeUTF("Connect");
                    out.writeUTF(g.target().name());
                    p.sendPluginMessage(plugin, CHANNEL, b.toByteArray());
                } catch (IOException e) {
                    p.sendMessage(ChatColor.RED + "Verbindung fehlgeschlagen.");
                }
            }
            case TRANSFER -> {
                p.sendMessage(ChatColor.GRAY + "Du wirst zu " + name + ChatColor.GRAY + " weitergeleitet …");
                p.sendMessage(ChatColor.DARK_GRAY + "Klappt es nicht (ältere Minecraft-Version)? Adresse: "
                        + ChatColor.WHITE + g.target().address());
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline()) {
                        try {
                            p.transfer(g.target().host(), g.target().port());
                        } catch (RuntimeException e) {
                            p.sendMessage(ChatColor.RED + "Weiterleitung nicht möglich. Adresse: "
                                    + ChatColor.WHITE + g.target().address());
                        }
                    }
                }, 10L);
            }
            case WORLD -> {
                World w = Bukkit.getWorld(g.target().name());
                if (w == null) {
                    p.sendMessage(ChatColor.YELLOW + g.name() + " ist noch nicht eingerichtet.");
                    return;
                }
                p.teleport(w.getSpawnLocation().add(0.5, 0, 0.5));
                p.sendMessage(ChatColor.GRAY + "Willkommen in " + name + ChatColor.GRAY + "!");
            }
        }
    }

    public void forget(UUID player) {
        last.remove(player);
    }
}
