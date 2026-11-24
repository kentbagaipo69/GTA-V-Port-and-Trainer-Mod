package dev.gtav.trainer;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.Button;
import android.widget.TextView;

final class TrainerStyle {
    static final int BG=Color.rgb(17,20,22), SURFACE=Color.rgb(28,32,35), LINE=Color.rgb(48,54,57);
    static final int TEXT=Color.rgb(236,239,234), MUTED=Color.rgb(151,161,160), ACCENT=Color.rgb(185,215,198);
    static int dp(Context c,int value) { return Math.round(value*c.getResources().getDisplayMetrics().density); }
    static GradientDrawable background(Context c,int color,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;
    }
    static void button(Button b,boolean selected) {
        b.setAllCaps(false);b.setTextSize(13);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setTextColor(selected?BG:TEXT);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);
        b.setPadding(dp(b.getContext(),12),0,dp(b.getContext(),12),0);
        int color=selected?ACCENT:SURFACE;
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(LINE),background(b.getContext(),color,6),null));
    }
    static TextView text(Context c,String value,int size,int color) {
        TextView v=new TextView(c);v.setText(value);v.setTextColor(color);v.setTextSize(size);
        v.setFontFeatureSettings("kern");return v;
    }
}
