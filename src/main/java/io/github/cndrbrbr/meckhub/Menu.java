package io.github.cndrbrbr.meckhub;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Spielauswahl als Inventar-Menü (Kompass). */
public final class Menu implements Listener {

    private final HubPlugin plugin;

    /** Kennzeichnet unser Menü und merkt sich, welches Spiel in welchem Feld liegt. */
    static final class Holder implements InventoryHolder {
        final List<Game> slots = new ArrayList<>();
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public Menu(HubPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        List<Game> games = plugin.games();
        int rows = Math.max(1, (games.size() + 8) / 9);
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, rows * 9, ChatColor.DARK_GRAY + "Spiele");
        for (Game g : games) {
            ItemStack it = new ItemStack(g.icon());
            ItemMeta meta = it.getItemMeta();
            meta.setDisplayName(HubPlugin.color(g.color()) + "" + ChatColor.BOLD + g.name());
            List<String> lore = new ArrayList<>();
            if (g.description() != null && !g.description().isBlank()) {
                lore.add(ChatColor.GRAY + g.description());
            }
            lore.add(plugin.statusLine(g));
            lore.add("");
            lore.add(ChatColor.YELLOW + "▶ Klicken zum Spielen");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            it.setItemMeta(meta);
            h.inv.setItem(h.slots.size(), it);
            h.slots.add(g);
        }
        p.openInventory(h.inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot >= 0 && slot < h.slots.size() && e.getWhoClicked() instanceof Player p) {
            p.closeInventory();
            plugin.connector().go(p, h.slots.get(slot));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }
}
