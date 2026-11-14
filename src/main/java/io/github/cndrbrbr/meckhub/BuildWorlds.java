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
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Bauwelten (config: bauwelten): Wer {@code meckhub.bauen.<welt>} hat, baut im Kreativmodus.
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

    boolean isBuildWorld(World w) {
        return worlds().contains(w.getName().toLowerCase(Locale.ROOT));
    }

    /** Darf der Spieler in dieser Welt bauen? */
    static boolean canBuild(Player p, World w) {
        // Platzhalter selbst prüfen: Bukkit leitet "meckhub.bauen.*" nicht auf einzelne Welten ab.
        return p.hasPermission("meckhub.bauen.*")
                || p.hasPermission("meckhub.bauen." + w.getName().toLowerCase(Locale.ROOT));
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
        if (!viewer(e.getPlayer()) || e.getClickedBlock() == null) {
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
