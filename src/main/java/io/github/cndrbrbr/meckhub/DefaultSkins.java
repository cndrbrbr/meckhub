package io.github.cndrbrbr.meckhub;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Die eingebauten Minecraft-Standardskins. Ohne hinterlegte Textur wählt das Spiel den Skin
 * über die UUID: Index = floorMod(uuid.hashCode(), 18), erst die 9 schmalen, dann die 9
 * breiten Varianten. Hier wird eine UUID gesucht, die auf den gewünschten Skin fällt.
 */
public final class DefaultSkins {

    static final List<String> NAMES = List.of("alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri");

    private DefaultSkins() {
    }

    /** @param spec z. B. "kai" (breit) oder "kai-slim" */
    public static Optional<UUID> uuidFor(String spec, long seed) {
        String s = spec.toLowerCase(Locale.ROOT);
        boolean slim = s.endsWith("-slim");
        int idx = NAMES.indexOf(slim ? s.substring(0, s.length() - 5) : s);
        if (idx < 0) {
            return Optional.empty();
        }
        int want = slim ? idx : idx + NAMES.size();
        for (long i = 0; ; i++) {
            UUID u = new UUID(0x6d65636b68756200L ^ seed, i); // fest je Figur, damit der Skin gleich bleibt
            if (Math.floorMod(u.hashCode(), 2 * NAMES.size()) == want) {
                return Optional.of(u);
            }
        }
    }
}
