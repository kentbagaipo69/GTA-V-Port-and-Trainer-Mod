package dev.gtav.trainer;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only checks may repeat. Game-changing commands are sent at most once per tap. */
final class BridgeClient {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Set<Runnable> listeners = new LinkedHashSet<>();
    private final BridgeWire wire = new BridgeWire("127.0.0.1", BridgeConfig.PORT, BridgeConfig.TOKEN);
    private boolean busy, ready;
    private String connection = "Checking com.thegwchr.fuzzy…";
    private String message = "No selection sent. Waiting for story mode.";
    private final Runnable poll = () -> check();

    String status() { return message; }
    String connection() { return connection; }
    boolean isBusy() { return busy; }
    boolean isConnected() { return ready; }
    boolean canExecute() { return ready && !busy; }
    void subscribe(Runnable listener) {
        if (listeners.add(listener) && listeners.size() == 1) check();
    }
    void unsubscribe(Runnable listener) {
        listeners.remove(listener);
        if (listeners.isEmpty()) { main.removeCallbacks(poll); ready = false; wire.cancelProbe(); }
    }
    private void notifyViews() {
        for (Runnable listener : new ArrayList<>(listeners)) listener.run();
    }
    private void complete() {
        busy = false;
        notifyViews();
        main.removeCallbacks(poll);
        if (!listeners.isEmpty()) main.postDelayed(poll, 4000);
    }
    void check() {
        if (busy) return;
        main.removeCallbacks(poll);
        busy = true;
        notifyViews();
        worker.execute(() -> {
            BridgeWire.Reply reply;
            try { reply = wire.probe(); }
            catch (BridgeWire.Failure e) { reply = new BridgeWire.Reply("FAILED", e.getMessage()); }
            final BridgeWire.Reply outcome = reply;
            main.post(() -> {
                ready = !listeners.isEmpty() && outcome.ready();
                if (ready && outcome.message.startsWith("Money:")) message = outcome.message;
                connection = ready ? "Connected · com.thegwchr.fuzzy · bridge 0.8.0"
                        : outcome.message;
                complete();
            });
        });
    }
    void execute(CommandCatalog.Action action, CommandCatalog.Item item) {
        executeRaw(action.id, item == null ? "" : item.id, item == null ? action.label : item.name, null);
    }
    void executeRaw(String action, String item, String label, java.util.function.Consumer<BridgeWire.Reply> callback) {
        if (!canExecute()) return;
        main.removeCallbacks(poll);
        busy = true;
        message = "Waiting for game: " + label;
        notifyViews();
        worker.execute(() -> {
            String result;
            BridgeWire.Reply response = null;
            boolean connected = true;
            try {
                BridgeWire.Reply reply = wire.execute(action, item);
                response = reply;
                switch (reply.status) {
                    case "APPLIED": result = "Game confirmed: " + reply.message; break;
                    case "CALLED": result = "Sent to game: " + reply.message + " (verify visually)"; break;
                    case "BLOCKED": connected = false; result = "Not sent: " + reply.message; break;
                    default: result = "Not applied: " + reply.message;
                }
                if (action.equals("clothing.inspect") && reply.status.equals("APPLIED")) result = "Wardrobe options received.";
            } catch (BridgeWire.Failure e) {
                connected = false;
                result = (e.uncertain ? "" : "Not sent: ") + e.getMessage();
            }
            final String outcome = result;
            final boolean reachable = connected;
            final BridgeWire.Reply received = response;
            main.post(() -> {
                ready = !listeners.isEmpty() && reachable;
                if (!reachable) connection = "Connection needs checking · com.thegwchr.fuzzy";
                message = outcome;
                complete();
                if (callback != null) callback.accept(received);
            });
        });
    }
}
