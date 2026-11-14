package io.github.cndrbrbr.meckhub;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

/** MeckHub: Eingangswelt mit Spielfiguren und Menü. */
public final class HubPlugin extends JavaPlugin {

    private volatile List<Game> games = List.of();
    private final Map<String, Pinger.Status> status = new ConcurrentHashMap<>();
    private YamlConfiguration places;
    private File placesFile;
    private Connector connector;
    private Figures figures;
    private Menu menu;
    private HubListener hub;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        placesFile = new File(getDataFolder(), "orte.yml");
        places = YamlConfiguration.loadConfiguration(placesFile);
        loadGames();
        getServer().getMessenger().registerOutgoingPluginChannel(this, Connector.CHANNEL);
        connector = new Connector(this);
        figures = new Figures(this);
        menu = new Menu(this);
        hub = new HubListener(this);
        getServer().getPluginManager().registerEvents(figures, this);
        getServer().getPluginManager().registerEvents(menu, this);
        getServer().getPluginManager().registerEvents(hub, this);
        getServer().getPluginManager().registerEvents(new BuildWorlds(this), this);
        HubCommand cmd = new HubCommand(this);
        getCommand("hub").setExecutor(cmd);
        getCommand("hub").setTabCompleter(cmd);
        // Welten (z. B. Multiverse) sind beim Start evtl. noch nicht geladen → kurz warten.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            figures.refreshAll();
            for (var p : Bukkit.getOnlinePlayers()) {
                if (spawn() != null && p.getWorld().equals(spawn().getWorld())) {
                    hub.prepare(p);
                }
            }
        }, 40L);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::pingAll, 20L, 200L);
        Bukkit.getScheduler().runTaskTimer(this, figures::tick, 100L, 100L);
        getLogger().info(games.size() + " Spiele geladen.");
    }

    @Override
    public void onDisable() {
        if (figures != null) {
            figures.removeAll();
        }
    }

    void reload() {
        reloadConfig();
        places = YamlConfiguration.loadConfiguration(placesFile);
        loadGames();
        status.clear();
        figures.refreshAll();
        Bukkit.getScheduler().runTaskAsynchronously(this, this::pingAll);
    }

    private void loadGames() {
        List<Game> out = new ArrayList<>();
        ConfigurationSection sec = getConfig().getConfigurationSection("games");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection g = sec.getConfigurationSection(id);
                if (g == null) {
                    continue;
                }
                Optional<Target> target = Target.parse(g.getString("target"));
                if (target.isEmpty()) {
                    getLogger().warning("Spiel " + id + ": ungültiges target '" + g.getString("target")
                            + "' (server:<name>, transfer:<host>:<port> oder world:<name>)");
                    continue;
                }
                String pingHost = null;
                int pingPort = 25565;
                String ping = g.getString("ping", "");
                if (!ping.equalsIgnoreCase("none")) {
                    if (!ping.isBlank()) {
                        String[] hp = ping.split(":");
                        pingHost = hp[0];
                        pingPort = hp.length > 1 ? Integer.parseInt(hp[1]) : 25565;
                    } else if (target.get().kind() == Target.Kind.TRANSFER) {
                        pingHost = target.get().host();
                        pingPort = target.get().port();
                    } else if (target.get().kind() == Target.Kind.SERVER) {
                        pingHost = target.get().name(); // Containername im Docker-Netz
                    }
                }
                Material icon = Material.matchMaterial(g.getString("icon", "GRASS_BLOCK"));
                Map<EquipmentSlot, Gear> gear = new EnumMap<>(EquipmentSlot.class);
                ConfigurationSection eq = g.getConfigurationSection("ausruestung");
                if (eq != null) {
                    for (String k : eq.getKeys(false)) {
                        EquipmentSlot slot = Figures.slot(k);
                        Optional<Gear> parsed = Gear.parse(eq.getString(k));
                        if (slot == null || parsed.isEmpty()) {
                            getLogger().warning("Spiel " + id + ": Ausrüstung '" + k + ": " + eq.getString(k) + "' ungültig");
                        } else {
                            gear.put(slot, parsed.get());
                        }
                    }
                }
                out.add(new Game(id, g.getString("name", id), g.getString("beschreibung", ""), target.get(), pingHost,
                        pingPort, g.getString("skin", "default:steve"), icon == null ? Material.GRASS_BLOCK : icon,
                        g.getString("farbe", "#ffffff"), gear));
            }
        }
        games = List.copyOf(out);
    }

    private void pingAll() {
        for (Game g : games) {
            if (g.pingHost() != null) {
                status.put(g.id(), Pinger.ping(g.pingHost(), g.pingPort(), 1500));
            }
        }
    }

    /** Statuszeile für Schild und Menü. */
    String statusLine(Game g) {
        if (g.target().kind() == Target.Kind.WORLD) {
            World w = Bukkit.getWorld(g.target().name());
            if (w == null) {
                return ChatColor.GRAY + "○ bald verfügbar";
            }
            return ChatColor.GREEN + "● " + w.getPlayers().size() + " hier";
        }
        Pinger.Status s = status.get(g.id());
        if (s == null) {
            return ChatColor.GRAY + "● …";
        }
        return s.online() ? ChatColor.GREEN + "● " + s.players() + " online" : ChatColor.RED + "● offline";
    }

    /** "#rrggbb" oder "&a" → Farbcode für Texte. */
    static String color(String spec) {
        if (spec != null && spec.matches("#[0-9a-fA-F]{6}")) {
            return net.md_5.bungee.api.ChatColor.of(spec).toString();
        }
        return spec == null ? "" : ChatColor.translateAlternateColorCodes('&', spec);
    }

    List<Game> games() {
        return Collections.unmodifiableList(games);
    }

    Optional<Game> game(String id) {
        return games.stream().filter(g -> g.id().equalsIgnoreCase(id)).findFirst();
    }

    Location spawn() {
        return places.getLocation("spawn");
    }

    void setSpawn(Location l) {
        places.set("spawn", l);
        savePlaces();
        l.getWorld().setSpawnLocation(l);
    }

    Location figureLocation(String id) {
        Location l = places.getLocation("figuren." + id.toLowerCase(Locale.ROOT));
        return l == null || l.getWorld() == null ? null : l;
    }

    void setFigureLocation(String id, Location l) {
        places.set("figuren." + id.toLowerCase(Locale.ROOT), l);
        savePlaces();
    }

    private void savePlaces() {
        try {
            places.save(placesFile);
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "orte.yml nicht speicherbar", e);
        }
    }

    Connector connector() {
        return connector;
    }

    Figures figures() {
        return figures;
    }

    Menu menu() {
        return menu;
    }

    HubListener hub() {
        return hub;
    }
}
