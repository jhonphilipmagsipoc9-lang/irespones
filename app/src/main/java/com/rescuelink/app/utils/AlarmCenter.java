package com.rescuelink.app.utils;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rescuelink.app.activities.IncidentManageActivity;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.Incident;
import com.rescuelink.app.models.IncidentStatus;
import com.rescuelink.app.models.User;

import java.util.HashSet;
import java.util.Set;

/**
 * Decides who is alarmed when a citizen needs help, and keeps the alarm ringing until the person stops it.
 * Alarmed: the admin of the report's service (Police, Fire, DRRMO or Medical) and every responder. The closest
 * responder, who is put in charge first, is told so. The Command Center admin has no alarm.
 *
 * With the online service on, reports reach every phone within a few seconds (see CloudSync), so the alarm
 * rings on the closest responder's phone first and on the rest of the service only if nobody answers.
 */
public final class AlarmCenter {
    private static final String PREFS = "rescuelink_alarm";
    private static final String KEY = "acked";
    /** Reports older than this no longer ring on their own. */
    private static final long FRESH_MS = 30 * 60 * 1000L;
    /** The closest responder rings first; the others of the service ring only after this long without a reply. */
    private static final long BACKUP_AFTER_MS = 60 * 1000L;

    private static AlertDialog shown;

    private AlarmCenter() { }

    private static boolean waiting(Incident i) {
        IncidentStatus s = i.getStatus();
        return s == IncidentStatus.SUBMITTED || s == IncidentStatus.UNDER_REVIEW || s == IncidentStatus.ASSIGNED;
    }

    /** Should this person be alarmed about this report? */
    public static boolean shouldAlert(User me, Incident i) {
        if (me == null || i == null || !me.isActive() || !waiting(i)) return false;
        if (System.currentTimeMillis() - i.getCreatedAt() > FRESH_MS) return false;
        if (User.ROLE_ADMIN.equals(me.getRole())) {
            // Only the Police, Fire, DRRMO and Medical admins. The Command Center has no alarm.
            return me.isServiceAdmin() && DataRepository.inScope(me, i);
        }
        if (User.ROLE_RESPONDER.equals(me.getRole())) {
            String in = i.getResponderName();
            if (in == null) return true;                                // nobody has it yet: every responder
            if (in.equals(me.getFullName())) return true;               // the closest responder, put in charge first
            // Backup: if the closest responder has not set out within a minute, the rest of the same service ring too.
            com.rescuelink.app.models.StatusEvent e = i.findEvent(IncidentStatus.ASSIGNED);
            long since = System.currentTimeMillis() - (e == null ? i.getCreatedAt() : e.getTimeMillis());
            return i.getStatus() == IncidentStatus.ASSIGNED && since > BACKUP_AFTER_MS
                    && DataRepository.serviceOf(me).equals(DataRepository.serviceOfCategory(i.getCategory()));
        }
        return false;
    }

    private static Set<String> acked(Context c) {
        return new HashSet<>(c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY, new HashSet<String>()));
    }

    private static String key(String ref) { return SessionManager.username() + "|" + ref; }

    public static void acknowledge(Context c, String ref) {
        if (ref == null) return;
        Set<String> s = acked(c);
        s.add(key(ref));
        c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY, s).apply();
    }

    /** The first report that should be ringing for the signed-in person and has not been stopped yet. */
    public static Incident pending(Context c) {
        User me = SessionManager.getCurrentUser();
        if (me == null) return null;
        Set<String> done = acked(c);
        for (Incident i : DataRepository.getAllIncidents()) {
            if (shouldAlert(me, i) && !done.contains(key(i.getReference()))) return i;
        }
        return null;
    }

    /** Starts the alarm if something is waiting and nothing is ringing yet. */
    public static void check(Context c) {
        if (c == null || AlarmService.isRinging()) return;
        Incident i = pending(c);
        if (i != null) AlarmService.start(c, i.getReference());
    }

    public static void stopAll(Context c) { AlarmService.stop(c); }

    /** Call when an admin or responder screen is shown: rings if needed, and shows the Stop box while it rings. */
    public static void onScreenResumed(final Activity a, final String openRef) {
        User me = SessionManager.getCurrentUser();
        if (me == null || User.ROLE_CITIZEN.equals(me.getRole())) return;
        check(a);
        if (!AlarmService.isRinging()) return;
        try { if (shown != null && shown.isShowing()) shown.dismiss(); } catch (RuntimeException ignored) { }
        final String ref = AlarmService.currentRef();
        final Incident i = DataRepository.findIncident(ref);
        StringBuilder sb = new StringBuilder();
        if (i != null) {
            sb.append(i.displayTitle()).append('\n');
            if (!i.getLocation().isEmpty()) sb.append(i.getLocation()).append('\n');
            if (i.getResponderName() != null) {
                sb.append(i.getResponderName().equals(me.getFullName())
                        ? "\nYou are the closest responder. Go first."
                        : "\nClosest responder: " + i.getResponderName());
            }
        }
        AlertDialog.Builder b = new MaterialAlertDialogBuilder(a)
                .setTitle("HELP NEEDED")
                .setMessage(sb.toString())
                .setCancelable(false)
                .setPositiveButton("Stop alarm", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        acknowledge(a, ref);
                        AlarmService.stop(a);
                        check(a);               // another waiting report starts its own alarm
                    }
                });
        if (i != null && !i.getReference().equals(openRef)) {
            b.setNeutralButton("View report", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    Intent in = new Intent(a, IncidentManageActivity.class);
                    in.putExtra("ref", i.getReference());
                    a.startActivity(in);        // the alarm keeps ringing until Stop alarm is tapped
                }
            });
        }
        shown = b.create();
        shown.show();
    }
}
