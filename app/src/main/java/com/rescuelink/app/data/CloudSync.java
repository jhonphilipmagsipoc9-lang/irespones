package com.rescuelink.app.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.rescuelink.app.models.AlertItem;
import com.rescuelink.app.models.Incident;
import com.rescuelink.app.models.IncidentMessage;
import com.rescuelink.app.models.IncidentStatus;
import com.rescuelink.app.models.User;
import com.rescuelink.app.net.Cloud;
import com.rescuelink.app.net.Config;
import com.rescuelink.app.net.Net;
import com.rescuelink.app.utils.SessionManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Connects the phones to each other through the online service (a Firebase Realtime Database).
 *
 * What is shared: who is online and where responders are (live tracking), reports, report chat messages and
 * broadcasts. Passwords are never uploaded; accounts stay on the phone where they were created.
 *
 * Network work runs on background threads; everything that touches {@link DataRepository} runs on the main
 * thread. Without an online service address (or without internet) nothing here does anything and the app
 * works exactly as before, on one phone.
 */
public final class CloudSync {
    public interface Listener {
        /** Called on the main thread after every sync. {@code changed} is true when new data arrived. */
        void onSynced(boolean changed);
    }

    private static final long WEEK_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long ONLINE_WINDOW_MS = 45000L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private static final Map<String, String> PUSHED_INCIDENTS = new ConcurrentHashMap<>();
    private static final Set<String> PUSHED_MESSAGES = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> PUSHED_ALERTS = new ConcurrentHashMap<>();
    private static final Map<String, String> PUSHED_STATIONS = new ConcurrentHashMap<>();
    private static volatile String pushedRoster = "";
    private static final ConcurrentLinkedQueue<String> PENDING_DELETES = new ConcurrentLinkedQueue<>();
    private static final Set<String> TOMBSTONES = ConcurrentHashMap.newKeySet();

    private static Context app;
    private static boolean started;
    private static boolean foreground;
    private static long lastTick;
    private static volatile long serverOffset;
    private static String lastUser;
    private static volatile boolean lastOk;

    private CloudSync() { }

    private static final Runnable TICK = new Runnable() {
        @Override public void run() {
            tick();
            MAIN.postDelayed(this, interval());
        }
    };

    /** Call once from Application.onCreate. */
    public static void start(Context c) {
        if (started) return;
        started = true;
        app = c.getApplicationContext();
        MAIN.postDelayed(TICK, 1500);
    }

    public static void addListener(Listener l) { if (l != null && !LISTENERS.contains(l)) LISTENERS.add(l); }
    public static void removeListener(Listener l) { LISTENERS.remove(l); }

    /** The app is on screen (true) or fully in the background (false). Syncing is faster while on screen. */
    public static void setForeground(boolean on) {
        boolean was = foreground;
        foreground = on;
        if (on && !was) poke();
    }

    public static boolean isForeground() { return foreground; }

    private static long interval() {
        if (foreground) return 2500;
        User me = SessionManager.getCurrentUser();
        return me != null && User.ROLE_RESPONDER.equals(me.getRole()) ? 5000 : 20000;
    }

    /** Something changed locally: send it now instead of waiting for the next round. */
    public static void poke() {
        if (!started) return;
        MAIN.post(new Runnable() {
            @Override public void run() {
                if (System.currentTimeMillis() - lastTick < 800) return;
                tick();
            }
        });
    }

    /** True when the online service is set up, the phone has internet and the last round worked. */
    public static boolean isOnline() {
        return started && lastOk && Config.cloudEnabled(app) && Net.hasInternet(app);
    }

    /** The online service's idea of "now", so phones with wrong clocks still agree. */
    public static long serverNow() { return System.currentTimeMillis() + serverOffset; }

    /** The address was changed: send everything again to the new place. */
    public static void reset() {
        PUSHED_INCIDENTS.clear();
        PUSHED_MESSAGES.clear();
        PUSHED_ALERTS.clear();
        PUSHED_STATIONS.clear();
        pushedRoster = "";
        TOMBSTONES.clear();
        lastOk = false;
        poke();
    }

    /** Something was deleted locally: delete it online too, and never take it back from a stale copy. */
    static void deleted(String path) {
        TOMBSTONES.add(path);
        PENDING_DELETES.add(path);
    }

    /** The person signed out: disappear from the online list at once. */
    public static void signedOut(final String username) {
        if (!started || username == null || username.isEmpty() || !Config.cloudEnabled(app)) return;
        final String base = Config.cloudUrl(app);
        final String key = Cloud.key(username);
        lastUser = null;
        Cloud.run(new Runnable() {
            @Override public void run() {
                Cloud.delete(base, "directory/" + key);
                Cloud.delete(base, "inbox/" + key);
            }
        });
    }

    // ------------------------------------------------------------------ one round
    private static void tick() {
        lastTick = System.currentTimeMillis();
        if (app == null || !Config.cloudEnabled(app) || !Net.hasInternet(app)) { lastOk = false; return; }
        final User me = SessionManager.getCurrentUser();
        if (me == null) {
            if (lastUser != null) { signedOut(lastUser); }
            return;
        }
        if (!BUSY.compareAndSet(false, true)) return;
        lastUser = me.getUsername();

        final String base = Config.cloudUrl(app);
        final String meKey = Cloud.key(me.getUsername());
        final String presence = presenceJson(me);
        final Map<String, String> incUp = changedIncidents();
        final Map<String, String> msgUp = newMessages();
        final Map<String, String> alertUp = changedAlerts();
        final Map<String, String> stationUp = changedStations(me);
        final String roster = rosterJson(me);

        Cloud.run(new Runnable() {
            @Override public void run() {
                String dir = null, inc = null, msg = null, alr = null, inbox = null, sta = null, ros = null;
                boolean ok;
                try {
                    ok = Cloud.put(base, "directory/" + meKey, presence);
                    if (ok) {
                        String p;
                        while ((p = PENDING_DELETES.poll()) != null) {
                            if (!Cloud.delete(base, p)) { PENDING_DELETES.add(p); break; }
                        }
                        for (Map.Entry<String, String> e : incUp.entrySet()) {
                            if (Cloud.put(base, "incidents/" + e.getKey(), e.getValue())) PUSHED_INCIDENTS.put(e.getKey(), e.getValue());
                        }
                        for (Map.Entry<String, String> e : msgUp.entrySet()) {
                            if (Cloud.put(base, "messages/" + e.getKey(), e.getValue())) PUSHED_MESSAGES.add(e.getKey());
                        }
                        for (Map.Entry<String, String> e : alertUp.entrySet()) {
                            if (Cloud.put(base, "alerts/" + e.getKey(), e.getValue())) PUSHED_ALERTS.put(e.getKey(), e.getValue());
                        }
                        for (Map.Entry<String, String> e : stationUp.entrySet()) {
                            if (Cloud.put(base, "stations/" + e.getKey(), e.getValue())) PUSHED_STATIONS.put(e.getKey(), e.getValue());
                        }
                        if (roster != null && !roster.equals(pushedRoster)) {
                            if (Cloud.put(base, "roster/" + meKey, roster)) pushedRoster = roster;
                        }
                        sta = Cloud.get(base, "stations");
                        ros = Cloud.get(base, "roster");
                        dir = Cloud.get(base, "directory");
                        inc = Cloud.get(base, "incidents");
                        msg = Cloud.get(base, "messages");
                        alr = Cloud.get(base, "alerts");
                        inbox = Cloud.get(base, "inbox/" + meKey);
                    }
                } catch (RuntimeException e) {
                    ok = false;
                }
                final boolean fok = ok;
                final String fDir = dir, fInc = inc, fMsg = msg, fAlr = alr, fInbox = inbox, fSta = sta, fRos = ros;
                MAIN.post(new Runnable() {
                    @Override public void run() {
                        try {
                            lastOk = fok;
                            boolean changed = false;
                            if (fok) {
                                changed |= applyStations(fSta);
                                changed |= applyDirectory(fDir);
                                changed |= applyRoster(fRos);
                                changed |= applyIncidents(fInc);
                                changed |= applyMessages(fMsg);
                                changed |= applyAlerts(fAlr);
                                com.rescuelink.app.call.IncomingCalls.check(app, fInbox);
                                if (changed) DataRepository.saveQuiet();
                            }
                            com.rescuelink.app.utils.AlarmCenter.check(app);
                            for (Listener l : LISTENERS) l.onSynced(changed);
                        } finally {
                            BUSY.set(false);
                        }
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------------ what to send
    private static String presenceJson(User me) {
        try {
            JSONObject o = new JSONObject();
            o.put("u", me.getUsername());
            o.put("n", me.getFullName());
            o.put("r", me.getRole());
            o.put("s", DataRepository.serviceOf(me));
            o.put("st", me.getStationName());
            o.put("d", me.getDutyStatus());
            o.put("on", true);
            o.put("ts", new JSONObject().put(".sv", "timestamp"));
            if (User.ROLE_RESPONDER.equals(me.getRole()) && me.hasPosition()) {
                o.put("lat", me.getLat());
                o.put("lon", me.getLon());
            }
            return o.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    private static final String[] DEFAULT_STATIONS = {"Bayawan District Hospital", "Bayawan City Police Station",
            "Bayawan City Fire Station (BFP)", "Bayawan City DRRMO"};

    private static boolean isDefaultStation(String name) {
        for (String d : DEFAULT_STATIONS) if (d.equalsIgnoreCase(name)) return true;
        return false;
    }

    /** Stations are shared so every phone, citizens included, sees the same ones. Citizens never change stations. */
    private static Map<String, String> changedStations(User me) {
        Map<String, String> out = new LinkedHashMap<>();
        if (me == null || User.ROLE_CITIZEN.equals(me.getRole())) return out;
        boolean admin = User.ROLE_ADMIN.equals(me.getRole());
        for (com.rescuelink.app.models.Station s : DataRepository.getStations()) {
            // Every phone starts with the four built-in stations, so only an admin sends those (to publish edits).
            if (!admin && isDefaultStation(s.getName())) continue;
            String k = Cloud.key(s.getName().toLowerCase(Locale.ROOT));
            if (TOMBSTONES.contains("stations/" + k)) continue;
            try {
                String json = Persistence.stationJson(s).toString();
                if (!json.equals(PUSHED_STATIONS.get(k))) out.put(k, json);
            } catch (JSONException ignored) { }
        }
        return out;
    }

    /**
     * A responder's last known place, kept after they sign out, so citizens and dispatch can see every responder
     * (offline ones show where they were last seen). Never includes a phone number.
     */
    private static String rosterJson(User me) {
        if (me == null || !User.ROLE_RESPONDER.equals(me.getRole())) return null;
        return presenceJson(me).replace(",\"on\":true", "");
    }

    private static boolean applyStations(String body) {
        JSONObject root = object(body);
        if (root == null) return false;
        boolean changed = false;
        Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (TOMBSTONES.contains("stations/" + k)) continue;
            JSONObject o = root.optJSONObject(k);
            if (o == null) continue;
            try {
                com.rescuelink.app.models.Station s = Persistence.parseStation(o);
                if (DataRepository.mergeStation(s)) changed = true;
                PUSHED_STATIONS.put(k, Persistence.stationJson(s).toString());
            } catch (JSONException ignored) { }
        }
        return changed;
    }

    private static boolean applyRoster(String body) {
        JSONObject root = object(body);
        if (root == null) return false;
        boolean changed = false;
        Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            JSONObject o = root.optJSONObject(keys.next());
            if (o == null) continue;
            double lat = o.has("lat") ? o.optDouble("lat", Double.NaN) : Double.NaN;
            double lon = o.has("lon") ? o.optDouble("lon", Double.NaN) : Double.NaN;
            changed |= DataRepository.mergeRoster(o.optString("u", ""), o.optString("n", ""), o.optString("s", ""),
                    o.optString("st", ""), o.optString("d", ""), lat, lon);
        }
        return changed;
    }

    private static String incidentText(Incident i) {
        try {
            JSONObject o = Persistence.incidentJson(i);
            o.remove("photo");                      // photos stay on the phone that took them
            return o.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    private static Map<String, String> changedIncidents() {
        Map<String, String> out = new LinkedHashMap<>();
        long cutoff = System.currentTimeMillis() - WEEK_MS;
        for (Incident i : DataRepository.incidentsSnapshot()) {
            if (i.getStatus() == IncidentStatus.DRAFT) continue;
            if (!i.isActive() && DataRepository.stamp(i) < cutoff) continue;
            String k = Cloud.key(i.getReference());
            if (TOMBSTONES.contains("incidents/" + k)) continue;
            String json = incidentText(i);
            if (json != null && !json.equals(PUSHED_INCIDENTS.get(k))) out.put(k, json);
        }
        return out;
    }

    private static String messageKey(IncidentMessage m) {
        return Cloud.key(m.getReference()) + "/" + m.getTimeMillis() + "_" + Cloud.key(m.getSenderUsername());
    }

    private static Map<String, String> newMessages() {
        Map<String, String> out = new LinkedHashMap<>();
        for (IncidentMessage m : DataRepository.messagesSnapshot()) {
            String k = messageKey(m);
            if (PUSHED_MESSAGES.contains(k) || TOMBSTONES.contains("incidents/" + Cloud.key(m.getReference()))) continue;
            try { out.put(k, Persistence.messageJson(m).toString()); } catch (JSONException ignored) { }
        }
        return out;
    }

    private static Map<String, String> changedAlerts() {
        Map<String, String> out = new LinkedHashMap<>();
        for (AlertItem a : DataRepository.alertsSnapshot()) {
            String k = Cloud.key(a.getId());
            if (TOMBSTONES.contains("alerts/" + k)) continue;
            try {
                String json = Persistence.alertJson(a).toString();
                if (!json.equals(PUSHED_ALERTS.get(k))) out.put(k, json);
            } catch (JSONException ignored) { }
        }
        return out;
    }

    // ------------------------------------------------------------------ what came back
    private static JSONObject object(String body) {
        if (body == null || body.isEmpty() || "null".equals(body)) return null;
        try { return new JSONObject(body); } catch (JSONException e) { return null; }
    }

    private static boolean applyDirectory(String body) {
        JSONObject root = object(body);
        if (root == null) return false;
        long maxTs = 0;
        Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            JSONObject o = root.optJSONObject(keys.next());
            if (o != null) maxTs = Math.max(maxTs, o.optLong("ts", 0));
        }
        if (maxTs > 0) serverOffset = maxTs - System.currentTimeMillis();
        boolean changed = false;
        Set<String> seen = new HashSet<>();
        keys = root.keys();
        while (keys.hasNext()) {
            JSONObject o = root.optJSONObject(keys.next());
            if (o == null) continue;
            String u = o.optString("u", "");
            if (u.isEmpty()) continue;
            boolean online = o.optBoolean("on", true) && maxTs - o.optLong("ts", 0) < ONLINE_WINDOW_MS;
            if (online) seen.add(u.toLowerCase(Locale.ROOT));
            double lat = o.has("lat") ? o.optDouble("lat", Double.NaN) : Double.NaN;
            double lon = o.has("lon") ? o.optDouble("lon", Double.NaN) : Double.NaN;
            changed |= DataRepository.mergePresence(u, o.optString("n", ""), o.optString("r", ""),
                    o.optString("s", ""), o.optString("st", ""), o.optString("d", ""), lat, lon, online);
        }
        changed |= DataRepository.markMissingOffline(seen);
        return changed;
    }

    private static boolean applyIncidents(String body) {
        JSONObject root = object(body);
        if (root == null) return false;
        boolean changed = false;
        long cutoff = System.currentTimeMillis() - WEEK_MS;
        Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (TOMBSTONES.contains("incidents/" + k)) continue;
            JSONObject o = root.optJSONObject(k);
            if (o == null) continue;
            try {
                Incident r = Persistence.parseIncident(o);
                if (!r.isActive() && DataRepository.stamp(r) < cutoff) continue;
                if (DataRepository.mergeIncident(r)) {
                    changed = true;
                    Incident local = DataRepository.findIncident(r.getReference());
                    String text = local == null ? null : incidentText(local);
                    if (text != null) PUSHED_INCIDENTS.put(Cloud.key(r.getReference()), text);   // do not echo it back
                }
            } catch (JSONException | IllegalArgumentException ignored) { }
        }
        return changed;
    }

    private static boolean applyMessages(String body) {
        JSONObject root = object(body);
        if (root == null) return false;
        boolean changed = false;
        Iterator<String> refs = root.keys();
        while (refs.hasNext()) {
            String ref = refs.next();
            if (TOMBSTONES.contains("incidents/" + ref)) continue;
            JSONObject group = root.optJSONObject(ref);
            if (group == null) continue;
            Iterator<String> ks = group.keys();
            while (ks.hasNext()) {
                String k = ks.next();
                JSONObject o = group.optJSONObject(k);
                if (o == null) continue;
                try {
                    IncidentMessage m = Persistence.parseMessage(o);
                    if (DataRepository.mergeMessage(m)) changed = true;
                    PUSHED_MESSAGES.add(ref + "/" + k);
                } catch (JSONException ignored) { }
            }
        }
        return changed;
    }

    private static boolean applyAlerts(String body) {
        JSONObject root = object(body);
        if (root == null) return false;
        boolean changed = false;
        Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (TOMBSTONES.contains("alerts/" + k)) continue;
            JSONObject o = root.optJSONObject(k);
            if (o == null) continue;
            try {
                AlertItem a = Persistence.parseAlert(o);
                if (DataRepository.mergeAlert(a)) { changed = true; PUSHED_ALERTS.put(k, Persistence.alertJson(a).toString()); }
            } catch (JSONException ignored) { }
        }
        return changed;
    }
}
