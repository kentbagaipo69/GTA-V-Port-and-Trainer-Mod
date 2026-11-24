package dev.gtav.trainer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class BridgeWireTest {
    static int passed;
    interface Scenario { void run(BridgeWire wire) throws Exception; }
    static void require(boolean condition) { if (!condition) throw new AssertionError(); }
    static void scenario(String name, String[] actions, String[] responses, Scenario check) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<String> received = Collections.synchronizedList(new ArrayList<>());
        try (ServerSocket server = new ServerSocket(0, 8, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(3000);
            Thread worker = new Thread(() -> {
                try {
                    for (int i = 0; i < actions.length; i++) {
                        try (Socket socket = server.accept()) {
                            socket.setSoTimeout(2000);
                            String line = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).readLine();
                            require(line != null && line.startsWith("test-token\t"));
                            String command = line.substring("test-token\t".length());
                            received.add(command);
                            require(command.equals(actions[i]));
                            if (responses[i] != null) socket.getOutputStream().write(responses[i].getBytes(StandardCharsets.UTF_8));
                        }
                    }
                    server.setSoTimeout(200);
                    try (Socket unexpected = server.accept()) { throw new AssertionError("Unexpected extra request/retry"); }
                    catch (SocketTimeoutException expected) { }
                } catch (Throwable e) { failure.set(e); }
            });
            worker.start();
            check.run(new BridgeWire("127.0.0.1", server.getLocalPort(), "test-token", 500));
            worker.join(5000);
            require(!worker.isAlive());
            if (failure.get() != null) throw new AssertionError(name, failure.get());
            require(received.size() == actions.length);
        }
        passed++;
        System.out.println("PASS " + name);
    }
    static void fails(BridgeWire wire, boolean uncertain) throws Exception {
        try { wire.execute("weapons.give_selected", "WEAPON_PISTOL"); throw new AssertionError("Expected failure"); }
        catch (BridgeWire.Failure e) { require(e.uncertain == uncertain); }
    }
    public static void main(String[] args) throws Exception {
        String hello=BridgeWire.IDENTITY+"\n", ready="READY\tScripts running\n";
        String[] command={"HELLO\t","PING\t","weapons.give_selected\tWEAPON_PISTOL"};
        scenario("matched bridge sends exactly one selection", command,
                new String[]{hello,ready,"APPLIED\tWeapon received\n"}, w -> require(w.execute("weapons.give_selected","WEAPON_PISTOL").status.equals("APPLIED")));
        scenario("old bridge blocks mutation",new String[]{"HELLO\t"},new String[]{"BRIDGE\t1\tcom.thegwchr.fuzzy\n"},w -> fails(w,false));
        scenario("wrong package blocks mutation",new String[]{"HELLO\t"},new String[]{hello.replace("com.thegwchr.fuzzy","wrong.package")},w -> fails(w,false));
        scenario("wrong game fingerprint blocks mutation",new String[]{"HELLO\t"},new String[]{hello.replace("ba87ec8a","00000000")},w -> fails(w,false));
        scenario("pairing mismatch blocks mutation",new String[]{"HELLO\t"},new String[]{"FAILED\tPairing mismatch\n"},w -> fails(w,false));
        scenario("waiting does not send selection",new String[]{"HELLO\t","PING\t"},new String[]{hello,"WAITING\tEnter story mode\n"},w -> require(w.execute("weapons.give_selected","WEAPON_PISTOL").status.equals("BLOCKED")));
        scenario("truncated reply is uncertain and never retried",command,new String[]{hello,ready,"APPLIED\tMissing newline"},w -> fails(w,true));
        scenario("closed connection is uncertain and never retried",command,new String[]{hello,ready,null},w -> fails(w,true));
        scenario("oversized reply rejected",command,new String[]{hello,ready,"A".repeat(513)+"\n"},w -> fails(w,true));
        scenario("unknown action status rejected",command,new String[]{hello,ready,"READY\tNot an action confirmation\n"},w -> fails(w,true));
        scenario("reset effects is sent once",new String[]{"HELLO\t","PING\t","player.reset_effects\t"},new String[]{hello,ready,"CALLED\tEffects disabled\n"},w -> require(w.execute("player.reset_effects", "").status.equals("CALLED")));
        scenario("money targets explicit story character",new String[]{"HELLO\t","PING\t","money.add_franklin\t1000000"},new String[]{hello,ready,"APPLIED\tBalance confirmed\n"},w -> require(w.execute("money.add_franklin", "1000000").status.equals("APPLIED")));
        scenario("animal selection retains model identifier",new String[]{"HELLO\t","PING\t","characters.select_model\ta_c_boar"},new String[]{hello,ready,"APPLIED\tModel changed\n"},w -> require(w.execute("characters.select_model", "a_c_boar").status.equals("APPLIED")));
        scenario("fast swimming sends one toggle",new String[]{"HELLO\t","PING\t","player.fast_swim\t"},new String[]{hello,ready,"CALLED\tSwim multiplier set\n"},w -> require(w.execute("player.fast_swim", "").status.equals("CALLED")));
        scenario("wardrobe request preserves model and indices",new String[]{"HELLO\t","PING\t","clothing.apply\tc_11_2_1_4294967295"},new String[]{hello,ready,"APPLIED\tClothing confirmed\n"},w -> require(w.execute("clothing.apply","c_11_2_1_4294967295").status.equals("APPLIED")));
        scenario("destination retains selected identifier",new String[]{"HELLO\t","PING\t","teleport.destination\tairport_runway"},new String[]{hello,ready,"CALLED\tTeleport requested\n"},w -> require(w.execute("teleport.destination","airport_runway").status.equals("CALLED")));
        scenario("newline injection rejected before connection",new String[]{},new String[]{},w -> {
            try { w.execute("player.max_health\n", ""); throw new AssertionError(); }
            catch (BridgeWire.Failure e) { require(!e.uncertain); }
        });
        System.out.println(passed+" transport scenarios passed; no game code executed.");
    }
}
