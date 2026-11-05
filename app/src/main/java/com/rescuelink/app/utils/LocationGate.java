package com.rescuelink.app.utils;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * The app needs the phone's location ON to find people and send help to the right place. This checks both the
 * permission and the phone's location switch, and asks for whichever is missing.
 */
public final class LocationGate {
    public static final int REQUEST_CODE = 4021;
    public static final int REQUEST_ENABLE = 4022;
    /** Each question is asked by itself once, then the "Turn on" box is the backup if the person said no. */
    private static boolean permissionAsked, switchPrompted;
    private static androidx.appcompat.app.AlertDialog showing;
    private static Activity owner;

    private LocationGate() { }

    /** True when the app may read the location and the phone's location switch is on. */
    public static boolean ready(Context c) {
        if (!LocationHelper.hasPermission(c)) return false;
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        try {
            if (Build.VERSION.SDK_INT >= 28) return lm.isLocationEnabled();
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Using the app: stays on screen until location is on. Call from onResume of the screens people live in, so it
     * clears itself when they come back from Settings.
     */
    public static void require(final Activity a) {
        if (ready(a)) {
            if (showing != null && showing.isShowing()) showing.dismiss();
            showing = null;
            switchPrompted = false;          // if it is turned off later, ask again
            return;
        }
        if (autoEnable(a)) return;           // the phone's own "allow / turn on" question is on screen
        if (showing != null && showing.isShowing()) {
            if (owner == a) return;
            try { showing.dismiss(); } catch (RuntimeException ignored) { }      // belonged to a screen that is gone
        }
        owner = a;
        showing = new MaterialAlertDialogBuilder(a)
                .setTitle("Turn on location")
                .setMessage("iResponse needs your location to be ON so responders can find you and so help goes to the right place. Turn it on to keep using the app.")
                .setCancelable(false)
                .setPositiveButton("Turn on", null)
                .create();
        showing.show();
        showing.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) { fix(a); }
        });
    }

    /** Emergency buttons: ask, but never stop the call. */
    public static void askBeforeEmergency(final Activity a, final Runnable go) {
        if (ready(a)) { go.run(); return; }
        new MaterialAlertDialogBuilder(a)
                .setTitle("Location is off")
                .setMessage("Turn on location so responders can see where you are. You can still call for help right now.")
                .setNegativeButton("Call anyway", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { go.run(); }
                })
                .setPositiveButton("Turn on", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { fix(a); }
                })
                .show();
    }

    /**
     * Turns location on by itself when the app opens: asks for the permission, then shows the phone's one-tap
     * "Turn on location?" box (no need to open Settings). Returns true when it just put a question on screen.
     */
    public static boolean autoEnable(Activity a) {
        if (ready(a)) { switchPrompted = false; return false; }
        if (!LocationHelper.hasPermission(a)) {
            if (permissionAsked) return false;
            permissionAsked = true;
            ActivityCompat.requestPermissions(a, LocationHelper.PERMISSIONS, REQUEST_CODE);
            return true;
        }
        if (switchPrompted) return false;
        switchPrompted = true;
        return promptSwitch(a, false);
    }

    /** Google Play services shows "Turn on device location?" with OK; one tap and GPS is on. */
    private static boolean promptSwitch(final Activity a, final boolean settingsFallback) {
        try {
            LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L).build();
            LocationSettingsRequest r = new LocationSettingsRequest.Builder()
                    .addLocationRequest(req).setAlwaysShow(true).build();
            LocationServices.getSettingsClient(a).checkLocationSettings(r)
                    .addOnFailureListener(a, new OnFailureListener() {
                        @Override public void onFailure(Exception e) {
                            if (e instanceof ResolvableApiException) {
                                try { ((ResolvableApiException) e).startResolutionForResult(a, REQUEST_ENABLE); return; }
                                catch (IntentSender.SendIntentException ignored) { }
                            }
                            if (settingsFallback) openSettings(a);
                        }
                    });
            return true;
        } catch (Throwable t) {                                   // no Google Play services on this phone
            if (settingsFallback) openSettings(a);
            return false;
        }
    }

    private static void openSettings(Activity a) {
        try { a.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); }
        catch (RuntimeException e) { a.startActivity(new Intent(Settings.ACTION_SETTINGS)); }
    }

    /** Hides the "Turn on location" box (signed out, or the screen it belonged to is closing). */
    public static void release(Activity a) {
        if (showing == null || (a != null && owner != a)) return;
        try { if (showing.isShowing()) showing.dismiss(); } catch (RuntimeException ignored) { }
        showing = null;
        owner = null;
    }

    private static void fix(Activity a) {
        if (!LocationHelper.hasPermission(a)) {
            // Said "don't ask again": Android will not show the question any more, so open the app's settings page.
            if (permissionAsked && !ActivityCompat.shouldShowRequestPermissionRationale(a, android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                try {
                    a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", a.getPackageName(), null)));
                    return;
                } catch (RuntimeException ignored) { }
            }
            permissionAsked = true;
            ActivityCompat.requestPermissions(a, LocationHelper.PERMISSIONS, REQUEST_CODE);
        } else {
            promptSwitch(a, true);
        }
    }
}
