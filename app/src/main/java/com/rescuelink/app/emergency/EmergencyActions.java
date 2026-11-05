package com.rescuelink.app.emergency;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.telephony.SmsManager;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rescuelink.app.R;
import com.rescuelink.app.call.CallLauncher;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.Incident;
import com.rescuelink.app.models.Station;
import com.rescuelink.app.models.User;
import com.rescuelink.app.net.Config;
import com.rescuelink.app.net.Net;
import com.rescuelink.app.utils.GeoUtils;
import com.rescuelink.app.utils.LocationHelper;
import com.rescuelink.app.utils.SessionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The one-tap emergency. Tapping Police, Fire, DRRMO or Hospital does the fastest thing that can work with
 * what the phone has right now:
 *   internet   - Wi-Fi or mobile data: a free voice call to the nearest responder who is online (no load needed)
 *   phone call - a SIM with load: dials the nearest station of that service
 *   SMS        - message only: sends the location and a short text
 * The person can also pin one of the two on the Home screen. If the chosen way is not available the app
 * says so and picks the other one instead of doing nothing. A signed-in citizen also gets the emergency
 * recorded in their reports, with location and Medical ID.
 */
public final class EmergencyActions {

    public enum Svc {
        POLICE("Police", "police", "Police", R.drawable.ic_cat_police, R.color.svc_police, new String[]{"Police"}),
        FIRE("Fire", "fire", "Fire", R.drawable.ic_cat_fire, R.color.svc_fire, new String[]{"Fire"}),
        DRRMO("DRRMO", "drrmo", "DRRMO", R.drawable.ic_cat_drrmo, R.color.svc_drrmo, new String[]{"DRRMO", "Rescue"}),
        MEDICAL("Medical", "medical", "Medical", R.drawable.ic_cat_medical, R.color.svc_medical, new String[]{"Hospital", "Medical"});

        public final String label, key, category;
        public final int icon, color;
        final String[] types;

        Svc(String label, String key, String category, int icon, int color, String[] types) {
            this.label = label; this.key = key; this.category = category;
            this.icon = icon; this.color = color; this.types = types;
        }

        public static Svc fromKey(String key) {
            for (Svc s : values()) if (s.key.equals(key)) return s;
            return null;
        }
    }

    private EmergencyActions() { }

    // ------------------------------------------------------------------ choosing the way
    /** The way that will be used right now, or null when the phone has no way to reach anyone. */
    public static String effectiveMode(Context c) {
        String pref = Config.mode(c);
        if (!Net.canUseSim(c)) return null;
        return Config.MODE_SMS.equals(pref) ? Config.MODE_SMS : Config.MODE_PHONE;
    }

    public static String describe(Context c) {
        String pref = Config.mode(c);
        String eff = effectiveMode(c);
        boolean net = CallLauncher.available(c);
        if (net && !Config.MODE_PHONE.equals(pref) && !Config.MODE_SMS.equals(pref)) {
            return "Tapping a service calls the nearest online responder over Wi-Fi or data, free"
                    + (eff == null ? "." : ". If nobody is online, it uses your SIM.");
        }
        if (eff == null) return "No working SIM found and no internet service. Connect to Wi-Fi, or ask someone nearby to call 911.";
        String how = Config.MODE_SMS.equals(eff) ? "by SMS" : "by phone call";
        if (Config.MODE_AUTO.equals(pref)) return "Auto: tapping a service will call " + how + ".";
        return "Tapping a service will call " + how + ".";
    }

    /** Tap a service: one step to help. */
    public static void start(Activity a, Svc s) { start(a, s, null); }

    /** Same, and runs {@code done} once the call or SMS has been handed off (or the dialog was closed). */
    public static void start(final Activity a, final Svc s, final Runnable done) {
        com.rescuelink.app.utils.LocationGate.askBeforeEmergency(a, new Runnable() {
            @Override public void run() { begin(a, s, done); }
        });
    }

    private static void begin(Activity a, Svc s, final Runnable done) {
        String pref = Config.mode(a);
        boolean wantsNet = !Config.MODE_PHONE.equals(pref) && !Config.MODE_SMS.equals(pref);
        if (wantsNet && CallLauncher.available(a) && internetCall(a, s, done)) return;
        String mode = effectiveMode(a);
        if (mode == null) {
            new MaterialAlertDialogBuilder(a)
                    .setTitle("No way to reach help")
                    .setMessage("This phone has no working SIM, so it cannot call or send an SMS, and no responder is online for an internet call. Connect to Wi-Fi or mobile data, move to where there is a signal, or ask someone nearby to call 911.")
                    .setPositiveButton(R.string.ok, null)
                    .setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                        @Override public void onDismiss(android.content.DialogInterface d) { after(done); }
                    }).show();
            return;
        }
        if (Config.MODE_SMS.equals(mode)) sms(a, s, done);
        else phone(a, s, done);
    }

    /**
     * Free call over the internet to the closest responder who is online. The emergency is also saved as a report,
     * which reaches every responder's phone. Returns false when the call cannot be placed (nobody of that service
     * is online, or the phone has a SIM to fall back on), so the caller can use phone or SMS instead.
     */
    private static boolean internetCall(Activity a, Svc s, Runnable done) {
        double lat = Double.NaN, lon = Double.NaN;
        if (LocationHelper.refresh(a) && GeoUtils.hasFix()) { lat = GeoUtils.myLat(); lon = GeoUtils.myLon(); }
        User target = DataRepository.nearestLiveResponder(s.category, lat, lon);
        if (target == null) {
            if (effectiveMode(a) != null) return false;            // nobody online but the SIM can still reach help
            Incident i = record(a, s, "app");
            new MaterialAlertDialogBuilder(a)
                    .setTitle("Help is being alerted")
                    .setMessage(i != null
                            ? "No responder of this service is online right now. Your emergency and location were sent, and the nearest responder's phone will ring as soon as they are online. If you can, ask someone nearby to call 911."
                            : "No responder of this service is online right now and this phone has no SIM. Sign in so your emergency can be recorded, or ask someone nearby to call 911.")
                    .setPositiveButton(R.string.ok, null)
                    .setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                        @Override public void onDismiss(android.content.DialogInterface d) { after(done); }
                    }).show();
            return true;
        }
        Incident i = record(a, s, "app");
        User callee = target;
        if (i != null && i.getResponderName() != null) {       // the responder put in charge, if they are online
            User inCharge = DataRepository.findUserByName(i.getResponderName());
            if (inCharge != null && inCharge.isLive()) callee = inCharge;
        }
        boolean ok = CallLauncher.call(a, callee, lat, lon);
        after(done);
        return ok;
    }

    private static void after(Runnable done) { if (done != null) done.run(); }

    // ------------------------------------------------------------------ phone call
    public static void phone(Activity a, Svc s) { phone(a, s, null); }

    public static void phone(Activity a, Svc s, Runnable done) {
        String number = numberFor(s);
        record(a, s, "phone");
        String clean = number.replaceAll("[^0-9+*#]", "");
        Uri uri = Uri.parse("tel:" + Uri.encode(clean));
        try {
            if (Net.canCallDirectly(a)) {
                Toast.makeText(a, "Calling " + s.label + " (" + number + ")", Toast.LENGTH_SHORT).show();
                a.startActivity(new Intent(Intent.ACTION_CALL, uri));
            } else {
                a.startActivity(new Intent(Intent.ACTION_DIAL, uri));      // number already filled in; one more tap
            }
        } catch (SecurityException | ActivityNotFoundException e) {
            Toast.makeText(a, "Could not start the call. Dial " + number + " yourself.", Toast.LENGTH_LONG).show();
        }
        after(done);
    }

    // ------------------------------------------------------------------ SMS
    public static void sms(Activity a, Svc s) { sms(a, s, null); }

    public static void sms(Activity a, Svc s, final Runnable done) {
        String number = numberFor(s);
        record(a, s, "sms");
        String body = smsBody(a, s);
        String clean = number.replaceAll("[^0-9+]", "");
        try {
            if (Net.canSendSms(a)) {
                SmsManager sm = Build.VERSION.SDK_INT >= 31 ? a.getSystemService(SmsManager.class) : SmsManager.getDefault();
                ArrayList<String> parts = sm.divideMessage(body);
                sm.sendMultipartTextMessage(clean, null, parts, null, null);
                new MaterialAlertDialogBuilder(a)
                        .setTitle("Emergency SMS sent")
                        .setMessage("Your location was sent to " + s.label + " (" + number + "). Stay safe and keep your phone on.")
                        .setPositiveButton(R.string.ok, null)
                        .setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                            @Override public void onDismiss(android.content.DialogInterface d) { after(done); }
                        }).show();
            } else {
                Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(clean)));
                i.putExtra("sms_body", body);
                a.startActivity(i);
                after(done);
            }
        } catch (RuntimeException e) {
            Toast.makeText(a, "Could not send the SMS. Try a phone call.", Toast.LENGTH_LONG).show();
            after(done);
        }
    }

    private static String smsBody(Context c, Svc s) {
        com.rescuelink.app.models.User me = SessionManager.getCurrentUser();
        StringBuilder sb = new StringBuilder("EMERGENCY - ").append(s.label.toUpperCase(java.util.Locale.ROOT)).append(". I need help.");
        if (me != null) sb.append(" Name: ").append(me.getFullName()).append('.');
        if (LocationHelper.refresh(c) && GeoUtils.hasFix()) {
            sb.append(" Location: ").append(GeoUtils.formatCoords(GeoUtils.myLat(), GeoUtils.myLon()))
                    .append(' ').append(com.rescuelink.app.utils.MapHelper.locationLink(GeoUtils.myLat(), GeoUtils.myLon()));
        } else if (me != null && !me.getAddress().isEmpty()) {
            sb.append(" Address: ").append(me.getAddress());
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ numbers and records
    /** Nearest available station of this service that has a saved number, otherwise the national hotline. */
    public static String numberFor(Svc s) {
        List<Station> list = new ArrayList<>();
        for (Station st : DataRepository.getStations()) {
            if (!st.isAvailable() || st.getPhone() == null || st.getPhone().trim().isEmpty()) continue;
            for (String t : s.types) if (t.equals(st.getCategory())) { list.add(st); break; }
        }
        if (list.isEmpty()) return Config.FALLBACK_NUMBER;
        Collections.sort(list, new Comparator<Station>() {
            @Override public int compare(Station x, Station y) {
                return Double.compare(GeoUtils.distanceFromMeKm(x.getLat(), x.getLon()), GeoUtils.distanceFromMeKm(y.getLat(), y.getLon()));
            }
        });
        return list.get(0).getPhone().trim();
    }

    /** A signed-in citizen's emergency is saved as a critical report, so it shows in their history and the Command Center. */
    public static Incident record(Context c, Svc s, String mode) {
        User me = SessionManager.getCurrentUser();
        if (me == null || !User.ROLE_CITIZEN.equals(me.getRole())) return null;
        double lat = Double.NaN, lon = Double.NaN;
        if (LocationHelper.refresh(c) && GeoUtils.hasFix()) {
            lat = GeoUtils.myLat();
            lon = GeoUtils.myLon();
        }
        Incident made = DataRepository.createEmergency(me, s.category, s.label, mode, lat, lon);
        if (made != null) com.rescuelink.app.utils.LocationShareService.startIfNeeded(c);   // responder follows the citizen live
        return made;
    }
}
