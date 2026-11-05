package com.rescuelink.app.data;

import android.content.Context;

import com.rescuelink.app.R;
import com.rescuelink.app.models.AlertItem;
import com.rescuelink.app.models.Incident;
import com.rescuelink.app.models.IncidentCategory;
import com.rescuelink.app.models.IncidentMessage;
import com.rescuelink.app.models.IncidentStatus;
import com.rescuelink.app.models.LogEntry;
import com.rescuelink.app.models.StatusEvent;
import com.rescuelink.app.models.Station;
import com.rescuelink.app.models.User;
import com.rescuelink.app.utils.FaceVerifier;
import com.rescuelink.app.utils.GeoUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Data store shared by every screen. It lives in memory while the app runs and is saved to the device
 * (see {@link Persistence}) so accounts, stations, reports and messages survive closing the app.
 */
public final class DataRepository {
    private static final List<User> USERS = new ArrayList<>();
    private static final List<Incident> INCIDENTS = new ArrayList<>();
    private static final List<Station> STATIONS = new ArrayList<>();
    private static final List<IncidentCategory> CATEGORIES = new ArrayList<>();
    private static final List<AlertItem> ALERTS = new ArrayList<>();
    private static final Set<String> READ_ALERTS = new HashSet<>();
    private static final List<LogEntry> LOG = new ArrayList<>();
    private static final List<IncidentMessage> MESSAGES = new ArrayList<>();
    private static final Map<String, Long> LAST_READ = new HashMap<>();
    private static Context appContext;
    private static int referenceCounter;
    private static int draftCounter;
    private static int alertCounter;

    static {
        seedCategories();
        referenceCounter = 1000;
    }

    private DataRepository() { }

    // ---------- saving ----------
    /** Username and password of the Command Center account created on the very first start. */
    public static final String DEFAULT_ADMIN_USERNAME = "100100";      // sign-in number of the Command Center
    private static final String LEGACY_ADMIN_USERNAME = "admin";
    public static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    /** Call once at startup. Loads what was saved on this device; on a first run the starter data is created. */
    public static synchronized void init(Context context) {
        appContext = context.getApplicationContext();
        Persistence.Snapshot snap = Persistence.read(appContext);
        if (snap == null) {
            seedFirstRun();
            save();
            return;
        }
        USERS.clear();
        // Accounts that came from the old server version have no password on this phone, so nobody could sign in to them.
        for (User u : snap.users) if (u.hasPassword()) USERS.add(u);
        STATIONS.clear();
        STATIONS.addAll(snap.stations);
        INCIDENTS.clear();
        INCIDENTS.addAll(snap.incidents);
        ALERTS.clear();
        ALERTS.addAll(snap.alerts);
        READ_ALERTS.clear();
        READ_ALERTS.addAll(snap.readAlerts);
        LOG.clear();
        LOG.addAll(snap.log);
        MESSAGES.clear();
        MESSAGES.addAll(snap.messages);
        LAST_READ.clear();
        LAST_READ.putAll(snap.lastRead);
        referenceCounter = Math.max(1000, snap.referenceCounter);
        draftCounter = snap.draftCounter;
        alertCounter = snap.alertCounter;
        migrateLegacyAdmin();
        boolean hasCommandCenter = false;
        for (User u : USERS) if (u.isCommandCenter()) hasCommandCenter = true;
        if (!hasCommandCenter) {      // never leave the phone without a Command Center account
            USERS.add(newDefaultAdmin());
        }
        seedDummyAccounts();
        save();
    }

    /** Seed dummy accounts for testing all services (Police, Fire, DRRMO, Medical) and civilian role. */
    private static void seedDummyAccounts() {
        // Civilian account
        if (!usernameExists("09170000001")) {
            User civilian = new User("Juan Dela Cruz", 25, "Bayawan City", "09170000001",
                    "123456", "09170000001", User.ROLE_CITIZEN);
            civilian.setBloodType("O+");
            civilian.setAllergies("None");
            civilian.setConditions("None");
            civilian.setVerification("", "", true, System.currentTimeMillis());
            USERS.add(civilian);
        }

        // Responders for all services
        if (!usernameExists("200001")) {
            User policeResp = new User("Officer Police", 30, "Bayawan City", "200001",
                    "123456", "09170000002", User.ROLE_RESPONDER);
            policeResp.setService("Police");
            policeResp.setStationName("Bayawan City Police Station");
            policeResp.setPosition(9.3650, 122.8046);
            policeResp.setDutyStatus(User.DUTY_ON);
            policeResp.setVerification("", "", true, System.currentTimeMillis());
            USERS.add(policeResp);
        }

        if (!usernameExists("200002")) {
            User fireResp = new User("Firefighter Fire", 30, "Bayawan City", "200002",
                    "123456", "09170000003", User.ROLE_RESPONDER);
            fireResp.setService("Fire");
            fireResp.setStationName("Bayawan City Fire Station (BFP)");
            fireResp.setPosition(9.3632, 122.8058);
            fireResp.setDutyStatus(User.DUTY_ON);
            fireResp.setVerification("", "", true, System.currentTimeMillis());
            USERS.add(fireResp);
        }

        if (!usernameExists("200003")) {
            User drrmoResp = new User("Responder DRRMO", 30, "Bayawan City", "200003",
                    "123456", "09170000004", User.ROLE_RESPONDER);
            drrmoResp.setService("DRRMO");
            drrmoResp.setStationName("Bayawan City DRRMO");
            drrmoResp.setPosition(9.3656, 122.8053);
            drrmoResp.setDutyStatus(User.DUTY_ON);
            drrmoResp.setVerification("", "", true, System.currentTimeMillis());
            USERS.add(drrmoResp);
        }

        if (!usernameExists("200004")) {
            User medicResp = new User("Medic Medical", 30, "Bayawan City", "200004",
                    "123456", "09170000005", User.ROLE_RESPONDER);
            medicResp.setService("Medical");
            medicResp.setStationName("Bayawan District Hospital");
            medicResp.setPosition(9.3668, 122.8062);
            medicResp.setDutyStatus(User.DUTY_ON);
            medicResp.setVerification("", "", true, System.currentTimeMillis());
            USERS.add(medicResp);
        }

        // A second citizen and a second responder per service, so "closest responder" can be tried with real choice.
        if (!usernameExists("09170000010")) {
            User c2 = new User("Maria Santos", 31, "Bayawan City", "09170000010",
                    "123456", "09170000010", User.ROLE_CITIZEN);
            c2.setBloodType("A+");
            c2.setAllergies("Penicillin");
            c2.setConditions("None");
            c2.setVerification("", "", true, System.currentTimeMillis());
            USERS.add(c2);
        }
        addDummyResponder("200005", "Officer Police 2", "09170000011", "Police", "Bayawan City Police Station", 9.3702, 122.8100);
        addDummyResponder("200006", "Firefighter Fire 2", "09170000012", "Fire", "Bayawan City Fire Station (BFP)", 9.3590, 122.8010);
        addDummyResponder("200007", "Responder DRRMO 2", "09170000013", "DRRMO", "Bayawan City DRRMO", 9.3720, 122.8020);
        addDummyResponder("200008", "Medic Medical 2", "09170000014", "Medical", "Bayawan District Hospital", 9.3600, 122.8110);

        // Service Admins for all services
        if (!usernameExists("100001")) {
            User policeAdmin = new User("Police Admin", 35, "Bayawan City", "100001",
                    "123456", "09170000006", User.ROLE_ADMIN);
            policeAdmin.setService("Police");
            policeAdmin.setStationName("Bayawan City Police Station");
            USERS.add(policeAdmin);
        }

        if (!usernameExists("100002")) {
            User fireAdmin = new User("Fire Admin", 35, "Bayawan City", "100002",
                    "123456", "09170000007", User.ROLE_ADMIN);
            fireAdmin.setService("Fire");
            fireAdmin.setStationName("Bayawan City Fire Station (BFP)");
            USERS.add(fireAdmin);
        }

        if (!usernameExists("100003")) {
            User drrmoAdmin = new User("DRRMO Admin", 35, "Bayawan City", "100003",
                    "123456", "09170000008", User.ROLE_ADMIN);
            drrmoAdmin.setService("DRRMO");
            drrmoAdmin.setStationName("Bayawan City DRRMO");
            USERS.add(drrmoAdmin);
        }

        if (!usernameExists("100004")) {
            User medicAdmin = new User("Medical Admin", 35, "Bayawan City", "100004",
                    "123456", "09170000009", User.ROLE_ADMIN);
            medicAdmin.setService("Medical");
            medicAdmin.setStationName("Bayawan District Hospital");
            USERS.add(medicAdmin);
        }
    }

    private static void addDummyResponder(String number, String name, String contact, String service,
                                          String station, double lat, double lon) {
        if (usernameExists(number)) return;
        User r = new User(name, 30, "Bayawan City", number, "123456", contact, User.ROLE_RESPONDER);
        r.setService(service);
        r.setStationName(station);
        r.setPosition(lat, lon);
        r.setDutyStatus(User.DUTY_ON);
        r.setVerification("", "", true, System.currentTimeMillis());
        USERS.add(r);
    }

    /** The old Command Center signed in as "admin". Move it to the sign-in number and keep its password. */
    private static void migrateLegacyAdmin() {
        for (int k = 0; k < USERS.size(); k++) {
            User u = USERS.get(k);
            if (u.isCommandCenter() && LEGACY_ADMIN_USERNAME.equalsIgnoreCase(u.getUsername()) && !usernameExists(DEFAULT_ADMIN_USERNAME)) {
                User n = User.restore(u.getFullName(), u.getAge(), u.getAddress(), DEFAULT_ADMIN_USERNAME,
                        u.getSalt(), u.getPasswordHash(), u.getContact(), u.getRole());
                n.setCreatedAt(u.getCreatedAt());
                n.setActive(u.isActive());
                n.setService(u.getService());
                USERS.set(k, n);
                save();
                return;
            }
        }
    }

    /** Sign-in: the number typed on the login page. Matches the sign-in number first, then a saved mobile number (older accounts). */
    public static User findUserByLogin(String typed) {
        String n = com.rescuelink.app.utils.Validator.normalizeNumber(typed);
        User u = findUser(n);
        if (u != null) return u;
        for (User x : USERS) if (!n.isEmpty() && n.equals(x.getContact())) return x;
        return null;
    }

    /** A free 6-digit sign-in number for a new responder or admin. */
    public static String generateStaffNumber() {
        java.security.SecureRandom r = new java.security.SecureRandom();
        for (int k = 0; k < 200; k++) {
            String n = String.valueOf(100000 + r.nextInt(900000));
            if (!usernameExists(n)) return n;
        }
        return String.valueOf(System.currentTimeMillis() % 900000 + 100000);
    }

    private static User newDefaultAdmin() {
        return new User("Command Center", 30, "Command Center", DEFAULT_ADMIN_USERNAME,
                DEFAULT_ADMIN_PASSWORD, "", User.ROLE_ADMIN);
    }

    /** First start: the Command Center account and four starter stations (the admin can edit or delete them). */
    private static void seedFirstRun() {
        USERS.clear(); STATIONS.clear(); INCIDENTS.clear(); ALERTS.clear(); READ_ALERTS.clear();
        LOG.clear(); MESSAGES.clear(); LAST_READ.clear();
        referenceCounter = 1000; draftCounter = 0; alertCounter = 0;
        USERS.add(newDefaultAdmin());
        STATIONS.add(new Station("Bayawan District Hospital", "Hospital", "Bayawan City, Negros Oriental", 9.3668, 122.8062, true, ""));
        STATIONS.add(new Station("Bayawan City Police Station", "Police", "Poblacion, Bayawan City", 9.3650, 122.8046, true, ""));
        STATIONS.add(new Station("Bayawan City Fire Station (BFP)", "Fire", "Poblacion, Bayawan City", 9.3632, 122.8058, true, ""));
        STATIONS.add(new Station("Bayawan City DRRMO", "DRRMO", "City Hall area, Bayawan City", 9.3656, 122.8053, true, ""));
        seedDummyAccounts();
    }

    /** Writes everything to the device and nudges the online sync so other phones see the change quickly. */
    public static void save() {
        saveQuiet();
        CloudSync.poke();
    }

    /** Writes everything to the device. Cheap enough to call after every change and whenever a screen pauses. */
    static synchronized void saveQuiet() {
        if (appContext == null) return;
        try {
            JSONObject root = new JSONObject();
            root.put("version", Persistence.version());
            root.put("referenceCounter", referenceCounter);
            root.put("draftCounter", draftCounter);
            root.put("alertCounter", alertCounter);
            JSONArray users = new JSONArray();
            for (User u : USERS) users.put(Persistence.userJson(u));
            root.put("users", users);
            JSONArray stations = new JSONArray();
            for (Station st : STATIONS) stations.put(Persistence.stationLocalJson(st));
            root.put("stations", stations);
            JSONArray incidents = new JSONArray();
            for (Incident i : INCIDENTS) incidents.put(Persistence.incidentJson(i));
            root.put("incidents", incidents);
            JSONArray alerts = new JSONArray();
            for (AlertItem a : ALERTS) alerts.put(Persistence.alertJson(a));
            root.put("alerts", alerts);
            JSONArray read = new JSONArray();
            for (String id : READ_ALERTS) read.put(id);
            root.put("readAlerts", read);
            JSONArray log = new JSONArray();
            for (LogEntry e : LOG) log.put(Persistence.logJson(e));
            root.put("log", log);
            JSONArray messages = new JSONArray();
            for (IncidentMessage m : MESSAGES) messages.put(Persistence.messageJson(m));
            root.put("messages", messages);
            JSONObject lastRead = new JSONObject();
            for (Map.Entry<String, Long> e : LAST_READ.entrySet()) lastRead.put(e.getKey(), e.getValue());
            root.put("lastRead", lastRead);
            Persistence.write(appContext, root);
        } catch (JSONException ignored) { }
    }

    // ---------- users ----------
    public static boolean usernameExists(String username) {
        String needle = username.toLowerCase(Locale.ROOT);
        for (User u : USERS) {
            if (u.getUsername().toLowerCase(Locale.ROOT).equals(needle)) return true;
        }
        return false;
    }

    public static void addUser(User user) {
        USERS.add(user);
        log("Account created: " + user.getFullName() + " (" + user.getRole() + ")");
        save();
    }

    /** Reports refer to responders by name, so keep them pointing at a responder who renamed themselves. */
    public static void renameResponder(String oldName, String newName) {
        if (oldName == null || oldName.equals(newName)) return;
        for (Incident i : INCIDENTS) {
            if (oldName.equals(i.getResponderName())) i.setResponderName(newName);
        }
    }

    public static int countUsersByRole(String role) {
        int n = 0;
        for (User u : USERS) if (u.getRole().equals(role)) n++;
        return n;
    }

    public static int countDisabledUsers() {
        int n = 0;
        for (User u : USERS) if (!u.isActive()) n++;
        return n;
    }

    /** Removes every disabled non-admin account. Returns how many were removed. */
    public static int deleteDisabledUsers() {
        int n = 0;
        for (User u : new ArrayList<>(USERS)) {
            if (!u.isActive() && !User.ROLE_ADMIN.equals(u.getRole())) {
                deleteUser(u);
                n++;
            }
        }
        return n;
    }

    /** Removes an account. Reports it filed are deleted, and reports it was handling go back to review. */
    public static void deleteUser(User user) {
        if (user == null || !USERS.remove(user)) return;
        String name = user.getFullName();
        if (appContext != null) FaceVerifier.deleteFiles(appContext, user);
        boolean responder = User.ROLE_RESPONDER.equals(user.getRole());
        log("Account deleted: " + name + " (" + user.getRole() + ")");
        for (Incident i : new ArrayList<>(INCIDENTS)) {
            if (i.getOwnerUsername() != null && i.getOwnerUsername().equalsIgnoreCase(user.getUsername())) {
                freeResponder(i);
                INCIDENTS.remove(i);
                dropMessages(i.getReference());
                CloudSync.deleted("incidents/" + com.rescuelink.app.net.Cloud.key(i.getReference()));
            } else if (responder && name.equals(i.getResponderName())) {
                i.setResponderName(null);
                IncidentStatus st = i.getStatus();
                if (st == IncidentStatus.ASSIGNED || st == IncidentStatus.RESPONDING || st == IncidentStatus.ON_SCENE) {
                    updateStatus(i, IncidentStatus.UNDER_REVIEW, name + " was removed. Waiting for a new responder.");
                } else {
                    addNote(i, name + " was removed from this report.");
                }
            }
        }
        save();
    }
    public static List<User> getUsers() { return new ArrayList<>(USERS); }

    public static User findUser(String username) {
        for (User u : USERS) if (u.getUsername().equalsIgnoreCase(username)) return u;
        return null;
    }

    public static User findUserByName(String fullName) {
        if (fullName == null) return null;
        for (User u : USERS) if (u.getFullName().equals(fullName)) return u;
        return null;
    }

    public static List<User> getUsersByRole(String role) {
        List<User> out = new ArrayList<>();
        for (User u : USERS) if (u.getRole().equals(role)) out.add(u);
        return out;
    }

    public static int countOnDutyResponders() {
        int n = 0;
        for (User u : USERS) {
            if (User.ROLE_RESPONDER.equals(u.getRole()) && u.isActive() && !User.DUTY_OFF.equals(u.getDutyStatus())) n++;
        }
        return n;
    }

    // ---------- services: Police, Fire, DRRMO, Medical ----------
    /** Which service a station category belongs to ("Hospital" is Medical, "Rescue" is DRRMO). Empty if none. */
    public static String serviceOfCategory(String category) {
        if (category == null) return "";
        switch (category) {
            case "Police": return "Police";
            case "Fire": return "Fire";
            case "DRRMO": case "Rescue": return "DRRMO";
            case "Medical": case "Hospital": return "Medical";
            default: return "";
        }
    }

    /** The service of a responder or service admin. Older responders fall back to the type of their station. */
    public static String serviceOf(User u) {
        if (u == null) return "";
        if (!u.getService().isEmpty()) return u.getService();
        if (User.ROLE_RESPONDER.equals(u.getRole()) && !u.getStationName().isEmpty()) {
            Station s = findStation(u.getStationName());
            if (s != null) return serviceOfCategory(s.getCategory());
        }
        return "";
    }

    /** True when the viewer is an admin limited to one service. */
    public static boolean isScoped(User viewer) { return viewer != null && viewer.isServiceAdmin(); }

    /** Can this report be seen by that viewer? Command Center and non-admins are not limited here. */
    public static boolean inScope(User viewer, Incident i) {
        if (!isScoped(viewer)) return true;
        return viewer.getService().equals(i.getCategory());
    }

    /** Every report this admin may see: all of them for the Command Center, one service for a service admin. */
    public static List<Incident> getAllIncidentsFor(User viewer) {
        List<Incident> out = new ArrayList<>();
        for (Incident i : getAllIncidents()) if (inScope(viewer, i)) out.add(i);
        return out;
    }

    /** Accounts an admin can see: everyone for the Command Center; own service's responders for a service admin. */
    public static List<User> getUsersFor(User viewer) {
        List<User> out = new ArrayList<>();
        for (User u : USERS) {
            if (u.isRemote()) continue;          // accounts that live on another phone cannot be managed from here
            if (!isScoped(viewer) || u == viewer || inTeam(viewer, u)) out.add(u);
        }
        return out;
    }

    /** A responder who belongs to this service admin: same station, or (until the admin sets one up) same service. */
    public static boolean inTeam(User admin, User responder) {
        if (admin == null || responder == null || !User.ROLE_RESPONDER.equals(responder.getRole())) return false;
        if (!admin.getService().equals(serviceOf(responder))) return false;
        return admin.getStationName().isEmpty() || admin.getStationName().equals(responder.getStationName());
    }

    /** Responders of one service, or every responder when the service is empty. */
    public static List<User> getRespondersIn(String service) {
        List<User> out = new ArrayList<>();
        for (User u : USERS) {
            if (!User.ROLE_RESPONDER.equals(u.getRole())) continue;
            if (service == null || service.isEmpty() || service.equals(serviceOf(u))) out.add(u);
        }
        return out;
    }

    /** Stations a responder of this service can join (evacuation and relief sites are left out). */
    public static List<Station> getStationsForService(String service) {
        List<Station> out = new ArrayList<>();
        for (Station s : getResponderStations()) {
            if (service == null || service.isEmpty() || service.equals(serviceOfCategory(s.getCategory()))) out.add(s);
        }
        return out;
    }

    /** Station types a new station of this service may have. */
    public static String[] stationTypesOfService(String service) {
        if ("Police".equals(service)) return new String[]{"Police"};
        if ("Fire".equals(service)) return new String[]{"Fire"};
        if ("DRRMO".equals(service)) return new String[]{"DRRMO", "Rescue"};
        if ("Medical".equals(service)) return new String[]{"Hospital", "Medical"};
        return new String[0];
    }

    /** What an admin may do to an account: the Command Center manages everyone but itself; a service admin its own responders. */
    public static boolean canManage(User actor, User target) {
        if (actor == null || target == null || actor == target) return false;
        if (actor.isCommandCenter()) return !target.isCommandCenter();
        if (actor.isServiceAdmin()) {
            return inTeam(actor, target);
        }
        return false;
    }

    private static long lastPositionSave;

    /** A responder's phone reports where it is. Everyone who may see responders sees the new spot. */
    public static void updatePosition(User u, double lat, double lon) {
        if (u == null) return;
        u.setPosition(lat, lon);
        long now = System.currentTimeMillis();
        if (now - lastPositionSave > 10000) {
            lastPositionSave = now;
            save();
        }
    }

    // ---------- categories: the four services people can call ----------
    private static void seedCategories() {
        CATEGORIES.add(new IncidentCategory("Police", R.drawable.ic_cat_police, "#E3ECF7"));
        CATEGORIES.add(new IncidentCategory("Fire", R.drawable.ic_cat_fire, "#FBEAE4"));
        CATEGORIES.add(new IncidentCategory("DRRMO", R.drawable.ic_cat_drrmo, "#E2F1EA"));
        CATEGORIES.add(new IncidentCategory("Medical", R.drawable.ic_cat_medical, "#FBE9EB"));
    }

    public static List<IncidentCategory> getCategories() { return new ArrayList<>(CATEGORIES); }

    public static List<String> getCategoryNames() {
        List<String> names = new ArrayList<>();
        for (IncidentCategory c : CATEGORIES) names.add(c.getName());
        return names;
    }

    /** The kind of station best suited to a report category. */
    public static String stationTypeFor(String category) {
        if (category == null) return "DRRMO";
        switch (category) {
            case "Police": return "Police";
            case "Fire": return "Fire";
            case "Medical": return "Medical";
            default: return "DRRMO";
        }
    }

    // ---------- stations ----------
    public static List<Station> getStations() { return new ArrayList<>(STATIONS); }

    /** Stations ordered by distance from the device's last known position. */
    public static List<Station> getStationsByDistance() {
        List<Station> copy = new ArrayList<>(STATIONS);
        Collections.sort(copy, new java.util.Comparator<com.rescuelink.app.models.Station>() {
            @Override
            public int compare(com.rescuelink.app.models.Station a, com.rescuelink.app.models.Station b) {
                return Double.compare(
                GeoUtils.distanceFromMeKm(a.getLat(), a.getLon()),
                GeoUtils.distanceFromMeKm(b.getLat(), b.getLon()));
            }
        });
        return copy;
    }

    /** True when a station of category {@code have} can serve a need of type {@code want}. Hospitals serve medical needs. */
    public static boolean categoryMatches(String have, String want) {
        if (have == null || want == null) return false;
        if (have.equals(want)) return true;
        if ("Medical".equals(want) && "Hospital".equals(have)) return true;
        return "DRRMO".equals(want) && "Rescue".equals(have);
    }

    /** Stations a responder can belong to: everything except evacuation and relief sites. */
    public static List<Station> getResponderStations() {
        List<Station> out = new ArrayList<>();
        for (Station s : STATIONS) {
            if ("Evacuation".equals(s.getCategory()) || "Relief".equals(s.getCategory())) continue;
            out.add(s);
        }
        return out;
    }

    /** Label shown in pickers, for example "Eastside Police Assistance Desk (Police)". */
    public static String stationLabel(Station s) { return s.getName() + " (" + s.getCategory() + ")"; }

    /** Finds a responder station from its picker label, or null. */
    public static Station findResponderStationByLabel(String label) {
        if (label == null) return null;
        for (Station s : getResponderStations()) if (stationLabel(s).equals(label)) return s;
        return null;
    }

    /** Nearest available response station (no evacuation or relief sites) to a point, or null. */
    public static Station nearestResponseStation(double lat, double lon) {
        Station best = null;
        double bestKm = Double.MAX_VALUE;
        for (Station s : getResponderStations()) {
            if (!s.isAvailable() || s.getPhone() == null || s.getPhone().trim().isEmpty()) continue;
            double km = GeoUtils.distanceKm(lat, lon, s.getLat(), s.getLon());
            if (km < bestKm) { bestKm = km; best = s; }
        }
        return best;
    }

    /** Responders who belong to a station. */
    public static List<User> getRespondersAt(String stationName) {
        List<User> out = new ArrayList<>();
        for (User u : USERS) {
            if (User.ROLE_RESPONDER.equals(u.getRole()) && u.getStationName().equals(stationName)) out.add(u);
        }
        return out;
    }

    public static void addStation(Station s) {
        STATIONS.add(s);
        log("Station added: " + s.getName());
        save();
    }

    /**
     * The station a responder describes while registering or editing their profile. If a station with that name
     * already exists the responder simply joins it; otherwise a new one is created at the chosen spot.
     * The responder's own phone number is used as the station contact until the admin saves a different one.
     */
    public static Station stationForResponder(String name, String category, String address,
                                              double lat, double lon, String phone) {
        Station existing = findStationIgnoreCase(name);
        if (existing != null) return existing;
        Station s = new Station(name.trim(), category, address, lat, lon, true, phone == null ? "" : phone);
        addStation(s);
        return s;
    }

    /** Changes a station's details and keeps reports and responders that point at its old name in step. */
    public static void updateStation(Station s, String name, String category, String address,
                                     double lat, double lon) {
        String old = s.getName();
        if (!old.equals(name)) {
            for (Incident i : INCIDENTS) if (old.equals(i.getAssignedStation())) i.setAssignedStation(name);
            for (User u : USERS) if (old.equals(u.getStationName())) u.setStationName(name);
        }
        s.setDetails(name, category, address, s.getPhone());
        s.setLocation(lat, lon);
        log("Station edited: " + name);
        save();
    }

    public static Station findStationIgnoreCase(String name) {
        if (name == null) return null;
        String needle = name.trim();
        for (Station s : STATIONS) if (s.getName().equalsIgnoreCase(needle)) return s;
        return null;
    }

    public static void deleteStation(Station s) {
        if (s == null || !STATIONS.remove(s)) return;
        for (Incident i : INCIDENTS) {
            if (s.getName().equals(i.getAssignedStation())) i.setAssignedStation(null);
        }
        for (User u : USERS) if (s.getName().equals(u.getStationName())) u.setStationName("");
        CloudSync.deleted("stations/" + com.rescuelink.app.net.Cloud.key(s.getName().toLowerCase(java.util.Locale.ROOT)));
        StationPhotos.removed(com.rescuelink.app.RescueLinkApp.appContext(), s);
        log("Station deleted: " + s.getName());
        save();
    }

    public static Station findStation(String name) {
        if (name == null) return null;
        for (Station s : STATIONS) if (s.getName().equals(name)) return s;
        return null;
    }

    /** Station types a citizen may send a report of this category to, most fitting first. */
    public static String[] stationTypesFor(String category) {
        if (category == null) return new String[0];
        switch (category) {
            case "Police": return new String[]{"Police"};
            case "Fire": return new String[]{"Fire"};
            case "DRRMO": return new String[]{"DRRMO", "Rescue"};
            case "Medical": return new String[]{"Hospital", "Medical"};
            default: return new String[0];     // reports saved before the four-service change: any station
        }
    }

    /** Available stations that fit a report category, nearest to the given point first. */
    public static List<Station> stationsForCategory(String category, final double lat, final double lon) {
        String[] types = stationTypesFor(category);
        List<Station> out = new ArrayList<>();
        for (Station s : getResponderStations()) {
            if (!s.isAvailable()) continue;
            boolean fits = types.length == 0;
            for (String t : types) if (t.equals(s.getCategory())) fits = true;
            if (fits) out.add(s);
        }
        Collections.sort(out, new java.util.Comparator<Station>() {
            @Override
            public int compare(Station a, Station b) {
                return Double.compare(GeoUtils.distanceKm(lat, lon, a.getLat(), a.getLon()),
                        GeoUtils.distanceKm(lat, lon, b.getLat(), b.getLon()));
            }
        });
        return out;
    }

    /** Nearest available station of the right type to a point, or null. */
    public static Station nearestStationFor(String category, double lat, double lon) {
        String type = stationTypeFor(category);
        Station best = null;
        double bestKm = Double.MAX_VALUE;
        for (Station s : STATIONS) {
            if (!s.isAvailable() || !categoryMatches(s.getCategory(), type)) continue;
            double km = GeoUtils.distanceKm(lat, lon, s.getLat(), s.getLon());
            if (km < bestKm) { bestKm = km; best = s; }
        }
        return best;
    }

    // ---------- incidents ----------
    private static String defaultNote(IncidentStatus s) {
        switch (s) {
            case SUBMITTED: return "Report received.";
            case UNDER_REVIEW: return "Operations desk is reviewing the report.";
            case ASSIGNED: return "A responder team has been assigned.";
            case RESPONDING: return "Responders are on the way.";
            case ON_SCENE: return "Responders have arrived at the location.";
            case RESOLVED: return "Incident resolved.";
            default: return s.getLabel();
        }
    }

    public static List<Incident> getAllIncidents() {
        List<Incident> out = new ArrayList<>();
        for (Incident i : INCIDENTS) if (i.getStatus() != IncidentStatus.DRAFT) out.add(i);
        Collections.sort(out, new java.util.Comparator<com.rescuelink.app.models.Incident>() {
            @Override
            public int compare(com.rescuelink.app.models.Incident a, com.rescuelink.app.models.Incident b) {
                return Long.compare(b.getCreatedAt(), a.getCreatedAt());
            }
        });
        return out;
    }

    public static List<Incident> getIncidentsFor(String username) {
        List<Incident> result = new ArrayList<>();
        for (Incident i : INCIDENTS) {
            if (i.getOwnerUsername().equalsIgnoreCase(username)) result.add(i);
        }
        Collections.sort(result, new java.util.Comparator<com.rescuelink.app.models.Incident>() {
            @Override
            public int compare(com.rescuelink.app.models.Incident a, com.rescuelink.app.models.Incident b) {
                return Long.compare(b.getCreatedAt(), a.getCreatedAt());
            }
        });
        return result;
    }

    /** Incidents assigned to a responder, newest first. */
    public static List<Incident> getAssignedTo(String responderName) {
        List<Incident> out = new ArrayList<>();
        for (Incident i : getAllIncidents()) {
            if (responderName != null && responderName.equals(i.getResponderName())) out.add(i);
        }
        return out;
    }

    /** Active incidents that nobody has taken yet. */
    public static List<Incident> getOpenIncidents() {
        List<Incident> out = new ArrayList<>();
        for (Incident i : getAllIncidents()) {
            if (i.isActive() && i.getResponderName() == null) out.add(i);
        }
        return out;
    }

    /** Every responder can see every active incident, so anyone can spot who needs help. The nearest responder is put in charge. */
    public static boolean visibleTo(Incident i, User responder) {
        return i != null && responder != null && User.ROLE_RESPONDER.equals(responder.getRole());
    }

    /**
     * The responder who should take this incident: the closest one who is active, shares a position and is not
     * Busy. Responders of the incident's own service come first; if none is free, the closest of any service.
     */
    public static User nearestResponder(Incident i) {
        if (i == null || !i.hasPosition()) return null;
        String svc = serviceOfCategory(i.getCategory());
        // When any responder is online right now, only online responders are sent. Otherwise fall back to
        // the saved positions, so the app still works on one phone without the online service.
        boolean anyLive = false;
        for (User u : USERS) {
            if (User.ROLE_RESPONDER.equals(u.getRole()) && u.isActive() && u.hasPosition()
                    && !User.DUTY_BUSY.equals(u.getDutyStatus()) && u.isLive()) { anyLive = true; break; }
        }
        User bestSame = null, bestAny = null;
        double dSame = Double.MAX_VALUE, dAny = Double.MAX_VALUE;
        for (User u : USERS) {
            if (!User.ROLE_RESPONDER.equals(u.getRole()) || !u.isActive() || !u.hasPosition()
                    || User.DUTY_BUSY.equals(u.getDutyStatus())) continue;
            if (anyLive && !u.isLive()) continue;
            double d = GeoUtils.distanceKm(i.getLat(), i.getLon(), u.getLat(), u.getLon());
            if (d < dAny) { dAny = d; bestAny = u; }
            if (!svc.isEmpty() && svc.equals(serviceOf(u)) && d < dSame) { dSame = d; bestSame = u; }
        }
        return bestSame != null ? bestSame : bestAny;
    }

    /** The closest responder of a service who is online right now, or null. Used to place an internet call. */
    public static User nearestLiveResponder(String category, double lat, double lon) {
        String svc = serviceOfCategory(category);
        boolean fix = !Double.isNaN(lat) && !Double.isNaN(lon);
        User best = null;
        double bestKm = Double.MAX_VALUE;
        for (User u : USERS) {
            if (!User.ROLE_RESPONDER.equals(u.getRole()) || !u.isActive() || !u.isLive()) continue;
            if (!svc.isEmpty() && !svc.equals(serviceOf(u))) continue;
            double d = fix && u.hasPosition() ? GeoUtils.distanceKm(lat, lon, u.getLat(), u.getLon()) : 1000.0;
            if (best == null || d < bestKm) { bestKm = d; best = u; }
        }
        return best;
    }

    /**
     * The spot distances to a report are measured from: its own pin, or when it has none (an SMS SOS, say) the
     * station answering it. Returns {lat, lon} or null when neither is known.
     */
    public static double[] referencePoint(Incident i) {
        if (i == null) return null;
        if (i.hasPosition()) return new double[]{i.getLat(), i.getLon()};
        Station s = i.getAssignedStation() == null ? null : findStation(i.getAssignedStation());
        if (s != null) return new double[]{s.getLat(), s.getLon()};
        return null;
    }

    /** True when this person has a report that is still open (the citizen shares a live position until it closes). */
    public static boolean hasActiveReport(User u) {
        if (u == null) return false;
        for (Incident i : getIncidentsFor(u.getUsername())) if (i.isActive()) return true;
        return false;
    }

    /**
     * Where the person who needs help is right now: their phone's live position while the report is open, else the
     * report's pin. Returns {lat, lon} or null. Responders route to this, and the km left is measured to it.
     */
    public static double[] victimPosition(Incident i) {
        if (i == null) return null;
        if (i.isActive()) {
            User c = findUser(i.getOwnerUsername());
            User me = com.rescuelink.app.utils.SessionManager.getCurrentUser();
            if (c != null && c.hasPosition() && (c.isLive() || c == me || !i.hasPosition())) {
                return new double[]{c.getLat(), c.getLon()};
            }
        }
        if (i.hasPosition()) return new double[]{i.getLat(), i.getLon()};
        return null;
    }

    /** Short words for the screens: what the distances are measured from. */
    public static String referenceLabel(Incident i) {
        if (i == null || i.hasPosition()) return "the report";
        return i.getAssignedStation() == null ? "the report" : i.getAssignedStation();
    }

    /** Straight-line km from the report (or its station) to a responder, or NaN when either place is unknown. */
    public static double distanceToReport(Incident i, User u) {
        double[] ref = referencePoint(i);
        if (ref == null || u == null || !u.hasPosition()) return Double.NaN;
        return GeoUtils.distanceKm(ref[0], ref[1], u.getLat(), u.getLon());
    }

    /** The responder in charge of an active report, or null. */
    public static User trackedResponder(Incident i) {
        if (i == null || !i.isActive() || i.getResponderName() == null) return null;
        return findUserByName(i.getResponderName());
    }

    /**
     * Every active responder of the report's service, closest first, so admins and responders can see who is nearest.
     * Responders who are not sharing a position come last.
     */
    public static List<User> rankedRespondersFor(final Incident i) {
        String svc = serviceOfCategory(i.getCategory());
        final List<User> out = new ArrayList<>();
        for (User u : USERS) {
            if (!User.ROLE_RESPONDER.equals(u.getRole()) || !u.isActive()) continue;
            if (!svc.isEmpty() && !svc.equals(serviceOf(u))) continue;
            out.add(u);
        }
        java.util.Collections.sort(out, new java.util.Comparator<User>() {
            @Override public int compare(User a, User b) {
                double da = distanceToReport(i, a), db = distanceToReport(i, b);
                if (Double.isNaN(da)) da = Double.MAX_VALUE;
                if (Double.isNaN(db)) db = Double.MAX_VALUE;
                return Double.compare(da, db);
            }
        });
        return out;
    }

    /** One line per responder, closest first: "Name \u00B7 1.2 km, about 4 min \u00B7 On duty \u00B7 Station". */
    public static String responderLine(Incident i, User u) {
        StringBuilder sb = new StringBuilder(u.getFullName());
        double km = distanceToReport(i, u);
        if (!Double.isNaN(km)) {
            sb.append(" \u00B7 ").append(GeoUtils.formatDistance(km)).append(", about ").append(GeoUtils.formatEta(km));
        } else {
            sb.append(" \u00B7 location not shared");
        }
        sb.append(" \u00B7 ").append(u.getDutyStatus());
        if (!u.getStationName().isEmpty()) sb.append(" \u00B7 ").append(u.getStationName());
        return sb.toString();
    }

    /** Responders of the report's service from closest to farthest, as text for the report screens. */
    public static String nearestSummary(Incident i) {
        if (referencePoint(i) == null) return "Closest responders: this report has no map location yet.";
        List<User> ranked = rankedRespondersFor(i);
        if (ranked.isEmpty()) return "Closest responders: no responder of this service yet.";
        StringBuilder sb = new StringBuilder("Closest responders");
        if (!i.hasPosition()) sb.append(" (to ").append(referenceLabel(i)).append(", the report has no map pin)");
        int n = 0;
        for (User u : ranked) {
            n++;
            sb.append('\n').append(n).append(". ").append(responderLine(i, u));
            if (u.getFullName().equals(i.getResponderName())) sb.append(" \u00B7 in charge");
            else if (n == 1) sb.append(" \u00B7 closest");
        }
        return sb.toString();
    }

    /** Puts the closest free responder in charge of every waiting incident that has a location. Returns how many were assigned. */
    public static synchronized int dispatchNearest() {
        int n = 0;
        for (Incident i : getOpenIncidents()) {
            if (i.getStatus() != IncidentStatus.SUBMITTED && i.getStatus() != IncidentStatus.UNDER_REVIEW) continue;
            User r = nearestResponder(i);
            if (r == null) continue;
            acceptIncident(i, r);
            n++;
        }
        if (n > 0) save();
        return n;
    }

    /** Open incidents a responder may take. */
    public static List<Incident> getOpenIncidentsFor(User responder) {
        List<Incident> out = new ArrayList<>();
        for (Incident i : getOpenIncidents()) if (visibleTo(i, responder)) out.add(i);
        return out;
    }

    public static Incident findIncident(String reference) {
        for (Incident i : INCIDENTS) {
            if (i.getReference().equals(reference)) return i;
        }
        return null;
    }

    public static void addIncident(Incident incident) { INCIDENTS.add(incident); save(); dispatchNearest(); com.rescuelink.app.utils.AlarmCenter.check(appContext); }
    public static void removeIncident(Incident incident) {
        if (incident != null && INCIDENTS.remove(incident)) dropMessages(incident.getReference());
        save();
    }

    /** Admin delete: removes any report and frees its responder. */
    public static void deleteIncident(Incident i) {
        if (i == null || !INCIDENTS.contains(i)) return;
        freeResponder(i);
        INCIDENTS.remove(i);
        dropMessages(i.getReference());
        CloudSync.deleted("incidents/" + com.rescuelink.app.net.Cloud.key(i.getReference()));
        log("Report deleted: " + i.getReference() + " (" + i.displayTitle() + ")");
        save();
    }

    public static int countClosedIncidents() {
        int n = 0;
        for (Incident i : INCIDENTS) {
            if (i.getStatus() == IncidentStatus.RESOLVED || i.getStatus() == IncidentStatus.CANCELLED) n++;
        }
        return n;
    }

    public static int countDrafts() {
        int n = 0;
        for (Incident i : INCIDENTS) if (i.getStatus() == IncidentStatus.DRAFT) n++;
        return n;
    }

    /** Deletes resolved and cancelled reports. Returns how many were removed. */
    public static int deleteClosedIncidents() {
        int n = 0;
        for (Incident i : new ArrayList<>(INCIDENTS)) {
            if (i.getStatus() == IncidentStatus.RESOLVED || i.getStatus() == IncidentStatus.CANCELLED) {
                INCIDENTS.remove(i);
                dropMessages(i.getReference());
                CloudSync.deleted("incidents/" + com.rescuelink.app.net.Cloud.key(i.getReference()));
                n++;
            }
        }
        if (n > 0) log("Cleaned up " + n + " closed reports");
        save();
        return n;
    }

    public static int deleteDrafts() {
        int n = 0;
        for (Incident i : new ArrayList<>(INCIDENTS)) {
            if (i.getStatus() == IncidentStatus.DRAFT) {
                INCIDENTS.remove(i);
                n++;
            }
        }
        if (n > 0) log("Cleaned up " + n + " draft reports");
        save();
        return n;
    }

    /** Puts a responder back to On duty when they have nothing else active. */
    public static void freeResponder(Incident gone) {
        if (gone == null || gone.getResponderName() == null) return;
        User u = findUserByName(gone.getResponderName());
        if (u == null) return;
        for (Incident other : INCIDENTS) {
            if (other != gone && other.isActive() && u.getFullName().equals(other.getResponderName())) return;
        }
        if (User.DUTY_BUSY.equals(u.getDutyStatus())) u.setDutyStatus(User.DUTY_ON);
    }

    /** A responder takes an open incident. */
    public static void acceptIncident(Incident i, User responder) {
        String own = responder.getStationName();
        Station st = own.isEmpty() ? null : findStation(own);
        if (st == null && i.hasPosition()) st = nearestStationFor(i.getCategory(), i.getLat(), i.getLon());
        assign(i, st == null ? null : st.getName(), responder.getFullName());
        responder.setDutyStatus(User.DUTY_BUSY);
    }

    /**
     * One-tap emergency: records a critical report for the signed-in citizen, routed to the nearest available
     * station of that service, so it shows up in their reports and in the Command Center and responder lists.
     */
    public static synchronized Incident createEmergency(User citizen, String category, String label,
                                                        String mode, double lat, double lon) {
        if (citizen == null) return null;
        boolean fix = !Double.isNaN(lat) && !Double.isNaN(lon);
        long now = System.currentTimeMillis();
        Incident i = new Incident(nextReference(), citizen.getUsername());
        i.setCategory(category);
        i.setTitle(label.toUpperCase(Locale.ROOT) + " emergency: " + citizen.getFullName());
        StringBuilder d = new StringBuilder("One-tap ").append(label).append(" emergency from the app (")
                .append("sms".equals(mode) ? "SMS" : "app".equals(mode) ? "internet call" : "phone call").append(").");
        if (!citizen.getBloodType().isEmpty()) d.append(" Blood type: ").append(citizen.getBloodType()).append('.');
        if (!citizen.getAllergies().isEmpty()) d.append(" Allergies: ").append(citizen.getAllergies()).append('.');
        if (!citizen.getConditions().isEmpty()) d.append(" Conditions: ").append(citizen.getConditions()).append('.');
        i.setDescription(d.toString());
        i.setLocation(fix ? "Live location (" + GeoUtils.formatCoords(lat, lon) + ")"
                : (citizen.getAddress().isEmpty() ? "Location unavailable" : citizen.getAddress()));
        i.setSeverity("Critical");
        i.setAffected(1);
        i.setSos(true);
        i.setCreatedAt(now);
        if (fix) i.setPosition(lat, lon);
        Station st = null;
        if (fix) st = nearestStationFor(category, lat, lon);
        if (st == null) {
            List<Station> fits = stationsForCategory(category, fix ? lat : 0, fix ? lon : 0);
            if (!fits.isEmpty()) st = fits.get(0);
        }
        if (st != null) i.setAssignedStation(st.getName());
        i.setStatus(IncidentStatus.SUBMITTED);
        i.addEvent(new StatusEvent(IncidentStatus.SUBMITTED, now, "Emergency received."));
        INCIDENTS.add(i);
        log("EMERGENCY " + label + " " + i.getReference());
        save();
        dispatchNearest();
        com.rescuelink.app.utils.AlarmCenter.check(appContext);
        return i;
    }

    /** Six random characters so two phones can never create the same reference. Easy to read out loud. */
    public static String nextReference() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder("RL-");
        for (int k = 0; k < 6; k++) sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        return sb.toString();
    }

    public static String nextDraftReference() {
        return String.format(Locale.US, "DRAFT-%04d", ++draftCounter);
    }

    /** Moves an incident to a new status, filling in any skipped steps of the normal flow. */
    public static void updateStatus(Incident i, IncidentStatus target, String note) {
        long now = System.currentTimeMillis();
        if (target != IncidentStatus.CANCELLED) {
            for (IncidentStatus s : IncidentStatus.FLOW) {
                if (s == target) break;
                if (i.findEvent(s) == null) i.addEvent(new StatusEvent(s, now, defaultNote(s)));
            }
        }
        i.setStatus(target);
        i.addEvent(new StatusEvent(target, now, note == null || note.trim().isEmpty()
                ? defaultNote(target) : note.trim()));
        log(i.getReference() + " is now " + target.getLabel());
        save();
    }

    /** Adds a note to the update history without changing the status. */
    public static void addNote(Incident i, String note) {
        i.addEvent(new StatusEvent(i.getStatus(), System.currentTimeMillis(), note));
        save();
    }

    public static void assign(Incident i, String stationName, String responderName) {
        i.setAssignedStation(stationName);
        i.setResponderName(responderName);
        log(i.getReference() + " assigned to " + (responderName == null ? "no responder" : responderName));
        String who = (responderName == null ? "" : responderName)
                + (stationName == null ? "" : (responderName == null ? "" : " from ") + stationName);
        if (i.getStatus() == IncidentStatus.SUBMITTED || i.getStatus() == IncidentStatus.UNDER_REVIEW) {
            updateStatus(i, IncidentStatus.ASSIGNED, "Assigned to " + who + ".");
        } else {
            addNote(i, "Reassigned to " + who + ".");
        }
    }

    public static Map<String, Integer> countByCategory() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (Incident i : getAllIncidents()) {
            Integer n = map.get(i.getCategory());
            map.put(i.getCategory(), n == null ? 1 : n + 1);
        }
        return map;
    }

    public static int countByStatus(IncidentStatus s) {
        int n = 0;
        for (Incident i : getAllIncidents()) if (i.getStatus() == s) n++;
        return n;
    }

    public static int countActive() {
        int n = 0;
        for (Incident i : getAllIncidents()) if (i.isActive()) n++;
        return n;
    }

    // ---------- alerts ----------
    public static List<AlertItem> getAlerts() { return new ArrayList<>(ALERTS); }

    public static AlertItem latestAlert() { return ALERTS.isEmpty() ? null : ALERTS.get(0); }

    public static void addAlert(String title, String message, int level, String author) {
        ALERTS.add(0, new AlertItem("a" + System.currentTimeMillis() + "-" + (++alertCounter), title, message, level,
                System.currentTimeMillis(), author));
        log("Broadcast sent: " + title);
        save();
    }

    public static void deleteAlert(AlertItem a) {
        if (a == null || !ALERTS.remove(a)) return;
        READ_ALERTS.remove(a.getId());
        CloudSync.deleted("alerts/" + com.rescuelink.app.net.Cloud.key(a.getId()));
        log("Broadcast deleted: " + a.getTitle());
        save();
    }

    public static int clearAlerts() {
        int n = ALERTS.size();
        for (AlertItem a : ALERTS) CloudSync.deleted("alerts/" + com.rescuelink.app.net.Cloud.key(a.getId()));
        ALERTS.clear();
        READ_ALERTS.clear();
        if (n > 0) log("Cleared " + n + " broadcasts");
        save();
        return n;
    }

    public static int unreadAlertCount() {
        int n = 0;
        for (AlertItem a : ALERTS) if (!READ_ALERTS.contains(a.getId())) n++;
        return n;
    }

    public static boolean isAlertRead(AlertItem a) { return READ_ALERTS.contains(a.getId()); }

    public static void markAllAlertsRead() {
        for (AlertItem a : ALERTS) READ_ALERTS.add(a.getId());
    }

    // ---------- messages ----------
    /** Who may read and write in a report's conversation: the reporter, the admin, and responders who can work it. */
    public static boolean canChat(User u, Incident i) {
        if (u == null || i == null || i.getStatus() == IncidentStatus.DRAFT) return false;
        String role = u.getRole();
        if (User.ROLE_ADMIN.equals(role)) return true;
        if (User.ROLE_CITIZEN.equals(role)) return i.getOwnerUsername().equalsIgnoreCase(u.getUsername());
        if (User.ROLE_RESPONDER.equals(role)) {
            if (u.getFullName().equals(i.getResponderName())) return true;
            return i.isActive() && i.getResponderName() == null;   // the responder in charge owns the conversation
        }
        return false;
    }

    /** Closed reports keep their conversation readable but no new messages can be added. */
    public static boolean canSend(User u, Incident i) { return canChat(u, i) && i.isActive(); }

    public static List<IncidentMessage> getMessages(String reference) {
        List<IncidentMessage> out = new ArrayList<>();
        for (IncidentMessage m : MESSAGES) if (m.getReference().equals(reference)) out.add(m);
        return out;
    }

    public static IncidentMessage lastMessage(String reference) {
        IncidentMessage last = null;
        for (IncidentMessage m : MESSAGES) if (m.getReference().equals(reference)) last = m;
        return last;
    }

    public static IncidentMessage addMessage(Incident i, User sender, String text) {
        String clean = text == null ? "" : text.trim();
        if (i == null || sender == null || clean.isEmpty() || !canSend(sender, i)) return null;
        IncidentMessage m = new IncidentMessage(i.getReference(), sender.getUsername(), sender.getFullName(),
                sender.getRole(), clean, System.currentTimeMillis());
        MESSAGES.add(m);
        markRead(sender, i.getReference());
        save();
        return m;
    }

    private static String readKey(User u, String reference) {
        return u.getUsername().toLowerCase(Locale.ROOT) + "|" + reference;
    }

    public static void markRead(User u, String reference) {
        if (u == null || reference == null) return;
        LAST_READ.put(readKey(u, reference), System.currentTimeMillis());
        save();
    }

    // ---------- direct messages (a citizen writes to any responder they choose) ----------
    private static final String DM_PREFIX = "DM_";

    /** The conversation key for one citizen and one responder. */
    public static String dmRef(String citizenUsername, String responderUsername) {
        return DM_PREFIX + com.rescuelink.app.net.Cloud.key(citizenUsername) + "_"
                + com.rescuelink.app.net.Cloud.key(responderUsername);
    }

    public static boolean isDm(String ref) { return ref != null && ref.startsWith(DM_PREFIX); }

    /** The username of the person on the other side of a direct conversation, or null if {@code me} is not part of it. */
    public static String dmOther(String ref, User me) {
        if (!isDm(ref) || me == null) return null;
        String mine = com.rescuelink.app.net.Cloud.key(me.getUsername());
        String rest = ref.substring(DM_PREFIX.length());
        int cut = rest.indexOf('_');
        if (cut < 0) return null;
        String a = rest.substring(0, cut), b = rest.substring(cut + 1);
        if (a.equals(mine)) return b;
        if (b.equals(mine)) return a;
        return null;
    }

    /** Every active responder a citizen can write to, online ones first. */
    public static List<User> messageableResponders() {
        List<User> out = new ArrayList<>();
        for (User u : USERS) if (User.ROLE_RESPONDER.equals(u.getRole()) && u.isActive()) out.add(u);
        Collections.sort(out, new java.util.Comparator<User>() {
            @Override public int compare(User a, User b) {
                if (a.isLive() != b.isLive()) return a.isLive() ? -1 : 1;
                return a.getFullName().compareToIgnoreCase(b.getFullName());
            }
        });
        return out;
    }

    /** Looks a user up by the cleaned key used inside a conversation reference. */
    public static User findUserByKey(String key) {
        for (User u : USERS) if (com.rescuelink.app.net.Cloud.key(u.getUsername()).equals(key)) return u;
        return null;
    }

    public static IncidentMessage sendDirect(User from, User to, String text) {
        String clean = text == null ? "" : text.trim();
        if (from == null || to == null || clean.isEmpty()) return null;
        String ref = User.ROLE_CITIZEN.equals(from.getRole()) ? dmRef(from.getUsername(), to.getUsername())
                : dmRef(to.getUsername(), from.getUsername());
        IncidentMessage m = new IncidentMessage(ref, from.getUsername(), from.getFullName(),
                from.getRole(), clean, System.currentTimeMillis());
        MESSAGES.add(m);
        markRead(from, ref);
        save();
        return m;
    }

    /** References of all direct conversations this person is in, most recent first. */
    public static List<String> directThreadsFor(User me) {
        List<String> out = new ArrayList<>();
        if (me == null) return out;
        for (IncidentMessage m : new ArrayList<>(MESSAGES)) {
            String ref = m.getReference();
            if (isDm(ref) && !out.contains(ref) && dmOther(ref, me) != null) out.add(ref);
        }
        Collections.sort(out, new java.util.Comparator<String>() {
            @Override public int compare(String a, String b) {
                IncidentMessage x = lastMessage(a), y = lastMessage(b);
                return Long.compare(y == null ? 0 : y.getTimeMillis(), x == null ? 0 : x.getTimeMillis());
            }
        });
        return out;
    }

    public static int unreadDirect(User u, String ref) {
        if (u == null || ref == null) return 0;
        Long seen = LAST_READ.get(readKey(u, ref));
        long since = seen == null ? 0L : seen;
        int n = 0;
        for (IncidentMessage m : MESSAGES) {
            if (m.getReference().equals(ref) && m.getTimeMillis() > since
                    && !m.getSenderUsername().equalsIgnoreCase(u.getUsername())) n++;
        }
        return n;
    }

    // ---------- report updates (the citizen's "My reports" notifications) ----------
    private static String updateKey(User u, String reference) {
        return "upd|" + u.getUsername().toLowerCase(Locale.ROOT) + "|" + reference;
    }

    /** History entries every report starts with (the submit itself), which are never "news" to the reporter. */
    private static int baselineEvents(Incident i) {
        int n = 0;
        for (StatusEvent e : i.getHistory()) {
            if (e.getStatus() == IncidentStatus.SUBMITTED || e.getStatus() == IncidentStatus.DRAFT) n++;
        }
        return n;
    }

    /** How many updates (accepted, assigned, status changes, notes) the reporter has not looked at yet. */
    public static int unreadUpdates(User u, Incident i) {
        if (u == null || i == null || i.getStatus() == IncidentStatus.DRAFT) return 0;
        if (!User.ROLE_CITIZEN.equals(u.getRole()) || !i.getOwnerUsername().equalsIgnoreCase(u.getUsername())) return 0;
        Long seen = LAST_READ.get(updateKey(u, i.getReference()));
        long base = seen == null ? baselineEvents(i) : seen;
        return (int) Math.max(0, i.getHistory().size() - base);
    }

    public static int unreadUpdatesFor(User u) {
        if (u == null || !User.ROLE_CITIZEN.equals(u.getRole())) return 0;
        int n = 0;
        for (Incident i : getIncidentsFor(u.getUsername())) n += unreadUpdates(u, i);
        return n;
    }

    /** The reporter opened the report: everything up to now counts as seen. */
    public static void markUpdatesSeen(User u, Incident i) {
        if (u == null || i == null || !User.ROLE_CITIZEN.equals(u.getRole())) return;
        if (!i.getOwnerUsername().equalsIgnoreCase(u.getUsername())) return;
        String key = updateKey(u, i.getReference());
        long now = i.getHistory().size();
        Long old = LAST_READ.get(key);
        if (old != null && old == now) return;
        LAST_READ.put(key, now);
        save();
    }

    /** Messages in a report written by other people since this user last opened the conversation. */
    public static int unreadMessages(User u, Incident i) {
        if (u == null || i == null) return 0;
        Long seen = LAST_READ.get(readKey(u, i.getReference()));
        long since = seen == null ? 0L : seen;
        int n = 0;
        for (IncidentMessage m : MESSAGES) {
            if (m.getReference().equals(i.getReference()) && m.getTimeMillis() > since
                    && !m.getSenderUsername().equalsIgnoreCase(u.getUsername())) n++;
        }
        return n;
    }

    public static int unreadMessagesFor(User u) {
        int n = 0;
        for (Incident i : chatThreadsFor(u)) n += unreadMessages(u, i);
        for (String ref : directThreadsFor(u)) n += unreadDirect(u, ref);
        return n;
    }

    /**
     * Conversations shown in a user's inbox, most recent first. Citizens see all their submitted reports,
     * responders see reports assigned to them, and everyone also sees any report that already has messages.
     */
    public static List<Incident> chatThreadsFor(User u) {
        List<Incident> out = new ArrayList<>();
        if (u == null) return out;
        for (Incident i : getAllIncidents()) {
            if (!canChat(u, i)) continue;
            boolean hasMessages = lastMessage(i.getReference()) != null;
            boolean mine = User.ROLE_CITIZEN.equals(u.getRole())
                    || u.getFullName().equals(i.getResponderName());
            if (hasMessages || mine) out.add(i);
        }
        Collections.sort(out, new java.util.Comparator<Incident>() {
            @Override
            public int compare(Incident a, Incident b) {
                return Long.compare(activityTime(b), activityTime(a));
            }
        });
        return out;
    }

    public static long activityTime(Incident i) {
        IncidentMessage m = lastMessage(i.getReference());
        return m == null ? i.getCreatedAt() : m.getTimeMillis();
    }

    private static void dropMessages(String reference) {
        for (IncidentMessage m : new ArrayList<>(MESSAGES)) {
            if (m.getReference().equals(reference)) MESSAGES.remove(m);
        }
        String suffix = "|" + reference;
        for (String k : new ArrayList<>(LAST_READ.keySet())) if (k.endsWith(suffix)) LAST_READ.remove(k);
    }

    // ---------- online sync (called by CloudSync on the main thread) ----------
    /** Latest moment anything on a report happened. The newer copy wins when two phones disagree. */
    static long stamp(Incident i) {
        long t = i.getCreatedAt();
        for (StatusEvent e : i.getHistory()) if (e.getTimeMillis() > t) t = e.getTimeMillis();
        return t;
    }

    /** Records what another phone says about a person. Returns true when something worth redrawing changed. */
    static synchronized boolean mergePresence(String username, String name, String role, String service,
                                              String station, String duty, double lat, double lon, boolean online) {
        if (username == null || username.isEmpty()) return false;
        User me = com.rescuelink.app.utils.SessionManager.getCurrentUser();
        User u = findUser(username);
        if (u != null && u == me) return false;                       // my own phone knows best
        boolean changed = false;
        if (u == null) {
            if (!online) return false;
            if (!User.ROLE_RESPONDER.equals(role) && !User.ROLE_CITIZEN.equals(role) && !User.ROLE_ADMIN.equals(role)) return false;
            u = User.restore(name.isEmpty() ? username : name, 18, "", username, "", "", "", role);
            u.setRemote(true);
            u.setService(service);
            u.setStationName(station);
            USERS.add(u);
            changed = true;
        }
        boolean wasLive = u.isLive();
        u.setLastSeen(online ? System.currentTimeMillis() : 0L);
        if (wasLive != u.isLive()) changed = true;
        if (online) {
            if (!duty.isEmpty() && !duty.equals(u.getDutyStatus())) { u.setDutyStatus(duty); changed = true; }
            if (!Double.isNaN(lat) && !Double.isNaN(lon)
                    && (!u.hasPosition() || GeoUtils.distanceKm(u.getLat(), u.getLon(), lat, lon) > 0.003)) {
                u.setPosition(lat, lon);
                changed = true;
            }
        }
        return changed;
    }

    /** Adds a report from another phone, or replaces ours when theirs is newer. Returns true when something changed. */
    /** Adds or updates a station that came from another phone. Returns true when something changed. */
    static synchronized boolean mergeStation(Station r) {
        if (r == null) return false;
        Station l = findStationIgnoreCase(r.getName());
        if (l == null) { STATIONS.add(r); return true; }
        boolean changed = false;
        if (!l.getCategory().equals(r.getCategory()) || !l.getAddress().equals(r.getAddress())
                || !l.getPhone().equals(r.getPhone())) {
            l.setDetails(l.getName(), r.getCategory(), r.getAddress(), r.getPhone());
            changed = true;
        }
        if (Math.abs(l.getLat() - r.getLat()) > 1e-6 || Math.abs(l.getLon() - r.getLon()) > 1e-6) {
            l.setLocation(r.getLat(), r.getLon());
            changed = true;
        }
        if (l.isAvailable() != r.isAvailable()) { l.setAvailable(r.isAvailable()); changed = true; }
        return changed;
    }

    /**
     * A responder's last known place from the shared roster. Responders who are online right now are handled by
     * the live list instead; this keeps the offline ones visible, shown where they were last seen.
     */
    static synchronized boolean mergeRoster(String username, String name, String service, String station,
                                            String duty, double lat, double lon) {
        if (username == null || username.isEmpty()) return false;
        User me = com.rescuelink.app.utils.SessionManager.getCurrentUser();
        User u = findUser(username);
        if (u != null && (u == me || u.isLive())) return false;
        boolean changed = false;
        if (u == null) {
            u = User.restore(name.isEmpty() ? username : name, 18, "", username, "", "", "", User.ROLE_RESPONDER);
            u.setRemote(true);
            u.setService(service);
            u.setStationName(station);
            USERS.add(u);
            changed = true;
        }
        if (!duty.isEmpty() && !duty.equals(u.getDutyStatus())) { u.setDutyStatus(duty); changed = true; }
        if (!Double.isNaN(lat) && !Double.isNaN(lon)
                && (!u.hasPosition() || GeoUtils.distanceKm(u.getLat(), u.getLon(), lat, lon) > 0.003)) {
            u.setPosition(lat, lon);
            changed = true;
        }
        return changed;
    }

    static synchronized boolean markMissingOffline(Set<String> seenLower) {
        User me = com.rescuelink.app.utils.SessionManager.getCurrentUser();
        boolean changed = false;
        for (User u : USERS) {
            if (u == me || !u.isLive()) continue;
            if (!seenLower.contains(u.getUsername().toLowerCase(Locale.ROOT))) { u.setLastSeen(0L); changed = true; }
        }
        return changed;
    }

    static synchronized boolean mergeIncident(Incident r) {
        if (r == null || r.getStatus() == IncidentStatus.DRAFT) return false;
        for (int k = 0; k < INCIDENTS.size(); k++) {
            Incident l = INCIDENTS.get(k);
            if (!l.getReference().equals(r.getReference())) continue;
            if (stamp(r) <= stamp(l)) return false;
            r.setPhotoUri(l.getPhotoUri());                           // photos stay on the phone that took them
            r.setFeedbackGiven(r.isFeedbackGiven() || l.isFeedbackGiven());
            INCIDENTS.set(k, r);
            takeIfMine(r);
            return true;
        }
        INCIDENTS.add(r);
        takeIfMine(r);
        return true;
    }

    /** A report that another phone assigned to the person signed in here makes them Busy, so nobody else is sent to them. */
    private static void takeIfMine(Incident r) {
        User me = com.rescuelink.app.utils.SessionManager.getCurrentUser();
        if (me == null || !User.ROLE_RESPONDER.equals(me.getRole()) || !r.isActive()) return;
        if (me.getFullName().equals(r.getResponderName()) && !User.DUTY_BUSY.equals(me.getDutyStatus())) {
            me.setDutyStatus(User.DUTY_BUSY);
        }
    }

    static synchronized boolean mergeMessage(IncidentMessage m) {
        if (m == null) return false;
        for (IncidentMessage x : MESSAGES) {
            if (x.getTimeMillis() == m.getTimeMillis() && x.getReference().equals(m.getReference())
                    && x.getSenderUsername().equalsIgnoreCase(m.getSenderUsername()) && x.getText().equals(m.getText())) return false;
        }
        MESSAGES.add(m);
        Collections.sort(MESSAGES, new java.util.Comparator<IncidentMessage>() {
            @Override public int compare(IncidentMessage a, IncidentMessage b) { return Long.compare(a.getTimeMillis(), b.getTimeMillis()); }
        });
        return true;
    }

    static synchronized boolean mergeAlert(AlertItem a) {
        if (a == null) return false;
        for (AlertItem x : ALERTS) if (x.getId().equals(a.getId())) return false;
        ALERTS.add(a);
        Collections.sort(ALERTS, new java.util.Comparator<AlertItem>() {
            @Override public int compare(AlertItem x, AlertItem y) { return Long.compare(y.getTimeMillis(), x.getTimeMillis()); }
        });
        return true;
    }

    /** Snapshots used by CloudSync to see what still has to be sent. */
    static synchronized List<Incident> incidentsSnapshot() { return new ArrayList<>(INCIDENTS); }
    static synchronized List<IncidentMessage> messagesSnapshot() { return new ArrayList<>(MESSAGES); }
    static synchronized List<AlertItem> alertsSnapshot() { return new ArrayList<>(ALERTS); }
    static synchronized Context appContext() { return appContext; }

    // ---------- activity log ----------
    private static String actor() {
        User u = com.rescuelink.app.utils.SessionManager.getCurrentUser();
        return u == null ? "System" : u.getFullName();
    }

    public static void log(String action) {
        LOG.add(0, new LogEntry(System.currentTimeMillis(), actor(), action));
        while (LOG.size() > 300) LOG.remove(LOG.size() - 1);
    }

    public static List<LogEntry> getLog() { return new ArrayList<>(LOG); }

    public static void clearLog() { LOG.clear(); }
}
