package com.rescuelink.app.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.rescuelink.app.R;

import java.util.Map;
import java.util.WeakHashMap;

/** The theme colours people can choose in Settings, and applying the chosen one to every screen. */
public final class ThemeManager {
    public static final class Option {
        public final String name;
        public final int style;
        public final int swatch;     // the colour shown in the picker
        Option(String name, int style, int swatch) { this.name = name; this.style = style; this.swatch = swatch; }
    }

    public static final Option[] OPTIONS = {
            new Option("Navy", R.style.ThemeOverlay_RL_Navy, 0xFF0A1F44),
            new Option("Ocean", R.style.ThemeOverlay_RL_Ocean, 0xFF0284C7),
            new Option("Teal", R.style.ThemeOverlay_RL_Teal, 0xFF0D9488),
            new Option("Forest", R.style.ThemeOverlay_RL_Forest, 0xFF16A34A),
            new Option("Purple", R.style.ThemeOverlay_RL_Purple, 0xFF7C3AED),
            new Option("Orange", R.style.ThemeOverlay_RL_Orange, 0xFFEA580C),
            new Option("Slate", R.style.ThemeOverlay_RL_Slate, 0xFF475569),
    };

    private static final String PREFS = "rescuelink_settings";
    private static final String KEY = "theme_index_";
    /** Whose colour {@link #current} holds. Each account keeps its own colour; it never changes anyone else's. */
    private static String currentFor = null;
    private static final Map<Activity, Integer> APPLIED = new WeakHashMap<>();
    private static int current = -1;

    private ThemeManager() { }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The signed-in account, or the one saved as signed in; empty on the sign-in screen. */
    private static String who(Context c) {
        com.rescuelink.app.models.User u = SessionManager.getCurrentUser();
        if (u != null) return u.getUsername();
        return com.rescuelink.app.net.Config.user(c);
    }

    public static int index(Context c) {
        String w = who(c);
        if (current < 0 || !w.equals(currentFor)) {
            currentFor = w;
            current = w.isEmpty() ? 0 : Math.max(0, Math.min(OPTIONS.length - 1, p(c).getInt(KEY + w, 0)));
        }
        return current;
    }

    /** Saves the colour for the signed-in account only. */
    public static void set(Context c, int index) {
        String w = who(c);
        current = Math.max(0, Math.min(OPTIONS.length - 1, index));
        currentFor = w;
        if (!w.isEmpty()) p(c).edit().putInt(KEY + w, current).apply();
    }

    /** Puts the chosen colours on this screen's theme. Call before its layout is inflated. */
    public static void apply(Activity a) {
        int i = index(a);
        a.getTheme().applyStyle(OPTIONS[i].style, true);
        APPLIED.put(a, i);
    }

    /** Screens left in the background while the colour changed redraw themselves when they come back. */
    public static void refreshIfStale(Activity a) {
        Integer was = APPLIED.get(a);
        if (was != null && was != index(a)) a.recreate();
    }
}
