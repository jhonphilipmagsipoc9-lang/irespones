package com.rescuelink.app.net;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.telephony.TelephonyManager;

import androidx.core.content.ContextCompat;

/** What kind of connection the phone has right now, so the emergency button can pick the fastest way to reach help. */
public final class Net {
    private Net() { }

    /** True when the phone has a working internet connection (Wi-Fi or mobile data). */
    public static boolean hasInternet(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    public static boolean onWifi(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network n = cm.getActiveNetwork();
        NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    /** True when the phone can place a normal call or send an SMS (it has a SIM that is ready). */
    public static boolean canUseSim(Context c) {
        if (!c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) return false;
        TelephonyManager tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
        return tm != null && tm.getSimState() == TelephonyManager.SIM_STATE_READY;
    }

    public static boolean has(Context c, String permission) {
        return ContextCompat.checkSelfPermission(c, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean canCallDirectly(Context c) { return has(c, Manifest.permission.CALL_PHONE); }
    public static boolean canSendSms(Context c) { return has(c, Manifest.permission.SEND_SMS); }
}
