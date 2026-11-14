package io.github.cndrbrbr.meckhub;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Regeln in der Eingangswelt: Spawn, Abenteuermodus, kein Schaden, Leere → Spawn, Kompass. */
public final class HubListener implements Listener {

    private final HubPlugin plugin;
    private final NamespacedKey menuKey;

    public HubListener(HubPlugin plugin) {
        this.plugin = plugin;
        this.menuKey = new NamespacedKey(plugin, "menu");
    }

    private boolean inHub(Player p) {
        Location s = plugin.spawn();
        return s != null && p.getWorld().equals(s.getWorld());
    }

    private boolean bypass(Player p) {
        return p.hasPermission("meckhub.bypass");
    }

    ItemStack menuItem() {
        ItemStack it = new ItemStack(Material.COMPASS);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Spiele " + ChatColor.GRAY + "(Rechtsklick)");
        m.getPersistentDataContainer().set(menuKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    boolean isMenuItem(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(menuKey, PersistentDataType.BYTE);
    }

    /** Spieler für die Eingangswelt herrichten. */
    void prepare(Player p) {
        if (bypass(p)) {
            if (!p.getInventory().contains(Material.COMPASS)) {
                p.getInventory().addItem(menuItem());
            }
            return;
        }
        p.setGameMode(GameMode.ADVENTURE);
        if (plugin.getConfig().getBoolean("clear-inventory", true)) {
            p.getInventory().clear();
        }
        p.getInventory().setItem(plugin.getConfig().getInt("menu-slot", 4), menuItem());
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        p.setFireTicks(0);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Location s = plugin.spawn();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            if (s != null) {
                p.teleport(s);
            }
            if (inHub(p)) {
                prepare(p);
            }
            String title = plugin.getConfig().getString("welcome.title", "");
            if (!title.isEmpty()) {
                p.sendTitle(ChatColor.translateAlternateColorCodes('&', title),
                        ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("welcome.subtitle", "")),
                        10, 60, 20);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.connector().forget(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (inHub(e.getPlayer())) {
            prepare(e.getPlayer());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Location s = plugin.spawn();
        if (s != null && e.getPlayer().getWorld().equals(s.getWorld())) {
            e.setRespawnLocation(s);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && inHub(p)) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
                p.teleport(plugin.spawn());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && inHub(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null || e.getTo().getY() >= plugin.getConfig().getDouble("void-y", 0)) {
            return;
        }
        Player p = e.getPlayer();
        if (inHub(p)) {
            p.teleport(plugin.spawn());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent e) {
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)
                && isMenuItem(e.getItem())) {
            e.setCancelled(true);
            plugin.menu().open(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onFigureClick(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        plugin.figures().gameOf(e.getRightClicked()).ifPresent(g -> {
            e.setCancelled(true);
            plugin.connector().go(e.getPlayer(), g);
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onFigureHit(EntityDamageByEntityEvent e) {
        plugin.figures().gameOf(e.getEntity()).ifPresent(g -> {
            e.setCancelled(true);
            if (e.getDamager() instanceof Player p) {
                plugin.connector().go(p, g);
            }
        });
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isMenuItem(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventory(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && !bypass(p) && inHub(p)
                && (isMenuItem(e.getCurrentItem()) || isMenuItem(e.getCursor()))) {
            e.setCancelled(true);
        }
    }
}
