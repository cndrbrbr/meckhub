package io.github.cndrbrbr.meckhub;

import java.util.Locale;
import java.util.Optional;

/**
 * Ausrüstung einer Figur als Text: {@code MATERIAL[#rrggbb][@trimmaterial:muster]},
 * z. B. {@code LEATHER_CHESTPLATE#1a1a1a@emerald:silence}. Ohne Bukkit, damit testbar.
 */
public record Gear(String material, Integer color, String trimMaterial, String trimPattern) {

    public static Optional<Gear> parse(String s) {
        if (s == null || s.isBlank()) {
            return Optional.empty();
        }
        String rest = s.trim();
        String trimMat = null;
        String trimPat = null;
        int at = rest.indexOf('@');
        if (at >= 0) {
            String[] t = rest.substring(at + 1).split(":");
            if (t.length != 2 || t[0].isBlank() || t[1].isBlank()) {
                return Optional.empty();
            }
            trimMat = t[0].toLowerCase(Locale.ROOT);
            trimPat = t[1].toLowerCase(Locale.ROOT);
            rest = rest.substring(0, at);
        }
        Integer color = null;
        int hash = rest.indexOf('#');
        if (hash >= 0) {
            String hex = rest.substring(hash + 1);
            if (!hex.matches("[0-9a-fA-F]{6}")) {
                return Optional.empty();
            }
            color = Integer.parseInt(hex, 16);
            rest = rest.substring(0, hash);
        }
        if (!rest.matches("[A-Za-z_]+")) {
            return Optional.empty();
        }
        return Optional.of(new Gear(rest.toUpperCase(Locale.ROOT), color, trimMat, trimPat));
    }
}
