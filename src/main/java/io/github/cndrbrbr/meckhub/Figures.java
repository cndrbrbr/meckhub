package io.github.cndrbrbr.meckhub;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.profile.PlayerProfile;

/**
 * Spielfiguren (Mannequins) mit schwebendem Schild. Nicht gespeichert, sondern beim Laden
 * des Chunks erzeugt; an einem Tag erkennbar, damit Reste nach einem Absturz verschwinden.
 */
public final class Figures implements Listener {

    static final String TAG = "meckhub_figure";

    private final HubPlugin plugin;
    private final Map<String, Mannequin> figures = new HashMap<>();
    private final Map<String, TextDisplay> labels = new HashMap<>();
    private final Map<String, PlayerProfile> skins = new HashMap<>();

    public Figures(HubPlugin plugin) {
        this.plugin = plugin;
    }

    /** Welches Spiel gehört zu dieser Entität? */
    public Optional<Game> gameOf(Entity e) {
        for (var en : figures.entrySet()) {
            if (en.getValue().equals(e)) {
                return plugin.game(en.getKey());
            }
        }
        return Optional.empty();
    }

    /** Alle Figuren anlegen bzw. an ihren Platz setzen (Start, reload, /hub figur). */
    public void refreshAll() {
        removeAll();
        for (Game g : plugin.games()) {
            loadSkin(g);
            spawn(g);
        }
    }

    public void removeAll() {
        figures.values().forEach(Entity::remove);
        labels.values().forEach(Entity::remove);
        figures.clear();
        labels.clear();
    }

    /** Alle 5 s: fehlende Figuren nachziehen, Schilder aktualisieren. */
    public void tick() {
        for (Game g : plugin.games()) {
            Mannequin m = figures.get(g.id());
            if (m == null || !m.isValid()) {
                spawn(g);
            }
            TextDisplay t = labels.get(g.id());
            if (t != null && t.isValid()) {
                t.setText(label(g));
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        Chunk c = e.getChunk();
        for (Entity en : c.getEntities()) {
            if (en.getScoreboardTags().contains(TAG) && !figures.containsValue(en) && !labels.containsValue(en)) {
                en.remove();
            }
        }
        for (Game g : plugin.games()) {
            Location l = plugin.figureLocation(g.id());
            if (l != null && l.getWorld().equals(c.getWorld()) && l.getBlockX() >> 4 == c.getX()
                    && l.getBlockZ() >> 4 == c.getZ()) {
                spawn(g);
            }
        }
    }

    private void spawn(Game g) {
        Location l = plugin.figureLocation(g.id());
        if (l == null || !l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) {
            return;
        }
        Mannequin old = figures.remove(g.id());
        if (old != null) {
            old.remove();
        }
        TextDisplay oldLabel = labels.remove(g.id());
        if (oldLabel != null) {
            oldLabel.remove();
        }
        Mannequin m = l.getWorld().spawn(l, Mannequin.class, e -> {
            e.setPersistent(false);
            e.addScoreboardTag(TAG);
            e.setImmovable(true);
            e.setInvulnerable(true);
            e.setSilent(true);
            e.setGravity(false);
            e.setCollidable(false);
            e.setRemoveWhenFarAway(false);
            e.setHideDescription(true);
            e.setCustomNameVisible(false);
            e.setRotation(l.getYaw(), l.getPitch());
            PlayerProfile skin = skins.get(g.id());
            if (skin != null) {
                e.setPlayerProfile(skin);
            }
            for (var en : g.gear().entrySet()) {
                item(en.getValue()).ifPresent(it -> e.getEquipment().setItem(en.getKey(), it));
            }
        });
        figures.put(g.id(), m);
        Location top = l.clone().add(0, 2.35, 0);
        TextDisplay t = l.getWorld().spawn(top, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
            d.setText(label(g));
        });
        labels.put(g.id(), t);
    }

    private String label(Game g) {
        StringBuilder sb = new StringBuilder();
        sb.append(HubPlugin.color(g.color())).append(ChatColor.BOLD).append(g.name()).append(ChatColor.RESET);
        if (g.description() != null && !g.description().isBlank()) {
            sb.append('\n').append(ChatColor.GRAY).append(g.description());
        }
        sb.append('\n').append(plugin.statusLine(g));
        sb.append('\n').append(ChatColor.YELLOW).append("▶ Klicken zum Spielen");
        return sb.toString();
    }

    /** Skin: "default:kai" (eingebauter Skin) oder "player:Name" (Skin eines Minecraft-Kontos). */
    private void loadSkin(Game g) {
        String spec = g.skin() == null ? "" : g.skin().trim();
        String shortName = ("hub_" + g.id()).replaceAll("[^A-Za-z0-9_]", "");
        shortName = shortName.substring(0, Math.min(16, shortName.length()));
        if (spec.toLowerCase(Locale.ROOT).startsWith("default:")) {
            String n = shortName;
            DefaultSkins.uuidFor(spec.substring(8), g.id().hashCode())
                    .ifPresentOrElse(u -> skins.put(g.id(), Bukkit.createPlayerProfile(u, n)),
                            () -> plugin.getLogger().warning("Unbekannter Standardskin bei " + g.id() + ": " + spec));
        } else if (spec.toLowerCase(Locale.ROOT).startsWith("player:")) {
            String player = spec.substring(7);
            if (skins.containsKey(g.id())) {
                return; // schon geladen
            }
            Bukkit.createPlayerProfile(null, player).update().whenComplete((prof, err) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (err != null || prof == null || !prof.isComplete()) {
                            plugin.getLogger().warning("Skin von " + player + " nicht ladbar (" + err + ").");
                            return;
                        }
                        skins.put(g.id(), prof);
                        Mannequin m = figures.get(g.id());
                        if (m != null && m.isValid()) {
                            m.setPlayerProfile(prof);
                        }
                    }));
        }
    }

    static Optional<ItemStack> item(Gear gear) {
        Material m = Material.matchMaterial(gear.material());
        if (m == null || !m.isItem()) {
            return Optional.empty();
        }
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (gear.color() != null && meta instanceof LeatherArmorMeta lam) {
            lam.setColor(Color.fromRGB(gear.color()));
        }
        if (gear.trimMaterial() != null && meta instanceof ArmorMeta am) {
            TrimMaterial tm = Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft(gear.trimMaterial()));
            TrimPattern tp = Registry.TRIM_PATTERN.get(NamespacedKey.minecraft(gear.trimPattern()));
            if (tm != null && tp != null) {
                am.setTrim(new ArmorTrim(tm, tp));
            }
        }
        if (meta != null) {
            it.setItemMeta(meta);
        }
        return Optional.of(it);
    }

    static EquipmentSlot slot(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "kopf", "head" -> EquipmentSlot.HEAD;
            case "brust", "chest" -> EquipmentSlot.CHEST;
            case "beine", "legs" -> EquipmentSlot.LEGS;
            case "fuesse", "feet" -> EquipmentSlot.FEET;
            case "hand", "mainhand" -> EquipmentSlot.HAND;
            case "nebenhand", "offhand" -> EquipmentSlot.OFF_HAND;
            default -> null;
        };
    }
}
