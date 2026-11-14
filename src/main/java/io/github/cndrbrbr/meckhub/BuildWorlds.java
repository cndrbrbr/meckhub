package io.github.cndrbrbr.meckhub;

import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Bauwelten (config: bauwelten): Wer {@code meckhub.bauen.<welt>} hat, baut im Kreativmodus.
 * In der Lobby selbst gilt stattdessen {@code meckhub.lobby.bauen} (nur Admins).
 * Alle anderen schauen nur zu: Abenteuermodus, Fliegen erlaubt, nichts verändern.
 */
public final class BuildWorlds implements Listener {

    private final HubPlugin plugin;

    public BuildWorlds(HubPlugin plugin) {
        this.plugin = plugin;
    }

    List<String> worlds() {
        return plugin.getConfig().getStringList("bauwelten").stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }

    boolean isHub(World w) {
        return plugin.spawn() != null && w.equals(plugin.spawn().getWorld());
    }

    boolean isBuildWorld(World w) {
        return worlds().contains(w.getName().toLowerCase(Locale.ROOT));
    }

    /** Recht, in der Eingangswelt (Lobby) zu bauen – nur Admins, nicht Builder. */
    static final String LOBBY_PERMISSION = "meckhub.lobby.bauen";

    /** Darf der Spieler in dieser Welt bauen? In der Lobby nur mit {@link #LOBBY_PERMISSION}. */
    static boolean canBuild(Player p, World w, boolean hub) {
        if (hub) {
            return p.hasPermission(LOBBY_PERMISSION);
        }
        // Platzhalter selbst prüfen: Bukkit leitet "meckhub.bauen.*" nicht auf einzelne Welten ab.
        return p.hasPermission("meckhub.bauen.*")
                || p.hasPermission("meckhub.bauen." + w.getName().toLowerCase(Locale.ROOT));
    }

    boolean canBuild(Player p, World w) {
        return canBuild(p, w, isHub(w));
    }

    private boolean viewer(Player p) {
        return isBuildWorld(p.getWorld()) && !canBuild(p, p.getWorld());
    }

    /** Spielmodus setzen – einen Tick später, damit Multiverse uns nicht überschreibt. */
    void apply(Player p) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || !isBuildWorld(p.getWorld())) {
                return;
            }
            if (canBuild(p, p.getWorld())) {
                p.setGameMode(GameMode.CREATIVE);
            } else if (isHub(p.getWorld())) {
                return; // Lobby: Abenteuermodus setzt HubListener, ohne Fliegen
            } else {
                p.setGameMode(GameMode.ADVENTURE);
                p.setAllowFlight(true);
                p.sendMessage(ChatColor.GRAY + "Hier kannst du dich umsehen und fliegen (zweimal Springen). "
                        + "Bauen dürfen nur Builder. Zurück zur Lobby: " + ChatColor.WHITE + "/hub");
            }
        }, 2L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (isBuildWorld(e.getFrom()) && !isBuildWorld(p.getWorld()) && p.getGameMode() == GameMode.ADVENTURE) {
            p.setAllowFlight(false);
        }
        apply(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        apply(e.getPlayer());
    }

    // ── Zuschauer dürfen nichts verändern ──────────────────────────────────

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (viewer(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (viewer(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (viewer(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (viewer(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (viewer(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    /** Türen, Tore, Knöpfe und Hebel dürfen Zuschauer benutzen, sonst nichts (Truhen, Schilder, Rahmen …). */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        if (!viewer(e.getPlayer())) {
            return;
        }
        // Werkzeuge (z. B. WorldEdit-Pinsel) wirken nicht; den Kompass öffnet HubListener trotzdem.
        if (e.getItem() != null) {
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        }
        if (e.getClickedBlock() == null) {
            return;
        }
        Block b = e.getClickedBlock();
        Material m = b.getType();
        boolean allowed = Tag.DOORS.isTagged(m) || Tag.TRAPDOORS.isTagged(m) || Tag.FENCE_GATES.isTagged(m)
                || Tag.BUTTONS.isTagged(m) || m == Material.LEVER;
        if (e.getAction() == Action.PHYSICAL) {
            allowed = Tag.PRESSURE_PLATES.isTagged(m);
        }
        if (!allowed) {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            if (e.getAction() == Action.PHYSICAL) {
                e.setCancelled(true);
            }
        }
    }

    /** WorldEdit-Befehle sind für Zuschauer gesperrt (Builder haben worldedit.* auch in der Lobby). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!viewer(e.getPlayer())) {
            return;
        }
        String label = e.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (isWorldEdit(label)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "Hier darfst du nichts verändern.");
        }
    }

    /** Gehört der Befehl zu WorldEdit? Über die CommandMap, weil WorldEdit seine Befehle dynamisch anmeldet. */
    static boolean isWorldEdit(String label) {
        if (label.startsWith("/") || label.startsWith("worldedit:")) {
            return true; // //set, //wand … und Befehle mit Namensraum
        }
        try {
            Object map = Bukkit.getServer().getClass().getMethod("getCommandMap").invoke(Bukkit.getServer());
            Object cmd = map.getClass().getMethod("getCommand", String.class).invoke(map, label);
            return cmd instanceof PluginIdentifiableCommand pc && pc.getPlugin().getName().contains("WorldEdit");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntity(PlayerInteractEntityEvent e) {
        if (viewer(e.getPlayer()) && plugin.figures().gameOf(e.getRightClicked()).isEmpty()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (viewer(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && viewer(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHanging(HangingBreakByEntityEvent e) {
        if (e.getRemover() instanceof Player p && viewer(p)) {
            e.setCancelled(true);
        }
    }
}
