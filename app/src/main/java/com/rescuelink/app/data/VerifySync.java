package com.rescuelink.app.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import com.rescuelink.app.models.User;
import com.rescuelink.app.net.Cloud;
import com.rescuelink.app.net.Config;
import com.rescuelink.app.utils.SessionManager;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * Sign-up ID photo and face scan. They are taken on the citizen's own phone, so a copy is kept online under
 * verify/<number> for the main admin (Command Center) to look at from any phone. Only the viewer in
 * UserAdminActions asks for them, and only for the Command Center admin; nothing is downloaded in bulk.
 */
public final class VerifySync {
    private static final int SIDE = 900;
    private static final String PREFS = "rescuelink_verify";

    private VerifySync() { }

    public interface Callback { void done(Bitmap idPhoto, Bitmap facePhoto, boolean found); }

    private static String key(String username) {
        return Cloud.key(username == null ? "" : username.toLowerCase(java.util.Locale.ROOT));
    }

    private static File dir(Context c) { return new File(c.getFilesDir(), "verify"); }

    private static Bitmap decodeScaled(File f) {
        if (f == null || !f.exists()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int s = 1;
        while (o.outWidth / (s * 2) >= SIDE && o.outHeight / (s * 2) >= SIDE) s *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = s;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
    }

    private static String encode(Bitmap b) {
        if (b == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 70, bo);
        return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
    }

    private static Bitmap decode(String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        try {
            byte[] raw = Base64.decode(b64, Base64.NO_WRAP);
            return BitmapFactory.decodeByteArray(raw, 0, raw.length);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Sends the signed-in citizen's own ID photo and face scan online once. Safe to call often; does nothing offline. */
    public static void uploadMine(final Context c) {
        final User me = SessionManager.getCurrentUser();
        if (me == null || !User.ROLE_CITIZEN.equals(me.getRole())) return;
        if (me.getIdPhotoFile().isEmpty() && me.getFacePhotoFile().isEmpty()) return;
        if (!Config.cloudEnabled(c)) return;
        final Context app = c.getApplicationContext();
        final String k = key(me.getUsername());
        final String tag = me.getIdPhotoFile() + "|" + me.getFacePhotoFile();
        if (tag.equals(app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(k, ""))) return;
        final File idF = new File(dir(app), me.getIdPhotoFile());
        final File faceF = new File(dir(app), me.getFacePhotoFile());
        Cloud.run(new Runnable() {
            @Override public void run() {
                try {
                    Bitmap id = decodeScaled(idF), face = decodeScaled(faceF);
                    if (id == null && face == null) return;
                    JSONObject o = new JSONObject();
                    o.put("id", encode(id));
                    o.put("face", encode(face));
                    o.put("at", me.getVerifiedAt());
                    o.put("n", me.getFullName());
                    if (Cloud.put(Config.cloudUrl(app), "verify/" + k, o.toString())) {
                        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(k, tag).apply();
                    }
                } catch (Exception ignored) { }
            }
        });
    }

    /** Loads a citizen's ID photo and face scan (from this phone when they signed up here, else online). */
    public static void fetch(final Context c, final User u, final Callback cb) {
        final Context app = c.getApplicationContext();
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        final File idF = new File(dir(app), u.getIdPhotoFile());
        final File faceF = new File(dir(app), u.getFacePhotoFile());
        Cloud.run(new Runnable() {
            @Override public void run() {
                Bitmap id = u.getIdPhotoFile().isEmpty() ? null : decodeScaled(idF);
                Bitmap face = u.getFacePhotoFile().isEmpty() ? null : decodeScaled(faceF);
                if (id == null && face == null && Config.cloudEnabled(app)) {
                    try {
                        String got = Cloud.get(Config.cloudUrl(app), "verify/" + key(u.getUsername()));
                        if (got != null && !"null".equals(got)) {
                            JSONObject o = new JSONObject(got);
                            id = decode(o.optString("id", ""));
                            face = decode(o.optString("face", ""));
                        }
                    } catch (Exception ignored) { }
                }
                final Bitmap fi = id, ff = face;
                main.post(new Runnable() {
                    @Override public void run() { cb.done(fi, ff, fi != null || ff != null); }
                });
            }
        });
    }
}
