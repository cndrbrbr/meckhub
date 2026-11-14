package io.github.cndrbrbr.meckhub;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimale Server-List-Ping-Abfrage (Status-Protokoll), wie sie die Serverliste im Spiel
 * macht. Liefert, ob der Server antwortet, und die Spielerzahl. Läuft asynchron.
 */
public final class Pinger {

    public record Status(boolean online, int players, int max) {
        public static final Status OFFLINE = new Status(false, 0, 0);
    }

    private static final Pattern ONLINE = Pattern.compile("\"online\"\\s*:\\s*(\\d+)");
    private static final Pattern MAX = Pattern.compile("\"max\"\\s*:\\s*(\\d+)");

    private Pinger() {
    }

    public static Status ping(String host, int port, int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            s.setSoTimeout(timeoutMs);
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            ByteArrayOutputStream hs = new ByteArrayOutputStream();
            writeVarInt(hs, 0x00);           // Handshake
            writeVarInt(hs, 767);            // Protokoll (egal für Status)
            writeString(hs, host);
            hs.write((port >> 8) & 0xff);
            hs.write(port & 0xff);
            writeVarInt(hs, 1);              // nächster Zustand: Status
            writePacket(out, hs.toByteArray());
            writePacket(out, new byte[] {0x00}); // Status-Anfrage
            DataInputStream in = new DataInputStream(s.getInputStream());
            readVarInt(in);                  // Paketlänge
            if (readVarInt(in) != 0x00) {
                return Status.OFFLINE;
            }
            int len = readVarInt(in);
            if (len < 0 || len > 1 << 21) {
                return Status.OFFLINE;
            }
            byte[] json = in.readNBytes(len);
            return parse(new String(json, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return Status.OFFLINE;
        }
    }

    static Status parse(String json) {
        int i = json.indexOf("\"players\"");
        String part = i >= 0 ? json.substring(i) : json;
        Matcher on = ONLINE.matcher(part);
        Matcher mx = MAX.matcher(part);
        return new Status(true, on.find() ? Integer.parseInt(on.group(1)) : 0, mx.find() ? Integer.parseInt(mx.group(1)) : 0);
    }

    static void writePacket(OutputStream out, byte[] body) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        writeVarInt(b, body.length);
        b.write(body);
        out.write(b.toByteArray());
        out.flush();
    }

    static void writeVarInt(OutputStream out, int v) throws IOException {
        while ((v & ~0x7f) != 0) {
            out.write((v & 0x7f) | 0x80);
            v >>>= 7;
        }
        out.write(v);
    }

    static int readVarInt(InputStream in) throws IOException {
        int v = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = in.read();
            if (b < 0) {
                throw new IOException("Ende des Datenstroms");
            }
            v |= (b & 0x7f) << shift;
            if ((b & 0x80) == 0) {
                return v;
            }
        }
        throw new IOException("VarInt zu lang");
    }

    static void writeString(OutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, b.length);
        out.write(b);
    }
}
