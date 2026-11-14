package mapshot;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import javax.imageio.ImageIO;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

/** Zeichnet Draufsichten aus plugins/mapshot.txt: "name x0 z0 x1 z1 step" je Zeile, dann Stopp. */
public final class Mapshot extends JavaPlugin {
    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, this::run, 20L);
    }

    private void run() {
        try {
            World w = Bukkit.getWorlds().get(0);
            for (String line : Files.readAllLines(new File(getDataFolder().getParentFile(), "mapshot.txt").toPath())) {
                if (line.isBlank()) continue;
                String[] a = line.trim().split("\\s+");
                render(w, a[0], Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]),
                        Integer.parseInt(a[4]), Integer.parseInt(a[5]));
            }
        } catch (Exception e) {
            getLogger().severe("Fehler: " + e);
            e.printStackTrace();
        }
        Bukkit.shutdown();
    }

    private void render(World w, String name, int x0, int z0, int x1, int z1, int step) throws Exception {
        int wpx = (x1 - x0) / step, hpx = (z1 - z0) / step;
        BufferedImage img = new BufferedImage(wpx, hpx, BufferedImage.TYPE_INT_RGB);
        int[] prev = new int[wpx];
        java.util.Arrays.fill(prev, Integer.MIN_VALUE);
        int loaded = 0;
        for (int py = 0; py < hpx; py++) {
            int z = z0 + py * step;
            for (int px = 0; px < wpx; px++) {
                int x = x0 + px * step;
                int cx = x >> 4, cz = z >> 4;
                if (!w.isChunkLoaded(cx, cz)) {
                    if (!w.loadChunk(cx, cz, false)) { img.setRGB(px, py, 0x000000); prev[px] = Integer.MIN_VALUE; continue; }
                    loaded++;
                }
                Block b = w.getHighestBlockAt(x, z, HeightMap.WORLD_SURFACE);
                int h = b.getY();
                org.bukkit.Color c = b.getBlockData().getMapColor();
                double f = prev[px] == Integer.MIN_VALUE ? 0.86 : h > prev[px] ? 1.0 : h < prev[px] ? 0.71 : 0.86;
                if (c.getRed() == 0 && c.getGreen() == 0 && c.getBlue() == 0) { // Luft / ohne Farbe
                    img.setRGB(px, py, 0x101010);
                } else {
                    img.setRGB(px, py, ((int) (c.getRed() * f) << 16) | ((int) (c.getGreen() * f) << 8) | (int) (c.getBlue() * f));
                }
                prev[px] = h;
            }
            if (py % 16 == 15) { // alte Chunks wieder freigeben
                for (var ch : w.getLoadedChunks()) if (!ch.isForceLoaded()) ch.unload(false);
            }
        }
        ImageIO.write(img, "png", new File(getDataFolder().getParentFile(), name + ".png"));
        getLogger().info(name + ": " + wpx + "x" + hpx + " px, " + loaded + " Chunk-Ladevorgänge");
    }
}
