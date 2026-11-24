package dev.gtav.trainer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public final class OverlayService extends Service {
    private static final String STOP = "dev.gtav.trainer.STOP_OVERLAY";
    private static final String CHANNEL = "trainer_overlay";
    // Android's input dispatcher rejects touches on overlay windows below 0.5 alpha.
    private static final int MIN_OPACITY = 50;
    private static boolean running;
    private WindowManager windows;
    private WindowManager.LayoutParams params;
    private View overlay;
    private TrainerPanel panel;
    private android.os.Bundle panelState;
    private boolean expanded;
    private int bubbleX, bubbleY, availableWidth, availableHeight;
    private int opacityPercent;
    private int panelPercent, bubbleDp;
    private ContextThemeWrapper themed;

    static boolean isRunning() { return running; }

    @Override public void onCreate() {
        super.onCreate();
        windows = getSystemService(WindowManager.class);
        themed = new ContextThemeWrapper(this, android.R.style.Theme_Material_NoActionBar);
        bubbleX = getSharedPreferences("overlay", MODE_PRIVATE).getInt("x", dp(16));
        bubbleY = getSharedPreferences("overlay", MODE_PRIVATE).getInt("y", dp(120));
        opacityPercent = Math.max(MIN_OPACITY, Math.min(100,
                getSharedPreferences("overlay", MODE_PRIVATE).getInt("opacity_percent", 100)));
        panelPercent = Math.max(80, Math.min(130, getSharedPreferences("overlay", MODE_PRIVATE).getInt("panel_percent", 100)));
        bubbleDp = Math.max(40, Math.min(96, getSharedPreferences("overlay", MODE_PRIVATE).getInt("bubble_dp", 56)));
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Floating trainer", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            stopOverlay();
            return START_NOT_STICKY;
        }
        // The bundled overlay is owned by a visible game's binding, never a started service.
        if (getPackageName().equals("com.thegwchr.fuzzy")) { stopSelf(); return START_NOT_STICKY; }
        showOverlay();
        return START_NOT_STICKY;
    }

    private void showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            stopOverlay();
            return;
        }
        try {
            Notification notification = notification();
            if (Build.VERSION.SDK_INT >= 34) startForeground(7, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(7, notification);
            if (overlay == null) showBubble();
            running = true;
        } catch (RuntimeException e) { fail(e); }
    }

    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 1,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 2, new Intent(this, OverlayService.class).setAction(STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Trainer overlay active")
                .setContentText("Tap the bubble for experimental live game controls.")
                .setContentIntent(open).setOngoing(true)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Stop overlay", stop).build())
                .build();
    }

    private void showBubble() {
        detachOverlay();
        expanded = false;
        panel = null;
        Button bubble = new Button(themed);
        bubble.setText("T");
        bubble.setTextColor(TrainerStyle.BG);
        bubble.setTextSize(20);
        bubble.setPadding(0, 0, 0, 0);
        bubble.setMinWidth(0);
        bubble.setMinHeight(0);
        bubble.setBackground(background(TrainerStyle.ACCENT, dp(16)));
        bubble.setContentDescription("Open trainer controls; drag to move");
        bubble.setOnClickListener(v -> showPanel());
        attach(bubble, dp(bubbleDp), dp(bubbleDp), bubbleX, bubbleY);
        drag(bubble);
    }

    private void showPanel() {
        bubbleX = params.x;
        bubbleY = params.y;
        detachOverlay();
        expanded = true;
        LinearLayout container = new LinearLayout(themed);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackground(background(TrainerStyle.BG, dp(12)));
        container.setClipToOutline(true);
        LinearLayout header = new LinearLayout(themed);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(TrainerStyle.SURFACE);
        TextView handle = new TextView(themed);
        handle.setText("Trainer  /  Story mode");
        handle.setTextColor(Color.WHITE);
        handle.setTextSize(13);
        handle.setGravity(Gravity.CENTER_VERTICAL);
        handle.setPadding(dp(12), 0, 0, 0);
        handle.setContentDescription("Drag trainer panel");
        header.addView(handle, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout display = new LinearLayout(themed);
        display.setOrientation(LinearLayout.VERTICAL);
        display.setVisibility(View.GONE);
        display.addView(opacityControl(), new LinearLayout.LayoutParams(-1, dp(48)));
        display.addView(sizeControl(false), new LinearLayout.LayoutParams(-1, dp(48)));
        display.addView(sizeControl(true), new LinearLayout.LayoutParams(-1, dp(48)));
        Button stopOverlay = new Button(themed);
        stopOverlay.setText("Stop overlay");
        stopOverlay.setAllCaps(false);
        stopOverlay.setOnClickListener(v -> stopOverlay());
        display.addView(stopOverlay, new LinearLayout.LayoutParams(-1, dp(48)));
        Button settings = headerButton("⋯", "Overlay display settings");
        settings.setOnClickListener(v -> display.setVisibility(display.getVisibility() == View.GONE ? View.VISIBLE : View.GONE));
        header.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        Button collapse = headerButton("−", "Collapse to bubble");
        collapse.setOnClickListener(v -> collapse());
        header.addView(collapse, new LinearLayout.LayoutParams(dp(48), dp(48)));
        Button close = headerButton("×", "Close controls; keep bubble");
        close.setOnClickListener(v -> collapse());
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        container.addView(header);
        container.addView(display);
        try {
            panel = new TrainerPanel(themed, true, this::enableKeyboard, panelState);
            container.addView(panel.view(), new LinearLayout.LayoutParams(-1, 0, 1));
            measureArea();
            attach(container, panelWidth(), panelHeight(), bubbleX, bubbleY);
            drag(handle);
            container.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) releaseKeyboard();
                return false;
            });
        } catch (Exception e) { fail(e); }
    }

    private void collapse() {
        panelState = new android.os.Bundle();
        if (panel != null) panel.saveState(panelState);
        bubbleX = params.x;
        bubbleY = params.y;
        try { showBubble(); } catch (RuntimeException error) { fail(error); }
        rememberPosition();
    }

    private View opacityControl() {
        LinearLayout row = new LinearLayout(themed);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(12), 0);
        TextView label = TrainerStyle.text(themed, "Opacity " + opacityPercent + "%", 12, TrainerStyle.MUTED);
        row.addView(label, new LinearLayout.LayoutParams(dp(100), -2));
        SeekBar slider = new SeekBar(themed);
        slider.setContentDescription("Overlay opacity");
        slider.setMin(MIN_OPACITY);
        slider.setMax(100);
        slider.setProgress(opacityPercent);
        slider.setProgressTintList(ColorStateList.valueOf(TrainerStyle.ACCENT));
        slider.setThumbTintList(ColorStateList.valueOf(TrainerStyle.ACCENT));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                opacityPercent = Math.max(MIN_OPACITY, Math.min(100, progress));
                label.setText("Opacity " + opacityPercent + "%");
                if (params != null) {
                    params.alpha = opacityPercent / 100f;
                    update();
                }
                getSharedPreferences("overlay", MODE_PRIVATE).edit()
                        .putInt("opacity_percent", opacityPercent).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        row.addView(slider, new LinearLayout.LayoutParams(0, -1, 1));
        return row;
    }

    private int panelWidth() { return Math.min(dp(380 * panelPercent / 100), availableWidth); }
    private int panelHeight() { return Math.min(dp(620 * panelPercent / 100), availableHeight); }

    private View sizeControl(boolean bubble) {
        LinearLayout row = new LinearLayout(themed);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(12), 0);
        String title = bubble ? "Bubble " : "Panel ";
        String unit = bubble ? " dp" : "%";
        TextView label = TrainerStyle.text(themed, title + (bubble ? bubbleDp : panelPercent) + unit, 12, TrainerStyle.MUTED);
        row.addView(label, new LinearLayout.LayoutParams(dp(100), -2));
        SeekBar slider = new SeekBar(themed);
        slider.setContentDescription(bubble ? "Bubble size" : "Panel size");
        slider.setMin(bubble ? 40 : 80); slider.setMax(bubble ? 96 : 130);
        slider.setProgress(bubble ? bubbleDp : panelPercent);
        slider.setProgressTintList(ColorStateList.valueOf(TrainerStyle.ACCENT));
        slider.setThumbTintList(ColorStateList.valueOf(TrainerStyle.ACCENT));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (!fromUser) return;
                if (bubble) bubbleDp = value; else panelPercent = value;
                label.setText(title + value + unit);
                getSharedPreferences("overlay", MODE_PRIVATE).edit()
                        .putInt(bubble ? "bubble_dp" : "panel_percent", value).apply();
                if (!bubble && overlay != null) {
                    measureArea(); params.width = panelWidth(); params.height = panelHeight(); clamp(); update();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        row.addView(slider, new LinearLayout.LayoutParams(0, -1, 1));
        return row;
    }

    private void attach(View view, int width, int height, int x, int y) {
        params = new WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        params.setTitle("GTA V Trainer Overlay");
        params.alpha = opacityPercent / 100f;
        params.x = x;
        params.y = y;
        clamp();
        windows.addView(view, params);
        overlay = view;
    }

    private void drag(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean moved;
            final int slop = ViewConfiguration.get(OverlayService.this).getScaledTouchSlop();
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX(); downY = event.getRawY();
                        startX = params.x; startY = params.y; moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                        moved |= Math.hypot(dx, dy) > slop;
                        if (moved) {
                            params.x = startX + Math.round(dx);
                            params.y = startY + Math.round(dy);
                            clamp();
                            update();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) rememberPosition(); else v.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        rememberPosition();
                        return true;
                    default: return false;
                }
            }
        });
    }

    private void measureArea() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowMetrics metrics = windows.getCurrentWindowMetrics();
            android.graphics.Insets bars = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            availableWidth = metrics.getBounds().width() - bars.left - bars.right;
            availableHeight = metrics.getBounds().height() - bars.top - bars.bottom;
        } else {
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            windows.getDefaultDisplay().getMetrics(metrics);
            availableWidth = metrics.widthPixels;
            availableHeight = metrics.heightPixels;
        }
    }

    private void clamp() {
        measureArea();
        params.x = Math.max(0, Math.min(params.x, availableWidth - params.width));
        params.y = Math.max(0, Math.min(params.y, availableHeight - params.height));
    }

    private void rememberPosition() {
        bubbleX = params.x;
        bubbleY = params.y;
        getSharedPreferences("overlay", MODE_PRIVATE).edit().putInt("x", bubbleX).putInt("y", bubbleY).apply();
    }

    private void enableKeyboard() {
        if (overlay == null) return;
        params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        update();
    }

    private void releaseKeyboard() {
        if (overlay == null) return;
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(overlay.getWindowToken(), 0);
        overlay.clearFocus();
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        update();
    }

    private void update() {
        if (overlay == null) return;
        try { windows.updateViewLayout(overlay, params); }
        catch (RuntimeException e) { fail(e); }
    }

    private void detachOverlay() {
        // Unsubscribe even when Android already removed the window after permission loss.
        if (panel != null) panel.pause();
        if (overlay == null) return;
        View detached = overlay;
        overlay = null;
        try { getSystemService(InputMethodManager.class).hideSoftInputFromWindow(detached.getWindowToken(), 0); }
        catch (RuntimeException error) { android.util.Log.w("TrainerOverlay", "Keyboard cleanup failed", error); }
        try { windows.removeViewImmediate(detached); }
        catch (RuntimeException error) { android.util.Log.w("TrainerOverlay", "Window cleanup failed", error); }
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (overlay == null) return;
        releaseKeyboard();
        measureArea();
        params.width = expanded ? panelWidth() : dp(bubbleDp);
        params.height = expanded ? panelHeight() : dp(bubbleDp);
        clamp();
        update();
        if (panel != null) panel.onConfigurationChanged(config);
    }

    private Button headerButton(String label, String description) {
        Button button = new Button(themed);
        button.setText(label);
        button.setTextSize(22);
        button.setTextColor(Color.WHITE);
        button.setContentDescription(description);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void fail(Exception error) {
        android.util.Log.e("TrainerOverlay", "Unable to display overlay", error);
        Toast.makeText(this, "Overlay could not open. Check Display over other apps permission.", Toast.LENGTH_LONG).show();
        stopOverlay();
    }

    @Override public void onDestroy() {
        detachOverlay();
        panel = null;
        running = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    private void stopOverlay() {
        detachOverlay();panel=null;running=false;
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    @Override public IBinder onBind(Intent intent) {
        showOverlay();
        return new android.os.Binder();
    }
    @Override public boolean onUnbind(Intent intent) { stopOverlay();return false; }
    @Override public void onTaskRemoved(Intent rootIntent) { stopOverlay();super.onTaskRemoved(rootIntent); }
}
