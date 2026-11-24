package dev.gtav.trainer;
import android.content.Context;
final class TrainerSession {
    private static TrainerSession instance;
    final CommandCatalog catalog;
    final BridgeClient client = new BridgeClient();
    private TrainerSession(Context context) throws Exception { catalog = CommandCatalog.load(context); }
    static TrainerSession get(Context context) throws Exception {
        if (instance == null) instance = new TrainerSession(context.getApplicationContext());
        return instance;
    }
}
