package com.rescuelink.app.utils;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

import com.rescuelink.app.models.IncidentStatus;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class UiUtils {
    public static final int GREEN_FG = 0xFF15803D, GREEN_BG = 0xFFDCFCE7;
    public static final int GRAY_FG = 0xFF7B8794, GRAY_BG = 0xFFEEF1F5;
    public static final int AMBER_FG = 0xFFB45309, AMBER_BG = 0xFFFEF3C7;
    public static final int RED_FG = 0xFFD92D20, RED_BG = 0xFFFDECEA;
    public static final int BLUE_FG = 0xFF2563EB, BLUE_BG = 0xFFE0EAFF;
    public static final int NAVY_FG = 0xFF0A1F44, NAVY_BG = 0xFFDCE6FA;

    private UiUtils() { }

    public static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static void styleBadge(TextView tv, String text, int textColor, int bgColor) {
        Context c = tv.getContext();
        tv.setText(text);
        tv.setTextColor(textColor);
        tv.setBackground(rounded(bgColor, dp(c, 12)));
        tv.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
    }

    public static void styleChip(TextView tv, IncidentStatus status) {
        styleBadge(tv, status.getLabel(), status.getTextColor(), status.getBgColor());
    }

    public static String formatTime(long millis) {
        return new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(new Date(millis));
    }

    public static String timeAgo(long millis) {
        long diff = System.currentTimeMillis() - millis;
        long min = diff / 60000L;
        if (min < 1) return "Just now";
        if (min < 60) return min + " min ago";
        long h = min / 60;
        if (h < 24) return h + (h == 1 ? " hour ago" : " hours ago");
        long d = h / 24;
        return d + (d == 1 ? " day ago" : " days ago");
    }

    public static String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return "Good morning";
        if (hour < 18) return "Good afternoon";
        return "Good evening";
    }
}
