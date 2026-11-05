package com.rescuelink.app.data;

import android.content.Context;

import com.rescuelink.app.models.AlertItem;
import com.rescuelink.app.models.Incident;
import com.rescuelink.app.models.IncidentMessage;
import com.rescuelink.app.models.IncidentStatus;
import com.rescuelink.app.models.LogEntry;
import com.rescuelink.app.models.StatusEvent;
import com.rescuelink.app.models.Station;
import com.rescuelink.app.models.User;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Saves everything the app knows (accounts, stations, reports, messages, broadcasts) to one JSON file in the
 * app's private storage, so nothing is lost when the app is closed. Accounts keep only a salted password hash.
 */
final class Persistence {
    private static final String FILE = "rescuelink_data.json";
    private static final int VERSION = 1;

    /** Everything read back from disk. */
    static final class Snapshot {
        final List<User> users = new ArrayList<>();
        final List<Station> stations = new ArrayList<>();
        final List<Incident> incidents = new ArrayList<>();
        final List<AlertItem> alerts = new ArrayList<>();
        final Set<String> readAlerts = new HashSet<>();
        final List<LogEntry> log = new ArrayList<>();
        final List<IncidentMessage> messages = new ArrayList<>();
        final Map<String, Long> lastRead = new HashMap<>();
        int referenceCounter, draftCounter, alertCounter;
    }

    private Persistence() { }

    private static File file(Context c) { return new File(c.getFilesDir(), FILE); }

    // ---------- write ----------
    static synchronized void write(Context c, JSONObject root) {
        File target = file(c);
        File tmp = new File(c.getFilesDir(), FILE + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(root.toString().getBytes("UTF-8"));
            out.getFD().sync();
        } catch (IOException e) {
            return;
        }
        if (!tmp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(target);
        }
    }

    static JSONObject userJson(User u) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", u.getFullName());
        o.put("age", u.getAge());
        o.put("address", u.getAddress());
        o.put("username", u.getUsername());
        o.put("salt", u.getSalt());
        o.put("hash", u.getPasswordHash());
        o.put("created", u.getCreatedAt());
        o.put("contact", u.getContact());
        o.put("email", u.getEmail());
        o.put("role", u.getRole());
        o.put("active", u.isActive());
        o.put("duty", u.getDutyStatus());
        o.put("blood", u.getBloodType());
        o.put("allergies", u.getAllergies());
        o.put("conditions", u.getConditions());
        if (u.getPhotoUri() != null) o.put("photo", u.getPhotoUri());
        o.put("station", u.getStationName());
        o.put("service", u.getService());
        o.put("idFile", u.getIdPhotoFile());
        o.put("faceFile", u.getFacePhotoFile());
        o.put("faceOk", u.isFaceVerified());
        o.put("verifiedAt", u.getVerifiedAt());
        if (u.hasPosition()) { o.put("lat", u.getLat()); o.put("lon", u.getLon()); }
        return o;
    }

    static JSONObject stationJson(Station s) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", s.getName());
        o.put("category", s.getCategory());
        o.put("address", s.getAddress());
        o.put("lat", s.getLat());
        o.put("lon", s.getLon());
        o.put("available", s.isAvailable());
        o.put("phone", s.getPhone());
        return o;
    }

    /** What is saved on this phone: the shared fields plus the local picture path (never sent online). */
    static JSONObject stationLocalJson(Station s) throws JSONException {
        JSONObject o = stationJson(s);
        if (!s.getPhotoUri().isEmpty()) o.put("photo", s.getPhotoUri());
        return o;
    }

    static JSONObject incidentJson(Incident i) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("ref", i.getReference());
        o.put("owner", i.getOwnerUsername());
        o.put("category", i.getCategory());
        o.put("title", i.getTitle());
        o.put("description", i.getDescription());
        o.put("location", i.getLocation());
        o.put("landmark", i.getLandmark());
        o.put("severity", i.getSeverity());
        o.put("affected", i.getAffected());
        o.put("status", i.getStatus().name());
        o.put("created", i.getCreatedAt());
        if (i.getAssignedStation() != null) o.put("station", i.getAssignedStation());
        if (i.getResponderName() != null) o.put("responder", i.getResponderName());
        o.put("feedback", i.isFeedbackGiven());
        if (i.hasPosition()) { o.put("lat", i.getLat()); o.put("lon", i.getLon()); }
        o.put("sos", i.isSos());
        if (i.getPhotoUri() != null) o.put("photo", i.getPhotoUri());
        JSONArray h = new JSONArray();
        for (StatusEvent e : i.getHistory()) {
            JSONObject eo = new JSONObject();
            eo.put("status", e.getStatus().name());
            eo.put("time", e.getTimeMillis());
            eo.put("note", e.getNote());
            h.put(eo);
        }
        o.put("history", h);
        return o;
    }

    static JSONObject alertJson(AlertItem a) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", a.getId());
        o.put("title", a.getTitle());
        o.put("message", a.getMessage());
        o.put("level", a.getLevel());
        o.put("time", a.getTimeMillis());
        o.put("author", a.getAuthor());
        return o;
    }

    static JSONObject logJson(LogEntry e) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("time", e.getTimeMillis());
        o.put("actor", e.getActor());
        o.put("action", e.getAction());
        return o;
    }

    static JSONObject messageJson(IncidentMessage m) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("ref", m.getReference());
        o.put("username", m.getSenderUsername());
        o.put("name", m.getSenderName());
        o.put("role", m.getSenderRole());
        o.put("text", m.getText());
        o.put("time", m.getTimeMillis());
        return o;
    }

    // ---------- read ----------
    /** Returns the saved data, or null when nothing usable has been saved yet. */
    static synchronized Snapshot read(Context c) {
        File f = file(c);
        if (!f.exists()) return null;
        try {
            String text;
            try (FileInputStream in = new FileInputStream(f)) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
                text = buf.toString("UTF-8");
            }
            JSONObject root = new JSONObject(text);
            Snapshot s = new Snapshot();
            s.referenceCounter = root.optInt("referenceCounter", 1000);
            s.draftCounter = root.optInt("draftCounter", 0);
            s.alertCounter = root.optInt("alertCounter", 0);

            JSONArray users = root.optJSONArray("users");
            for (int k = 0; users != null && k < users.length(); k++) s.users.add(parseUser(users.getJSONObject(k)));

            JSONArray stations = root.optJSONArray("stations");
            for (int k = 0; stations != null && k < stations.length(); k++) s.stations.add(parseStation(stations.getJSONObject(k)));

            JSONArray incidents = root.optJSONArray("incidents");
            for (int k = 0; incidents != null && k < incidents.length(); k++) s.incidents.add(parseIncident(incidents.getJSONObject(k)));

            JSONArray alerts = root.optJSONArray("alerts");
            for (int k = 0; alerts != null && k < alerts.length(); k++) s.alerts.add(parseAlert(alerts.getJSONObject(k)));
            JSONArray read = root.optJSONArray("readAlerts");
            for (int k = 0; read != null && k < read.length(); k++) s.readAlerts.add(read.getString(k));

            JSONArray log = root.optJSONArray("log");
            for (int k = 0; log != null && k < log.length(); k++) {
                JSONObject o = log.getJSONObject(k);
                s.log.add(new LogEntry(o.getLong("time"), o.optString("actor", ""), o.optString("action", "")));
            }

            JSONArray messages = root.optJSONArray("messages");
            for (int k = 0; messages != null && k < messages.length(); k++) s.messages.add(parseMessage(messages.getJSONObject(k)));
            JSONObject lr = root.optJSONObject("lastRead");
            if (lr != null) {
                java.util.Iterator<String> keys = lr.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    s.lastRead.put(key, lr.getLong(key));
                }
            }
            return s;
        } catch (IOException | JSONException | IllegalArgumentException e) {
            // Keep the unreadable file for inspection instead of silently overwriting it on the next save.
            //noinspection ResultOfMethodCallIgnored
            f.renameTo(new File(c.getFilesDir(), FILE + ".bad"));
            return null;
        }
    }

    // ---------- parsing ----------
    static User parseUser(JSONObject o) throws JSONException {
        User u = User.restore(o.getString("name"), o.optInt("age", 18), o.optString("address", ""),
                o.getString("username"), o.optString("salt", ""), o.optString("hash", ""),
                o.optString("contact", ""), o.getString("role"));
        u.setCreatedAt(o.optLong("created", System.currentTimeMillis()));
        u.setActive(o.optBoolean("active", true));
        u.setEmail(o.optString("email", ""));
        u.setDutyStatus(o.optString("duty", User.DUTY_OFF));
        u.setBloodType(o.optString("blood", ""));
        u.setAllergies(o.optString("allergies", ""));
        u.setConditions(o.optString("conditions", ""));
        if (o.has("photo")) u.setPhotoUri(o.getString("photo"));
        u.setStationName(o.optString("station", ""));
        u.setService(o.optString("service", ""));
        u.setVerification(o.optString("idFile", ""), o.optString("faceFile", ""),
                o.optBoolean("faceOk", false), o.optLong("verifiedAt", 0L));
        if (o.has("lat") && o.has("lon")) u.setPosition(o.getDouble("lat"), o.getDouble("lon"));
        return u;
    }

    static Station parseStation(JSONObject o) throws JSONException {
        Station st = new Station(o.getString("name"), o.getString("category"),
                o.optString("address", ""), o.getDouble("lat"), o.getDouble("lon"),
                o.optBoolean("available", true), o.optString("phone", ""));
        st.setPhotoUri(o.optString("photo", ""));
        return st;
    }

    static Incident parseIncident(JSONObject o) throws JSONException {
        Incident i = new Incident(o.getString("ref"), o.getString("owner"));
        i.setCategory(o.optString("category", ""));
        i.setTitle(o.optString("title", ""));
        i.setDescription(o.optString("description", ""));
        i.setLocation(o.optString("location", ""));
        i.setLandmark(o.optString("landmark", ""));
        i.setSeverity(o.optString("severity", "Moderate"));
        i.setAffected(o.optInt("affected", 1));
        i.setStatus(IncidentStatus.valueOf(o.getString("status")));
        i.setCreatedAt(o.optLong("created", System.currentTimeMillis()));
        i.setAssignedStation(o.has("station") ? o.getString("station") : null);
        i.setResponderName(o.has("responder") ? o.getString("responder") : null);
        i.setFeedbackGiven(o.optBoolean("feedback", false));
        if (o.has("lat") && o.has("lon")) i.setPosition(o.getDouble("lat"), o.getDouble("lon"));
        i.setSos(o.optBoolean("sos", false));
        if (o.has("photo")) i.setPhotoUri(o.getString("photo"));
        JSONArray h = o.optJSONArray("history");
        for (int e = 0; h != null && e < h.length(); e++) {
            JSONObject eo = h.getJSONObject(e);
            i.addEvent(new StatusEvent(IncidentStatus.valueOf(eo.getString("status")),
                    eo.getLong("time"), eo.optString("note", "")));
        }
        return i;
    }

    static AlertItem parseAlert(JSONObject o) throws JSONException {
        return new AlertItem(o.getString("id"), o.getString("title"), o.optString("message", ""),
                o.optInt("level", 0), o.optLong("time", 0), o.optString("author", ""));
    }

    static IncidentMessage parseMessage(JSONObject o) throws JSONException {
        return new IncidentMessage(o.getString("ref"), o.getString("username"),
                o.optString("name", ""), o.optString("role", ""), o.optString("text", ""), o.getLong("time"));
    }

    static int version() { return VERSION; }
}
