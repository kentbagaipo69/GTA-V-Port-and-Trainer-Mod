package dev.gtav.trainer;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

final class PerformanceSettings {
    private static final String[] KEYS = {"resolution", "fpsTarget", "quality", "textureQuality", "vsync"};
    static void show(Activity activity) {
        if (!activity.getPackageName().equals("com.thegwchr.fuzzy")) {
            Toast.makeText(activity, "Performance presets require the bundled game build.", Toast.LENGTH_LONG).show();
            return;
        }
        ActivityManager manager = activity.getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(memory);
        int recommended = PerformanceProfile.recommend(memory.totalMem, manager.isLowRamDevice());
        Point size = new Point();
        activity.getWindowManager().getDefaultDisplay().getRealSize(size);
        SharedPreferences prefs = activity.getSharedPreferences("launcher", Context.MODE_PRIVATE);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = TrainerStyle.dp(activity, 20);content.setPadding(pad,pad,pad,pad);
        TextView description = new TextView(activity);
        description.setText("Starting suggestion: " + PerformanceProfile.NAMES[recommended]
                + " (based on RAM, not a GPU benchmark).\n\n"
                + "Requires Android 11+, ARM64 and a compatible Vulkan 1.3 driver. Presets cannot make unsupported hardware compatible.\n\n"
                + "Save and exit gameplay first. Settings take effect on the next game launch. Custom launch arguments can override these settings.");
        content.addView(description);
        RadioGroup choices = new RadioGroup(activity);
        for (int tier = 0; tier < 3; tier++) {
            String[] values = PerformanceProfile.values(tier, size.x, size.y);
            RadioButton choice = new RadioButton(activity);choice.setId(100 + tier);
            choice.setText(PerformanceProfile.NAMES[tier] + " · " + values[0] + " · " + values[1] + " FPS target\n"
                    + (tier == 0 ? "Low graphics / normal textures" : tier == 1 ? "Default graphics / normal textures" : "Default graphics / high textures"));
            choices.addView(choice);
        }
        choices.check(100 + recommended);content.addView(choices);
        TextView current = new TextView(activity);
        current.setText("\nCurrent launcher settings: " + prefs.getString("resolution", "Default resolution")
                + " · " + prefs.getString("fpsTarget", "60") + " FPS target.\n"
                + "All presets enable VSync. High increases load and may increase heat. FPS targets are not guaranteed.");
        content.addView(current);
        ScrollView scroll = new ScrollView(activity);scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Game performance")
                .setView(scroll).setNegativeButton("Cancel", null)
                .setNeutralButton("Restore previous", null).setPositiveButton("Apply preset", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int tier = choices.getCheckedRadioButtonId() - 100;
                SharedPreferences.Editor edit = prefs.edit();
                if (!prefs.getBoolean("trainer_profile_backup", false)) {
                    for (String key : KEYS) {
                        edit.putBoolean("trainer_saved_has_" + key, prefs.contains(key));
                        edit.putString("trainer_saved_" + key, prefs.getString(key, ""));
                    }
                    edit.putBoolean("trainer_profile_backup", true);
                }
                String[] values = PerformanceProfile.values(tier, size.x, size.y);
                for (int i = 0; i < KEYS.length; i++) edit.putString(KEYS[i], values[i]);
                if (edit.commit()) {
                    Toast.makeText(activity, "Preset saved. Exit and relaunch the game to apply it.", Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                } else Toast.makeText(activity, "Could not save settings. Try again.", Toast.LENGTH_LONG).show();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(prefs.getBoolean("trainer_profile_backup", false));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                SharedPreferences.Editor edit = prefs.edit();
                for (String key : KEYS) {
                    if (prefs.getBoolean("trainer_saved_has_" + key, false)) edit.putString(key, prefs.getString("trainer_saved_" + key, ""));
                    else edit.remove(key);
                    edit.remove("trainer_saved_has_" + key).remove("trainer_saved_" + key);
                }
                edit.remove("trainer_profile_backup");
                if (edit.commit()) {Toast.makeText(activity, "Previous settings restored. Relaunch the game.", Toast.LENGTH_LONG).show();dialog.dismiss();}
                else Toast.makeText(activity, "Could not restore settings. Try again.", Toast.LENGTH_LONG).show();
            });
        });
        dialog.show();
    }
}
