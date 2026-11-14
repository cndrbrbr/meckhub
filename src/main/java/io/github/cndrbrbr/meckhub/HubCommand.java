package io.github.cndrbrbr.meckhub;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /hub [menue|setspawn|figur <spiel> [entfernen]|liste|reload] */
public final class HubCommand implements TabExecutor {

    private final HubPlugin plugin;

    public HubCommand(HubPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("meckhub.admin");
        if (sub.isEmpty() || sub.equals("menue")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Nur für Spieler.");
                return true;
            }
            if (sub.isEmpty() && plugin.spawn() != null) {
                p.teleport(plugin.spawn());
            } else {
                plugin.menu().open(p);
            }
            return true;
        }
        if (!admin) {
            sender.sendMessage(ChatColor.RED + "Dafür brauchst du Admin-Rechte.");
            return true;
        }
        switch (sub) {
            case "setspawn" -> {
                if (sender instanceof Player p) {
                    plugin.setSpawn(p.getLocation());
                    sender.sendMessage(ChatColor.GREEN + "Spawn der Eingangswelt gesetzt.");
                }
            }
            case "figur" -> {
                if (!(sender instanceof Player p) || args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "/" + label + " figur <spiel> [entfernen]  – setzt die Figur an deine Position, Blick wie du");
                    return true;
                }
                var g = plugin.game(args[1]);
                if (g.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "Unbekanntes Spiel. /" + label + " liste");
                    return true;
                }
                if (args.length >= 3 && args[2].equalsIgnoreCase("entfernen")) {
                    plugin.setFigureLocation(g.get().id(), null);
                    sender.sendMessage(ChatColor.GREEN + "Figur von " + g.get().name() + " entfernt.");
                } else {
                    Location l = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
                    l.setYaw(Math.round(p.getLocation().getYaw() / 15f) * 15f);
                    l.setPitch(0);
                    plugin.setFigureLocation(g.get().id(), l);
                    sender.sendMessage(ChatColor.GREEN + "Figur von " + g.get().name() + " steht hier. Tritt einen Schritt zur Seite.");
                }
                plugin.figures().refreshAll();
            }
            case "liste" -> {
                sender.sendMessage(ChatColor.GOLD + "Spiele:");
                for (Game g : plugin.games()) {
                    Location l = plugin.figureLocation(g.id());
                    sender.sendMessage(ChatColor.WHITE + g.id() + ChatColor.GRAY + " – " + g.name() + " → " + g.target()
                            + "  " + plugin.statusLine(g) + ChatColor.GRAY
                            + (l == null ? "  (keine Figur)" : "  Figur bei " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()));
                }
            }
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(ChatColor.GREEN + "MeckHub neu geladen (" + plugin.games().size() + " Spiele).");
            }
            default -> sender.sendMessage(ChatColor.RED + "/" + label + " [menue|setspawn|figur <spiel> [entfernen]|liste|reload]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        List<String> opts;
        if (args.length == 1) {
            opts = sender.hasPermission("meckhub.admin")
                    ? List.of("menue", "setspawn", "figur", "liste", "reload") : List.of("menue");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("figur")) {
            opts = plugin.games().stream().map(Game::id).toList();
        } else if (args.length == 3 && args[0].equalsIgnoreCase("figur")) {
            opts = List.of("entfernen");
        } else {
            return out;
        }
        String pre = args[args.length - 1].toLowerCase(Locale.ROOT);
        opts.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(pre)).forEach(out::add);
        return out;
    }
}
