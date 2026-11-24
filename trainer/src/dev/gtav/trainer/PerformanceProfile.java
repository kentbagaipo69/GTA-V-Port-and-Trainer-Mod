package dev.gtav.trainer;

final class PerformanceProfile {
    static final String[] NAMES = {"Low", "Medium", "High"};
    static int recommend(long bytes, boolean lowRam) {
        if (lowRam || bytes <= 4L * 1024 * 1024 * 1024) return 0;
        return bytes <= 8L * 1024 * 1024 * 1024 ? 1 : 2;
    }
    static String[] values(int tier, int width, int height) {
        if (tier < 0 || tier > 2 || width < 2 || height < 2)
            throw new IllegalArgumentException("Invalid performance profile or display size");
        int longer = Math.max(width, height), shorter = Math.min(width, height);
        double scale = Math.min(1.0, new int[]{960,1280,1600}[tier] / (double)longer);
        int w = Math.max(2, ((int)(longer * scale)) & ~1);
        int h = Math.max(2, ((int)(shorter * scale)) & ~1);
        // Values and types match LauncherActivity.settingsArgs in the supplied port.
        return new String[]{w + "x" + h, tier == 2 ? "60" : "30",
                tier == 0 ? "low" : "", tier == 2 ? "1" : "0", ""};
    }
}
