package com.rescuelink.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;

import com.rescuelink.app.models.User;
import com.rescuelink.app.net.Cloud;
import com.rescuelink.app.net.Config;
import com.rescuelink.app.utils.SessionManager;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Profile pictures for everyone. A small copy (192 px) of each picture is kept online under photos/<number>, and a tiny
 * list photoidx/<number> = version says which picture is current. Every phone reads the list on each sync and
 * downloads only the pictures that changed, so admins, responders and citizens all see each other's photos.
 * Photos are optional: nobody without one is affected.
 */
public final class PhotoSync {
    private static final int SIDE = 192;
    private static final String PREFS = "rescuelink_photos";

    private static final ConcurrentHashMap<String, String> HAVE = new ConcurrentHashMap<>();   // number -> version on this phone
    private static final Set<String> BUSY = ConcurrentHashMap.newKeySet();
    private static boolean loaded;
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    private PhotoSync() { }

    private static synchronized void load(Context c) {
        if (loaded) return;
        loaded = true;
        try {
            JSONObject o = new JSONObject(c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("have", "{}"));
            Iterator<String> it = o.keys();
            while (it.hasNext()) { String k = it.next(); HAVE.put(k, o.optString(k, "")); }
        } catch (Exception ignored) { }
    }

    private static void persist(Context c) {
        JSONObject o = new JSONObject();
        try { for (java.util.Map.Entry<String, String> e : HAVE.entrySet()) o.put(e.getKey(), e.getValue()); } catch (Exception ignored) { }
        c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("have", o.toString()).apply();
    }

    private static String key(String username) { return Cloud.key(username == null ? "" : username.toLowerCase(Locale.ROOT)); }

    /** Sends this person's current picture online (in the background). Safe to call when offline: it just does nothing. */
    public static void upload(final Context c, final User u) {
        if (u == null || u.getPhotoUri() == null || u.getPhotoUri().isEmpty() || !Config.cloudEnabled(c)) return;
        final Context app = c.getApplicationContext();
        final String uri = u.getPhotoUri();
        final String k = key(u.getUsername());
        Cloud.run(new Runnable() {
            @Override public void run() {
                try {
                    String path = Uri.parse(uri).getPath();
                    Bitmap src = path == null ? null : BitmapFactory.decodeFile(path);
                    if (src == null) return;
                    Bitmap sm = Bitmap.createScaledBitmap(src, SIDE, SIDE, true);
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    sm.compress(Bitmap.CompressFormat.JPEG, 72, bo);
                    String b64 = Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
                    String ver = Integer.toHexString(b64.hashCode()) + Integer.toHexString(b64.length());
                    String base = Config.cloudUrl(app);
                    if (Cloud.put(base, "photos/" + k, new JSONObject().put("d", b64).toString())
                            && Cloud.put(base, "photoidx/" + k, JSONObject.quote(ver))) {
                        HAVE.put(k, ver);
                        persist(app);
                    }
                } catch (Exception ignored) { }
            }
        });
    }

    /** The person removed their picture: remove it online too. */
    public static void removed(final Context c, User u) {
        if (u == null || !Config.cloudEnabled(c)) return;
        final Context app = c.getApplicationContext();
        final String k = key(u.getUsername());
        HAVE.remove(k);
        persist(app);
        Cloud.run(new Runnable() {
            @Override public void run() {
                String base = Config.cloudUrl(app);
                Cloud.delete(base, "photoidx/" + k);
                Cloud.delete(base, "photos/" + k);
            }
        });
    }

    /**
     * Called by the sync on the main thread with the online list of picture versions. Starts downloads for pictures
     * this phone does not have yet, and drops pictures that were removed.
     */
    static boolean applyIndex(final Context app, String body) {
        load(app);
        JSONObject idx = null;
        if (body != null && !body.isEmpty() && !"null".equals(body)) {
            try { idx = new JSONObject(body); } catch (Exception ignored) { }
        }
        if (body == null) return false;
        boolean changed = false;
        final String base = Config.cloudUrl(app);
        User me = SessionManager.getCurrentUser();
        for (final User u : DataRepository.getUsers()) {
            final String k = key(u.getUsername());
            String ver = idx == null ? "" : idx.optString(k, "");
            boolean mine = u == me;
            if (ver.isEmpty()) {
                // Nobody has shared a picture for this person. Remote people lose a picture they had removed.
                if (!mine && u.isRemote() && u.getPhotoUri() != null && HAVE.containsKey(k)) {
                    HAVE.remove(k); u.setPhotoUri(null); changed = true;
                } else if (mine && u.getPhotoUri() != null && !u.getPhotoUri().isEmpty() && !HAVE.containsKey(k)) {
                    upload(app, u);      // a picture taken before photos were shared (or on another phone): share it now
                }
                continue;
            }
            if (ver.equals(HAVE.get(k)) && u.getPhotoUri() != null && !u.getPhotoUri().isEmpty()) continue;
            if (mine && ver.equals(HAVE.get(k))) continue;
            if (!BUSY.add(k)) continue;
            final String fver = ver;
            Cloud.run(new Runnable() {
                @Override public void run() {
                    try {
                        String got = Cloud.get(base, "photos/" + k);
                        if (got == null || "null".equals(got)) return;
                        String b64 = new JSONObject(got).optString("d", "");
                        byte[] raw = Base64.decode(b64, Base64.DEFAULT);
                        File dir = new File(app.getFilesDir(), "avatars");
                        //noinspection ResultOfMethodCallIgnored
                        dir.mkdirs();
                        final File out = new File(dir, "r" + k + "_" + fver + ".jpg");
                        try (FileOutputStream fo = new FileOutputStream(out)) { fo.write(raw); }
                        MAIN.post(new Runnable() {
                            @Override public void run() {
                                String old = u.getPhotoUri();
                                u.setPhotoUri(Uri.fromFile(out).toString());
                                HAVE.put(k, fver);
                                persist(app);
                                if (old != null && old.contains("/avatars/r" + k + "_") && !old.equals(u.getPhotoUri())) {
                                    try { //noinspection ResultOfMethodCallIgnored
                                        new File(Uri.parse(old).getPath()).delete(); } catch (Exception ignored) { }
                                }
                                DataRepository.saveQuiet();
                                CloudSync.refreshUi();
                            }
                        });
                    } catch (Exception ignored) {
                    } finally {
                        BUSY.remove(k);
                    }
                }
            });
        }
        return changed;
    }
}
