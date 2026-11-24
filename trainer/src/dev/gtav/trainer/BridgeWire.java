package dev.gtav.trainer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Transport shared by the Android client and executable JVM integration tests. */
final class BridgeWire {
    static final String IDENTITY = "BRIDGE\t2\tcom.thegwchr.fuzzy\t0.8.0\tba87ec8a59e9b10437208a9c271290730527e9f6";
    private final String host, token;
    private final int port, readTimeout;
    private Socket activeProbe;
    private long probeGeneration;
    synchronized void cancelProbe() {
        probeGeneration++;
        if (activeProbe != null) try { activeProbe.close(); } catch (IOException ignored) { }
    }

    BridgeWire(String host, int port, String token) { this(host, port, token, 15000); }
    BridgeWire(String host, int port, String token, int readTimeout) {
        this.host = host; this.port = port; this.token = token; this.readTimeout = readTimeout;
    }
    static final class Failure extends IOException {
        final boolean uncertain;
        Failure(String message, boolean uncertain) { super(message); this.uncertain = uncertain; }
    }
    static final class Reply {
        final String status, message;
        Reply(String status, String message) { this.status = status; this.message = message; }
        boolean ready() { return status.equals("READY"); }
    }
    Reply probe() throws Failure {
        long generation;
        synchronized (this) { generation = probeGeneration; }
        String hello = request("HELLO", "", false, generation);
        if (!IDENTITY.equals(hello)) {
            if (hello.startsWith("FAILED\t")) throw new Failure(hello.substring(7), false);
            throw new Failure("Bridge version mismatch. Install the patched game with bridge 0.8.0.", false);
        }
        Reply reply = parse(request("PING", "", false, generation), false);
        if (!reply.status.equals("READY") && !reply.status.equals("WAITING") && !reply.status.equals("FAILED"))
            throw new Failure("Invalid readiness response from game bridge.", false);
        return reply;
    }
    Reply execute(String action, String item) throws Failure {
        if (action.isEmpty() || action.equals("HELLO") || action.equals("PING")
                || !action.matches("[a-z_]+\\.[a-z_]+") || !item.matches("[A-Za-z0-9_]*"))
            throw new Failure("Invalid selection; nothing was sent.", false);
        Reply readiness = probe();
        if (!readiness.ready()) return new Reply("BLOCKED", readiness.message);
        Reply reply = parse(request(action, item, true, -1), true);
        if (!reply.status.equals("APPLIED") && !reply.status.equals("CALLED") && !reply.status.equals("FAILED"))
            throw new Failure("Invalid action response. Check the game before retrying.", true);
        return reply;
    }
    private static Reply parse(String line, boolean uncertain) throws Failure {
        int split = line.indexOf('\t');
        if (split < 1 || split == line.length() - 1 || line.indexOf('\t', split + 1) >= 0)
            throw new Failure("Malformed bridge response. No confirmation received.", uncertain);
        return new Reply(line.substring(0, split), line.substring(split + 1));
    }
    private String request(String action, String item, boolean mutating, long generation) throws Failure {
        boolean attempted = false;
        try (Socket socket = new Socket()) {
            if (!mutating) synchronized (this) {
                if (generation != probeGeneration) throw new IOException("probe canceled");
                activeProbe = socket;
            }
            socket.connect(new InetSocketAddress(host, port), 1500);
            int budget = mutating ? readTimeout : Math.min(readTimeout, 2500);
            long deadline = System.nanoTime() + budget * 1000000L;
            attempted = true;
            socket.getOutputStream().write((token + "\t" + action + "\t" + item + "\n").getBytes(StandardCharsets.UTF_8));
            InputStream in = socket.getInputStream();
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < 512; i++) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new IOException("response deadline exceeded");
                socket.setSoTimeout((int)Math.max(1, (remaining + 999999L) / 1000000L));
                int ch = in.read();
                if (ch == '\n') return result.toString();
                if (ch < 0) throw new IOException("incomplete response");
                if (ch == 0 || ch == '\r' || ch > 127) throw new IOException("invalid response bytes");
                result.append((char) ch);
            }
            throw new IOException("response too long");
        } catch (IOException e) {
            boolean uncertain = attempted && mutating;
            throw new Failure(uncertain
                    ? "No confirmation received. The action may have run; check the game before retrying."
                    : "Cannot reach game bridge. Open the matching patched game and load story mode.", uncertain);
        } finally {
            if (!mutating) synchronized (this) { activeProbe = null; }
        }
    }
}
