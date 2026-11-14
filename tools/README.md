# Werkzeuge (einmalig, nicht Teil des Plugins)

- `inselbau/Bau.java` – hat am 14.11.2026 den Rohbau der Eingangsinsel in SpawnHub gesetzt
  (Mitte x −15 / z 0, Boden y 62, Radius 24, 7 Podeste, Brücke nach Norden zur alten Lobby)
  und dabei `orte.yml` mit Spawn und Figurplätzen geschrieben. Lief auf einer Kopie der Welt.
- `mapshot/Mapshot.java` – zeichnet Draufsichten einer Welt als PNG (Kartenfarben, Höhenschattierung);
  Bereiche aus `plugins/mapshot.txt`: `name x0 z0 x1 z1 schritt`.

Beide als kleines Plugin mit eigener plugin.yml auf einem Wegwerf-Server laufen lassen
(spigot.yml `timeout-time` hochsetzen, sonst bricht der Watchdog ab).
