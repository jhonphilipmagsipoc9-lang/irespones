package com.rescuelink.app.call;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.rescuelink.app.R;
import com.rescuelink.app.activities.CallActivity;
import com.rescuelink.app.data.CloudSync;
import com.rescuelink.app.models.User;
import com.rescuelink.app.net.Cloud;
import com.rescuelink.app.net.Config;
import com.rescuelink.app.utils.SessionManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** Notices when someone is calling the signed-in person and shows the incoming-call screen (or a notification). */
public final class IncomingCalls {
    private static final String CHANNEL = "incoming_call";
    private static final int NOTIFICATION_ID = 44;
    private static final long FRESH_MS = 45000;
    private static final Set<String> HANDLED = new HashSet<>();

    private IncomingCalls() { }

    /** Called by CloudSync on the main thread with whatever is waiting in this person's inbox. */
    public static void check(final Context app, String inboxJson) {
        if (inboxJson == null || inboxJson.isEmpty() || "null".equals(inboxJson)) return;
        final User me = SessionManager.getCurrentUser();
        if (me == null) return;
        JSONObject o;
        try { o = new JSONObject(inboxJson); } catch (JSONException e) { return; }
        final String id = o.optString("id", "");
        if (id.isEmpty() || HANDLED.contains(id)) return;
        HANDLED.add(id);
        if (CloudSync.serverNow() - o.optLong("t", 0) > FRESH_MS) return;      // an old call nobody answered
        final String from = o.optString("from", "");
        final String fromName = o.optString("fromName", from);
        if (CallSession.busy()) {                                              // already in a call
            final String base = Config.cloudUrl(app);
            Cloud.run(new Runnable() {
                @Override public void run() {
                    Cloud.put(base, "calls/" + id + "/state", "\"declined\"");
                    Cloud.delete(base, "inbox/" + Cloud.key(me.getUsername()));
                }
            });
            return;
        }
        final String base = Config.cloudUrl(app);
        Cloud.run(new Runnable() {
            @Override public void run() {
                final String body = Cloud.get(base, "calls/" + id);
                CloudSyncMain.post(new Runnable() {
                    @Override public void run() { ring(app, me, id, from, fromName, body); }
                });
            }
        });
    }

    private static void ring(Context app, User me, String id, String from, String fromName, String body) {
        if (body == null || body.isEmpty() || "null".equals(body) || CallSession.busy()) return;
        if (SessionManager.getCurrentUser() != me) return;
        JSONObject c;
        try { c = new JSONObject(body); } catch (JSONException e) { return; }
        if (!"ringing".equals(c.optString("state", "")) || !c.has("offer")) return;
        double lat = c.has("lat") ? c.optDouble("lat", Double.NaN) : Double.NaN;
        double lon = c.has("lon") ? c.optDouble("lon", Double.NaN) : Double.NaN;
        CallSession.prepareIncoming(app, id, from, fromName, c.optString("offer", ""), lat, lon,
                me.getUsername(), me.getFullName());
        Intent open = new Intent(app, CallActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (CloudSync.isForeground()) {
            try {
                app.startActivity(open);
                return;
            } catch (RuntimeException ignored) { }
        }
        notifyIncoming(app, open, fromName);
    }

    private static void notifyIncoming(Context app, Intent open, String fromName) {
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Incoming calls", NotificationManager.IMPORTANCE_HIGH);
            Uri ring = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ch.setSound(ring, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }
        PendingIntent pi = PendingIntent.getActivity(app, 1, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_phone)
                .setContentTitle("Incoming iResponse call")
                .setContentText(fromName + " is calling")
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setAutoCancel(true)
                .setTimeoutAfter(FRESH_MS)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .build();
        try { nm.notify(NOTIFICATION_ID, n); } catch (RuntimeException ignored) { }
    }

    static void cancelNotification(Context app) {
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIFICATION_ID);
    }

    /** Posts work to the main thread. */
    private static final class CloudSyncMain {
        private static final android.os.Handler H = new android.os.Handler(android.os.Looper.getMainLooper());
        static void post(Runnable r) { H.post(r); }
    }
}
