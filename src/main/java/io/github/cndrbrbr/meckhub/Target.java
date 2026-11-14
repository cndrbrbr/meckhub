package io.github.cndrbrbr.meckhub;

import java.util.Locale;
import java.util.Optional;

/**
 * Wohin eine Spielfigur führt:
 * <ul>
 *   <li>{@code server:mc2} – Server im eigenen BungeeCord-Netz</li>
 *   <li>{@code transfer:host:port} – anderer Rechner, Minecraft-Transfer (ab 1.20.5)</li>
 *   <li>{@code world:name} – Welt auf diesem Server (z. B. von Multiverse)</li>
 * </ul>
 */
public record Target(Kind kind, String name, String host, int port) {

    public enum Kind { SERVER, TRANSFER, WORLD }

    public static Optional<Target> parse(String s) {
        if (s == null) {
            return Optional.empty();
        }
        String[] p = s.trim().split(":");
        if (p.length < 2 || p[1].isBlank()) {
            return Optional.empty();
        }
        switch (p[0].toLowerCase(Locale.ROOT)) {
            case "server":
                return p.length == 2 ? Optional.of(new Target(Kind.SERVER, p[1], null, 0)) : Optional.empty();
            case "world":
                return p.length == 2 ? Optional.of(new Target(Kind.WORLD, p[1], null, 0)) : Optional.empty();
            case "transfer":
                if (p.length > 3) {
                    return Optional.empty();
                }
                int port = 25565;
                if (p.length == 3) {
                    try {
                        port = Integer.parseInt(p[2]);
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                }
                if (port < 1 || port > 65535) {
                    return Optional.empty();
                }
                return Optional.of(new Target(Kind.TRANSFER, p[1] + ":" + port, p[1], port));
            default:
                return Optional.empty();
        }
    }

    /** Adresse zum Abtippen (nur bei Transfer). */
    public String address() {
        return port == 25565 ? host : host + ":" + port;
    }

    @Override
    public String toString() {
        return switch (kind) {
            case SERVER -> "server:" + name;
            case WORLD -> "world:" + name;
            case TRANSFER -> "transfer:" + host + ":" + port;
        };
    }
}
