package dev.gtav.trainer;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private TrainerPanel panel;
    private TextView overlayStatus;
    private boolean awaitingPermission;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        awaitingPermission = state != null && state.getBoolean("awaiting_overlay_permission", false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(TrainerStyle.BG);
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return WindowInsets.CONSUMED;
            });
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(TrainerStyle.dp(this,12),TrainerStyle.dp(this,8),TrainerStyle.dp(this,12),0);
        Button start = new Button(this);
        start.setText("Start overlay");
        start.setAllCaps(false);
        TrainerStyle.button(start,true);
        start.setOnClickListener(v -> requestOverlay());
        Button stop = new Button(this);
        stop.setText("Stop overlay");
        stop.setAllCaps(false);
        TrainerStyle.button(stop,false);
        stop.setOnClickListener(v -> {
            awaitingPermission = false;
            stopService(new Intent(this, OverlayService.class));
            overlayStatus.setText("Overlay stopped.");
        });
        actions.addView(start, new LinearLayout.LayoutParams(0, TrainerStyle.dp(this,44), 1));
        LinearLayout.LayoutParams stopParams=new LinearLayout.LayoutParams(0,TrainerStyle.dp(this,44),1);
        stopParams.leftMargin=TrainerStyle.dp(this,6);actions.addView(stop,stopParams);
        root.addView(actions);
        Button performance = new Button(this);
        performance.setText("Game performance");performance.setAllCaps(false);
        TrainerStyle.button(performance, false);
        performance.setOnClickListener(v -> PerformanceSettings.show(this));
        root.addView(performance, new LinearLayout.LayoutParams(-1, TrainerStyle.dp(this,44)));
        overlayStatus = new TextView(this);
        overlayStatus.setTextColor(TrainerStyle.MUTED);
        overlayStatus.setTextSize(11);
        overlayStatus.setPadding(TrainerStyle.dp(this,12),TrainerStyle.dp(this,6),TrainerStyle.dp(this,12),TrainerStyle.dp(this,4));
        root.addView(overlayStatus);
        try {
            panel = new TrainerPanel(this, false, () -> { }, state);
            root.addView(panel.view(), new LinearLayout.LayoutParams(-1, 0, 1));
        } catch (Exception e) {
            overlayStatus.setText("Could not load trainer: " + e.getMessage());
        }
        setContentView(root);
    }

    private void requestOverlay() {
        if (Settings.canDrawOverlays(this)) {
            startOverlay();
            return;
        }
        awaitingPermission = true;
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException e) {
            awaitingPermission = false;
            overlayStatus.setText("Open Settings > Apps > Special app access > Display over other apps, then enable this app.");
        }
    }

    private void startOverlay() {
        awaitingPermission = false;
        if (getPackageName().equals("com.thegwchr.fuzzy")) {
            startActivity(new Intent().setClassName(getPackageName(), "com.thegwchr.fuzzy.LauncherActivity"));
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                && !getPreferences(MODE_PRIVATE).getBoolean("notification_prompted", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notification_prompted", true).apply();
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
        try {
            startForegroundService(new Intent(this, OverlayService.class));
            overlayStatus.setText("Overlay starting…");
        } catch (RuntimeException e) {
            overlayStatus.setText("Could not start overlay: " + e.getMessage());
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (panel != null) panel.resume();
        if (awaitingPermission) {
            awaitingPermission = false;
            if (Settings.canDrawOverlays(this)) startOverlay();
            else overlayStatus.setText("Overlay permission was not granted. Tap Start overlay to try again.");
        } else if (panel != null) {
            overlayStatus.setText(OverlayService.isRunning()
                    ? "Overlay active · Tap to open, drag to move"
                    : "Overlay off");
        }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("awaiting_overlay_permission", awaitingPermission);
        if (panel != null) panel.saveState(outState);
    }

    @Override protected void onStop() {
        if (panel != null) panel.pause();
        super.onStop();
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (panel != null) panel.onConfigurationChanged(configuration);
    }
}
