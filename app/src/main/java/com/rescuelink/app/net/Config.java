package com.rescuelink.app.net;

import android.content.Context;
import android.content.SharedPreferences;

/** Who was signed in last time, and how the person likes to call for help. Stored only on this phone. */
public final class Config {
    private static final String PREFS = "rescuelink_net";
    /** Dialled when a station has no saved number. 911 is the national emergency hotline in the Philippines. */
    public static final String FALLBACK_NUMBER = "911";

    public static final String MODE_AUTO = "auto";
    public static final String MODE_PHONE = "phone";
    public static final String MODE_SMS = "sms";
    /** Free voice call over Wi-Fi or mobile data to the nearest responder who is online. */
    public static final String MODE_APP = "app";

    /**
     * Address of the online service (a Firebase Realtime Database). Put it here before building so every phone is
     * connected from the first start; there is no screen to type it. Leave empty to run offline only.
     * See FIREBASE_SETUP.md.
     */
    public static final String DEFAULT_CLOUD_URL = "https://irespons-f9784-default-rtdb.asia-southeast1.firebasedatabase.app";

    private Config() { }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String user(Context c) { return p(c).getString("user", ""); }
    public static boolean hasSession(Context c) { return !user(c).isEmpty(); }

    public static void saveSession(Context c, String username) {
        p(c).edit().putString("user", username).apply();
    }

    public static void clearSession(Context c) {
        p(c).edit().remove("user").remove("token").apply();
    }

    /** Proof from the server that this phone signed in online. Sent when an admin creates or manages accounts. */
    public static String token(Context c) { return p(c).getString("token", ""); }
    public static void saveToken(Context c, String token) { p(c).edit().putString("token", token == null ? "" : token).apply(); }

    /** Auto, Internet call, Phone or SMS. */
    public static String mode(Context c) {
        String m = p(c).getString("mode", MODE_AUTO);
        return MODE_PHONE.equals(m) || MODE_SMS.equals(m) || MODE_APP.equals(m) ? m : MODE_AUTO;
    }

    /** The online service address, without a trailing slash. Empty when the app runs offline only. */
    public static String cloudUrl(Context c) {
        String u = DEFAULT_CLOUD_URL;
        if (u.isEmpty()) u = p(c).getString("cloud_url", "");     // an address saved by an earlier version still works
        u = u == null ? "" : u.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    public static void setCloudUrl(Context c, String url) {
        p(c).edit().putString("cloud_url", url == null ? "" : url.trim()).apply();
    }

    public static boolean cloudEnabled(Context c) {
        String u = cloudUrl(c);
        return u.startsWith("https://") || u.startsWith("http://");
    }

    public static void setMode(Context c, String mode) { p(c).edit().putString("mode", mode).apply(); }
}
