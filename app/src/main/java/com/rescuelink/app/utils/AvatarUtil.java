package com.rescuelink.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.User;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

/** Profile pictures: shows a photo or the first letter, and saves a chosen picture inside the app. */
public final class AvatarUtil {
    private static final int SIZE = 480;

    private AvatarUtil() { }

    public static String initial(String name) {
        String n = name == null ? "" : name.trim();
        return n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    /** Photo when there is one (and it can be read), otherwise the first letter of the name. */
    public static void bind(TextView letter, ImageView photo, String name, String photoUri) {
        letter.setText(initial(name));
        boolean shown = false;
        if (photoUri != null && !photoUri.isEmpty()) {
            try {
                photo.setImageURI(null);                  // forces a reload when the file name is reused
                photo.setImageURI(Uri.parse(photoUri));
                shown = photo.getDrawable() != null;
            } catch (Exception ignored) { }
        }
        photo.setVisibility(shown ? View.VISIBLE : View.GONE);
        letter.setVisibility(shown ? View.GONE : View.VISIBLE);
    }

    public static void bind(TextView letter, ImageView photo, User u) {
        bind(letter, photo, u == null ? "" : u.getFullName(), u == null ? null : u.getPhotoUri());
    }

    /** Reads a picture from the gallery, shrinks it to a square avatar and applies the camera's rotation. */
    public static Bitmap load(Context c, Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = Math.max(1, Math.min(bounds.outWidth, bounds.outHeight) / SIZE);
            Bitmap raw;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                raw = BitmapFactory.decodeStream(in, null, o);
            }
            if (raw == null) return null;
            int turn = 0;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                if (in != null) {
                    int orient = new android.media.ExifInterface(in).getAttributeInt(
                            android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL);
                    if (orient == android.media.ExifInterface.ORIENTATION_ROTATE_90) turn = 90;
                    else if (orient == android.media.ExifInterface.ORIENTATION_ROTATE_180) turn = 180;
                    else if (orient == android.media.ExifInterface.ORIENTATION_ROTATE_270) turn = 270;
                }
            } catch (Exception ignored) { }
            return turn == 0 ? raw : rotate(raw, turn);
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap rotate(Bitmap b, int degrees) {
        Matrix m = new Matrix();
        m.postRotate(degrees);
        return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
    }

    /** Centre-crops to a square, saves it in app-private storage and makes it the user's profile picture. */
    public static boolean save(Context c, User u, Bitmap src) {
        if (u == null || src == null) return false;
        try {
            int side = Math.min(src.getWidth(), src.getHeight());
            Bitmap sq = Bitmap.createBitmap(src, (src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
            if (side > SIZE) sq = Bitmap.createScaledBitmap(sq, SIZE, SIZE, true);
            File dir = new File(c.getFilesDir(), "avatars");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File out = new File(dir, "u" + Math.abs(u.getUsername().toLowerCase(Locale.ROOT).hashCode())
                    + "_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream fo = new FileOutputStream(out)) {
                sq.compress(Bitmap.CompressFormat.JPEG, 88, fo);
            }
            deleteFile(u.getPhotoUri());
            u.setPhotoUri(Uri.fromFile(out).toString());
            DataRepository.save();
            com.rescuelink.app.data.PhotoSync.upload(c, u);       // so everyone else sees it too
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void remove(User u) {
        if (u == null) return;
        deleteFile(u.getPhotoUri());
        u.setPhotoUri(null);
        DataRepository.save();
        com.rescuelink.app.data.PhotoSync.removed(com.rescuelink.app.RescueLinkApp.appContext(), u);
    }

    private static void deleteFile(String uri) {
        if (uri == null || uri.isEmpty()) return;
        try {
            String path = Uri.parse(uri).getPath();
            if (path != null && path.contains("/avatars/")) {
                //noinspection ResultOfMethodCallIgnored
                new File(path).delete();
            }
        } catch (Exception ignored) { }
    }
}
