package bau;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

/** Rohbau der neuen Eingangsinsel in SpawnHub (einmalig auf einer Kopie). */
public final class Bau extends JavaPlugin {
    static final int CX = -15, CZ = 0, F = 62, R = 24;
    static final int BRIDGE_END = -65;
    World w;

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            w = Bukkit.getWorlds().get(0);
            island();
            bridge();
            podests();
            labels();
            getLogger().info("Rohbau fertig.");
            Bukkit.shutdown();
        }, 20L);
    }

    void set(int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }

    static double noise(int x, int z) { // fester Pseudo-Zufall je Spalte
        long h = x * 73856093L ^ z * 19349663L;
        h = (h ^ (h >>> 13)) * 0x5bd1e995L;
        return ((h ^ (h >>> 15)) & 0xffff) / 65535.0;
    }

    void island() {
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double r = Math.hypot(dx, dz);
                if (r > R + 0.4) continue;
                int x = CX + dx, z = CZ + dz;
                double ang = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = Norden
                double n = noise(x, z);
                Material floor;
                if (r > R - 1.6) {
                    floor = Material.POLISHED_DEEPSLATE;
                } else if (r > R - 5.5) {
                    floor = Material.GRASS_BLOCK;
                } else if (r < 2.6) {
                    floor = r < 1 ? Material.SEA_LANTERN : Material.QUARTZ_BRICKS;
                } else {
                    double spoke = Math.abs(((ang % 45) + 45) % 45 - 22.5); // Abstand zur nächsten Speiche in Grad
                    boolean onSpoke = (22.5 - spoke) * Math.PI / 180 * r < 0.7;
                    if ((r >= 7.5 && r < 8.5) || (r >= 15.5 && r < 16.5)) floor = Material.POLISHED_ANDESITE;
                    else if (onSpoke && r < 15.5) floor = Material.POLISHED_DIORITE;
                    else floor = Material.SMOOTH_STONE;
                }
                set(x, F, z, floor);
                for (int y = F + 1; y <= F + 4; y++) set(x, y, z, Material.AIR);
                // Rand: Mauer mit Laternen, Öffnung für die Brücke im Norden
                if (r > R - 1.6 && !(dz < 0 && Math.abs(dx) <= 2)) {
                    set(x, F + 1, z, Material.DEEPSLATE_BRICK_WALL);
                    if (Math.round(ang) % 30 == 0 && r > R - 1.0) {
                        Block b = w.getBlockAt(x, F + 2, z);
                        b.setType(Material.LANTERN, false);
                    }
                }
                if (floor == Material.GRASS_BLOCK) {
                    if (n < 0.28) set(x, F + 1, z, Material.SHORT_GRASS);
                    else if (n < 0.32) set(x, F + 1, z, Material.CORNFLOWER);
                    else if (n < 0.36) set(x, F + 1, z, Material.OXEYE_DAISY);
                    else if (n < 0.39) set(x, F + 1, z, Material.ALLIUM);
                    else if (n > 0.985) set(x, F + 1, z, Material.FLOWERING_AZALEA);
                }
                // Felskörper darunter, nach unten spitz zulaufend
                int depth = (int) Math.round(3 + 13 * Math.pow(1 - r / (R + 0.5), 0.8) + n * 2);
                for (int d = 1; d <= depth; d++) {
                    Material m = d == 1 ? Material.DIRT
                            : d <= 4 ? (n < 0.5 ? Material.STONE : Material.ANDESITE)
                            : d <= 9 ? (n < 0.6 ? Material.STONE : Material.TUFF)
                            : (n < 0.5 ? Material.DEEPSLATE : Material.COBBLED_DEEPSLATE);
                    set(x, F - d, z, m);
                }
            }
        }
    }

    void bridge() {
        for (int z = CZ - R + 1; z >= BRIDGE_END; z--) {
            for (int x = CX - 2; x <= CX + 2; x++) {
                set(x, F, z, Math.abs(x - CX) == 2 ? Material.DEEPSLATE_BRICKS
                        : x == CX ? Material.SMOOTH_STONE : Material.POLISHED_ANDESITE);
                for (int y = F + 1; y <= F + 3; y++) set(x, y, z, Material.AIR);
                Block under = w.getBlockAt(x, F - 1, z);
                under.setType(Material.DEEPSLATE_BRICK_SLAB, false);
                Slab s = (Slab) under.getBlockData();
                s.setType(Slab.Type.TOP);
                under.setBlockData(s, false);
            }
            if (z < CZ - R + 2) { // Geländer erst außerhalb der Insel
                set(CX - 3, F, z, Material.DEEPSLATE_BRICKS);
                set(CX + 3, F, z, Material.DEEPSLATE_BRICKS);
                set(CX - 3, F + 1, z, Material.DEEPSLATE_BRICK_WALL);
                set(CX + 3, F + 1, z, Material.DEEPSLATE_BRICK_WALL);
                if ((z - BRIDGE_END) % 8 == 4) {
                    set(CX - 3, F + 2, z, Material.LANTERN);
                    set(CX + 3, F + 2, z, Material.LANTERN);
                }
            }
        }
    }

    static final String[] GAMES = {"mecktown", "buildcontest", "onyxcraft", "inigresso", "softsurvival", "javascript", "schulcampus"};
    static final Material[] CORE = {Material.BROWN_CONCRETE, Material.ORANGE_CONCRETE, Material.PURPLE_CONCRETE,
            Material.LIME_CONCRETE, Material.GREEN_CONCRETE, Material.YELLOW_CONCRETE, Material.LIGHT_BLUE_CONCRETE};

    void podests() {
        StringBuilder orte = new StringBuilder();
        orte.append("spawn:\n  ==: org.bukkit.Location\n  world: ").append(w.getName())
                .append("\n  x: ").append(CX + 0.5).append("\n  y: ").append(F + 1.0).append("\n  z: ").append(CZ + 0.5)
                .append("\n  pitch: 0.0\n  yaw: 0.0\nfiguren:\n");
        for (int i = 0; i < GAMES.length; i++) {
            double a = Math.toRadians(90 + 30 * i); // Osten → Süden → Westen
            int px = CX + (int) Math.round(Math.sin(a) * 11);
            int pz = CZ + (int) Math.round(-Math.cos(a) * 11);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    set(px + dx, F + 1, pz + dz, dx == 0 && dz == 0 ? CORE[i] : Material.POLISHED_DEEPSLATE);
            set(px, F, pz, Material.SEA_LANTERN);
            double vx = CX + 0.5 - (px + 0.5), vz = CZ + 0.5 - (pz + 0.5);
            double yaw = Math.round(Math.toDegrees(Math.atan2(-vx, vz)) / 15) * 15.0;
            orte.append("  ").append(GAMES[i]).append(":\n    ==: org.bukkit.Location\n    world: ").append(w.getName())
                    .append("\n    x: ").append(px + 0.5).append("\n    y: ").append(F + 2.0).append("\n    z: ").append(pz + 0.5)
                    .append("\n    pitch: 0.0\n    yaw: ").append(yaw).append('\n');
        }
        try {
            java.nio.file.Files.writeString(new java.io.File(getDataFolder().getParentFile(), "orte-neu.yml").toPath(), orte);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    void labels() {
        text(new Location(w, CX + 0.5, F + 3.2, CZ - R + 0.5), "§6§l↑ Alte Lobby\n§7über die Brücke");
    }

    void text(Location l, String s) {
        w.spawn(l, TextDisplay.class, d -> {
            d.setText(s);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setPersistent(true);
            d.addScoreboardTag("meckhub_deko");
        });
    }
}
