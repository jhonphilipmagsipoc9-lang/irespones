package com.rescuelink.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;

import com.rescuelink.app.models.Station;
import com.rescuelink.app.net.Cloud;
import com.rescuelink.app.net.Config;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pictures of stations and evacuation sites, shared the same way as profile pictures (see {@link PhotoSync}):
 * a small copy is kept online under stphotos/<key>, and stphotoidx/<key> = version says which one is current.
 * Every phone downloads only what changed, so citizens, responders and admins all see the same picture.
 */
public final class StationPhotos {
    private static final int SHARE_SIDE = 360;      // longest side of the copy kept online
    private static final int LOCAL_SIDE = 720;      // longest side of the copy kept on the phone
    private static final String PREFS = "rescuelink_station_photos";

    private static final ConcurrentHashMap<String, String> HAVE = new ConcurrentHashMap<>();
    private static final Set<String> BUSY = ConcurrentHashMap.newKeySet();
    private static boolean loaded;
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    private StationPhotos() { }

    private static String key(Station s) { return Cloud.key(s.getName().toLowerCase(Locale.ROOT)); }

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

    private static Bitmap fit(Bitmap src, int side) {
        int longest = Math.max(src.getWidth(), src.getHeight());
        if (longest <= side) return src;
        float f = side / (float) longest;
        return Bitmap.createScaledBitmap(src, Math.max(1, Math.round(src.getWidth() * f)), Math.max(1, Math.round(src.getHeight() * f)), true);
    }

    private static File dir(Context c) {
        File d = new File(c.getApplicationContext().getFilesDir(), "stations");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private static void deleteFile(String uri) {
        if (uri == null || uri.isEmpty()) return;
        try {
            String path = Uri.parse(uri).getPath();
            if (path != null && path.contains("/stations/")) {
                //noinspection ResultOfMethodCallIgnored
                new File(path).delete();
            }
        } catch (Exception ignored) { }
    }

    /** Saves a picture for this station on the phone and shares it online. */
    public static boolean save(Context c, Station s, Bitmap src) {
        if (s == null || src == null) return false;
        try {
            Bitmap b = fit(src, LOCAL_SIDE);
            File out = new File(dir(c), "s" + Math.abs(key(s).hashCode()) + "_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream fo = new FileOutputStream(out)) { b.compress(Bitmap.CompressFormat.JPEG, 85, fo); }
            deleteFile(s.getPhotoUri());
            s.setPhotoUri(Uri.fromFile(out).toString());
            DataRepository.save();
            upload(c, s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Removes the picture from the phone and online. */
    public static void clear(Context c, Station s) {
        if (s == null) return;
        deleteFile(s.getPhotoUri());
        s.setPhotoUri(null);
        DataRepository.save();
        removed(c, s);
    }

    /** Sends the current picture online (in the background). Does nothing without internet or an online address. */
    public static void upload(final Context c, final Station s) {
        if (s == null || s.getPhotoUri().isEmpty() || !Config.cloudEnabled(c)) return;
        final Context app = c.getApplicationContext();
        final String uri = s.getPhotoUri();
        final String k = key(s);
        Cloud.run(new Runnable() {
            @Override public void run() {
                try {
                    String path = Uri.parse(uri).getPath();
                    Bitmap src = path == null ? null : BitmapFactory.decodeFile(path);
                    if (src == null) return;
                    Bitmap sm = fit(src, SHARE_SIDE);
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    sm.compress(Bitmap.CompressFormat.JPEG, 72, bo);
                    String b64 = Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
                    String ver = Integer.toHexString(b64.hashCode()) + Integer.toHexString(b64.length());
                    String base = Config.cloudUrl(app);
                    if (Cloud.put(base, "stphotos/" + k, new JSONObject().put("d", b64).toString())
                            && Cloud.put(base, "stphotoidx/" + k, JSONObject.quote(ver))) {
                        load(app);
                        HAVE.put(k, ver);
                        persist(app);
                    }
                } catch (Exception ignored) { }
            }
        });
    }

    /** The picture was removed or the station deleted: remove it online too. */
    public static void removed(final Context c, Station s) {
        if (c == null || s == null || !Config.cloudEnabled(c)) return;
        final Context app = c.getApplicationContext();
        final String k = key(s);
        load(app);
        HAVE.remove(k);
        persist(app);
        Cloud.run(new Runnable() {
            @Override public void run() {
                String base = Config.cloudUrl(app);
                Cloud.delete(base, "stphotoidx/" + k);
                Cloud.delete(base, "stphotos/" + k);
            }
        });
    }

    /** Called by the sync on the main thread with the online list of station picture versions. */
    static boolean applyIndex(final Context app, String body) {
        if (body == null) return false;
        load(app);
        JSONObject idx = null;
        if (!body.isEmpty() && !"null".equals(body)) {
            try { idx = new JSONObject(body); } catch (Exception ignored) { }
        }
        boolean changed = false;
        final String base = Config.cloudUrl(app);
        for (final Station s : new ArrayList<>(DataRepository.getStations())) {
            final String k = key(s);
            final String ver = idx == null ? "" : idx.optString(k, "");
            boolean hasLocal = !s.getPhotoUri().isEmpty();
            if (ver.isEmpty()) {
                if (HAVE.containsKey(k)) {                 // removed on another phone
                    HAVE.remove(k);
                    persist(app);
                    if (hasLocal) { deleteFile(s.getPhotoUri()); s.setPhotoUri(null); changed = true; }
                } else if (hasLocal) {
                    upload(app, s);                         // taken before pictures were shared: share it now
                }
                continue;
            }
            if (ver.equals(HAVE.get(k)) && hasLocal) continue;
            if (!BUSY.add(k)) continue;
            Cloud.run(new Runnable() {
                @Override public void run() {
                    try {
                        String got = Cloud.get(base, "stphotos/" + k);
                        if (got == null || "null".equals(got)) return;
                        byte[] raw = Base64.decode(new JSONObject(got).optString("d", ""), Base64.DEFAULT);
                        final File out = new File(dir(app), "r" + k + "_" + ver + ".jpg");
                        try (FileOutputStream fo = new FileOutputStream(out)) { fo.write(raw); }
                        MAIN.post(new Runnable() {
                            @Override public void run() {
                                String old = s.getPhotoUri();
                                s.setPhotoUri(Uri.fromFile(out).toString());
                                HAVE.put(k, ver);
                                persist(app);
                                if (!old.isEmpty() && !old.equals(s.getPhotoUri())) deleteFile(old);
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
