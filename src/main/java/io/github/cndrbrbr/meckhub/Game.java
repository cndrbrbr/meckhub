package io.github.cndrbrbr.meckhub;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Ein Spiel aus der config.yml.
 *
 * @param pingHost Adresse für die Statusabfrage (null = keine Abfrage, z. B. bei Welten)
 */
public record Game(String id, String name, String description, Target target, String pingHost, int pingPort,
                   String skin, Material icon, String color, Map<EquipmentSlot, Gear> gear) {
}
