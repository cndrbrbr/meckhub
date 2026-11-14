package io.github.cndrbrbr.meckhub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LogicTest {

    @Test
    void ziele() {
        var s = Target.parse("server:mc2").orElseThrow();
        assertEquals(Target.Kind.SERVER, s.kind());
        assertEquals("mc2", s.name());
        var t = Target.parse("transfer:2.56.99.112:25566").orElseThrow();
        assertEquals("2.56.99.112", t.host());
        assertEquals(25566, t.port());
        assertEquals("2.56.99.112:25566", t.address());
        var d = Target.parse("transfer:codefield.de").orElseThrow();
        assertEquals(25565, d.port());
        assertEquals("codefield.de", d.address());
        assertEquals("world:schulcampus", Target.parse("world:schulcampus").orElseThrow().toString());
        assertTrue(Target.parse("transfer:host:99999").isEmpty());
        assertTrue(Target.parse("transfer:host:abc").isEmpty());
        assertTrue(Target.parse("bungee:mc2").isEmpty());
        assertTrue(Target.parse("server:").isEmpty());
        assertTrue(Target.parse(null).isEmpty());
    }

    @Test
    void ausruestung() {
        var g = Gear.parse("leather_chestplate#141414@Emerald:silence").orElseThrow();
        assertEquals("LEATHER_CHESTPLATE", g.material());
        assertEquals(0x141414, g.color());
        assertEquals("emerald", g.trimMaterial());
        assertEquals("silence", g.trimPattern());
        var plain = Gear.parse("BOOK").orElseThrow();
        assertEquals(null, plain.color());
        assertEquals(null, plain.trimMaterial());
        assertTrue(Gear.parse("LEATHER_HELMET#12345").isEmpty());
        assertTrue(Gear.parse("NETHERITE_CHESTPLATE@amethyst").isEmpty());
        assertTrue(Gear.parse("STEIN 1").isEmpty());
    }

    @Test
    void standardskins() {
        for (int i = 0; i < DefaultSkins.NAMES.size(); i++) {
            String n = DefaultSkins.NAMES.get(i);
            UUID wide = DefaultSkins.uuidFor(n, 42).orElseThrow();
            UUID slim = DefaultSkins.uuidFor(n + "-slim", 42).orElseThrow();
            assertEquals(i + 9, Math.floorMod(wide.hashCode(), 18), n);
            assertEquals(i, Math.floorMod(slim.hashCode(), 18), n);
            assertEquals(wide, DefaultSkins.uuidFor(n, 42).orElseThrow()); // stabil
        }
        assertTrue(DefaultSkins.uuidFor("herobrine", 1).isEmpty());
    }

    @Test
    void varIntHinUndZurueck() throws Exception {
        for (int v : new int[] {0, 1, 127, 128, 255, 25565, 2097151, Integer.MAX_VALUE}) {
            var out = new ByteArrayOutputStream();
            Pinger.writeVarInt(out, v);
            assertEquals(v, Pinger.readVarInt(new ByteArrayInputStream(out.toByteArray())));
        }
    }

    @Test
    void statusVomFakeServer() throws Exception {
        String json = "{\"version\":{\"name\":\"26.3\",\"protocol\":1},\"players\":{\"max\":40,\"online\":3},"
                + "\"description\":{\"text\":\"online: 99\"}}";
        try (ServerSocket server = new ServerSocket(0)) {
            Thread t = new Thread(() -> {
                try (Socket c = server.accept()) {
                    DataInputStream in = new DataInputStream(c.getInputStream());
                    in.readNBytes(Pinger.readVarInt(in)); // Handshake
                    in.readNBytes(Pinger.readVarInt(in)); // Status-Anfrage
                    var body = new ByteArrayOutputStream();
                    Pinger.writeVarInt(body, 0);
                    byte[] b = json.getBytes(StandardCharsets.UTF_8);
                    Pinger.writeVarInt(body, b.length);
                    body.write(b);
                    OutputStream out = c.getOutputStream();
                    Pinger.writePacket(out, body.toByteArray());
                } catch (Exception ignored) {
                    // Test prüft das Ergebnis
                }
            });
            t.start();
            var s = Pinger.ping("127.0.0.1", server.getLocalPort(), 2000);
            t.join(3000);
            assertTrue(s.online());
            assertEquals(3, s.players());
            assertEquals(40, s.max());
        }
    }

    @Test
    void offlineWennNiemandAntwortet() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        assertFalse(Pinger.ping("127.0.0.1", port, 500).online());
    }
}
