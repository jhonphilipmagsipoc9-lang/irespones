package com.rescuelink.app.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.rescuelink.app.R;
import com.rescuelink.app.activities.IncidentChatActivity;
import com.rescuelink.app.activities.IncidentDetailActivity;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.Incident;
import com.rescuelink.app.models.IncidentMessage;
import com.rescuelink.app.models.StatusEvent;
import com.rescuelink.app.models.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phone notifications for new chat messages and for updates on a citizen's reports (accepted, assigned, on the
 * way, resolved...). Runs after every sync. The counts shown here are the same ones shown as badges in the app.
 */
public final class Notifier {
    private static final String CH_MESSAGES = "chat_messages";
    private static final String CH_REPORTS = "report_updates";

    /** Unread count we already told the person about, per report. A notification is only posted when it grows. */
    private static final Map<String, Integer> MSG_SEEN = new HashMap<>();
    private static final Map<String, Integer> UPD_SEEN = new HashMap<>();
    private static String forUser = "";
    private static boolean primed;

    private Notifier() { }

    /** Call after each sync, once the open screen has had its chance to mark things as read. */
    public static synchronized void check(Context app) {
        User me = SessionManager.getCurrentUser();
        if (app == null) return;
        if (me == null) { MSG_SEEN.clear(); UPD_SEEN.clear(); forUser = ""; primed = false; return; }
        if (!me.getUsername().equals(forUser)) {      // another person signed in: start fresh
            MSG_SEEN.clear(); UPD_SEEN.clear(); forUser = me.getUsername(); primed = false;
        }
        // The first look only records what is already waiting. Badges show it; a notification would be old news.
        boolean quiet = !primed;
        primed = true;

        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        ensureChannels(nm);
        boolean allowed = NotificationManagerCompat.from(app).areNotificationsEnabled();

        for (Incident i : DataRepository.chatThreadsFor(me)) {
            String ref = i.getReference();
            int unread = DataRepository.unreadMessages(me, i);
            int before = MSG_SEEN.containsKey(ref) ? MSG_SEEN.get(ref) : 0;
            MSG_SEEN.put(ref, unread);
            if (unread == 0) { nm.cancel(ref.hashCode()); continue; }
            if (unread > before && !quiet && allowed) postMessage(app, nm, i, unread);
        }

        for (Incident i : DataRepository.getIncidentsFor(me.getUsername())) {
            String ref = i.getReference();
            int unread = DataRepository.unreadUpdates(me, i);
            int before = UPD_SEEN.containsKey(ref) ? UPD_SEEN.get(ref) : 0;
            UPD_SEEN.put(ref, unread);
            if (unread == 0) { nm.cancel(("upd" + ref).hashCode()); continue; }
            if (unread > before && !quiet && allowed) postUpdate(app, nm, i, unread);
        }
    }

    private static void ensureChannels(NotificationManager nm) {
        if (Build.VERSION.SDK_INT < 26) return;
        nm.createNotificationChannel(new NotificationChannel(CH_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(CH_REPORTS, "Report updates", NotificationManager.IMPORTANCE_HIGH));
    }

    private static PendingIntent open(Context app, Intent i, int code) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(app, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void postMessage(Context app, NotificationManager nm, Incident i, int unread) {
        IncidentMessage last = DataRepository.lastMessage(i.getReference());
        String who = last == null ? "New message" : last.getSenderName();
        String text = last == null ? "You have a new message." : last.getText();
        Intent chat = new Intent(app, IncidentChatActivity.class).putExtra(IncidentChatActivity.EXTRA_REF, i.getReference());
        Notification n = new NotificationCompat.Builder(app, CH_MESSAGES)
                .setSmallIcon(R.drawable.ic_chat)
                .setContentTitle(unread > 1 ? who + " (" + unread + " new messages)" : who)
                .setContentText(text)
                .setSubText(i.getReference())
                .setNumber(unread)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open(app, chat, i.getReference().hashCode()))
                .build();
        try { nm.notify(i.getReference().hashCode(), n); } catch (RuntimeException ignored) { }
    }

    private static void postUpdate(Context app, NotificationManager nm, Incident i, int unread) {
        List<StatusEvent> h = i.getHistory();
        StatusEvent last = h.isEmpty() ? null : h.get(h.size() - 1);
        String title = last == null ? "Report updated" : headline(last, i);
        String text = last == null ? i.displayTitle() : last.getNote();
        if (text == null || text.trim().isEmpty()) text = i.displayTitle();
        Intent detail = new Intent(app, IncidentDetailActivity.class).putExtra("ref", i.getReference());
        Notification n = new NotificationCompat.Builder(app, CH_REPORTS)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(i.getReference())
                .setNumber(unread)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open(app, detail, ("upd" + i.getReference()).hashCode()))
                .build();
        try { nm.notify(("upd" + i.getReference()).hashCode(), n); } catch (RuntimeException ignored) { }
    }

    private static String headline(StatusEvent e, Incident i) {
        switch (e.getStatus()) {
            case UNDER_REVIEW: return "Your report is being reviewed";
            case ASSIGNED: return i.getResponderName() == null ? "Your report was accepted"
                    : i.getResponderName() + " accepted your report";
            case RESPONDING: return "Help is on the way";
            case ON_SCENE: return "Responder is on scene";
            case RESOLVED: return "Your report was resolved";
            case CANCELLED: return "Your report was cancelled";
            default: return "Report update";
        }
    }
}
