package com.rescuelink.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.FaceDetector;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.rescuelink.app.models.User;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Photo checks for registration. Everything runs on the phone with the face detector that ships with
 * Android, so it needs no internet and no extra library. The photos stay in the app's private storage.
 */
public final class FaceVerifier {
    private FaceVerifier() { }

    /** Must match android:authorities of the FileProvider in the manifest (applicationId + ".fileprovider"). */
    public static String authority(Context ctx) { return ctx.getPackageName() + ".fileprovider"; }

    public static final class Result {
        public final boolean ok;
        public final String message;
        Result(boolean ok, String message) { this.ok = ok; this.message = message; }
    }

    /** Temporary photos taken during registration (shared with the camera app through the FileProvider). */
    public static File tempFile(Context ctx, String name) {
        File dir = new File(ctx.getCacheDir(), "verify");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, name);
    }

    public static Uri uriFor(Context ctx, File f) {
        return FileProvider.getUriForFile(ctx, authority(ctx), f);
    }

    /** Copies a picked gallery image into a temporary file. */
    public static boolean copy(Context ctx, Uri src, File dst) {
        try (InputStream in = ctx.getContentResolver().openInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            if (in == null) return false;
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Decodes a photo no larger than maxSide pixels and turns it upright using its EXIF tag. */
    public static Bitmap load(File f, int maxSide) {
        if (f == null || !f.exists() || f.length() == 0) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int big = Math.max(o.outWidth, o.outHeight);
        if (big <= 0) return null;
        int sample = 1;
        while (big / (sample * 2) >= maxSide) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), d);
        if (b == null) return null;
        int deg = 0;
        try {
            int ori = new ExifInterface(f.getAbsolutePath())
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (ori == ExifInterface.ORIENTATION_ROTATE_90) deg = 90;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_180) deg = 180;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_270) deg = 270;
        } catch (IOException ignored) { }
        if (deg != 0) {
            Matrix m = new Matrix();
            m.postRotate(deg);
            Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            if (r != b) b.recycle();
            b = r;
        }
        return b;
    }

    /**
     * Looks for a face. A selfie must show exactly one face, close enough to the camera. A photo of an ID
     * only needs a face somewhere on the card. This detects a face; it does not identify who it is.
     * Call off the main thread.
     */
    public static Result check(File photo, boolean selfie) {
        Bitmap src = load(photo, 640);
        if (src == null) return new Result(false, "The photo could not be read. Please take it again.");
        int w = src.getWidth() & ~1;          // the detector needs an even width
        int h = src.getHeight();
        Bitmap cropped = Bitmap.createBitmap(src, 0, 0, w, h);       // same object as src when the width was already even
        Bitmap b = cropped.copy(Bitmap.Config.RGB_565, true);
        if (b == null) {
            if (cropped != src) cropped.recycle();
            src.recycle();
            return new Result(false, "Not enough memory to check the photo. Close other apps and try again.");
        }
        if (cropped != src) cropped.recycle();
        FaceDetector det = new FaceDetector(w, h, 3);
        FaceDetector.Face[] faces = new FaceDetector.Face[3];
        int n = det.findFaces(b, faces);
        float eyes = 0f;
        float conf = 0f;
        for (int i = 0; i < n; i++) {
            if (faces[i] != null && faces[i].eyesDistance() > eyes) {
                eyes = faces[i].eyesDistance();
                conf = faces[i].confidence();
            }
        }
        src.recycle();
        b.recycle();
        if (n == 0) {
            return new Result(false, selfie
                    ? "No face found. Hold the phone at eye level in good light and look straight at the camera."
                    : "No face found on the ID. Make sure the photo on the ID is visible and sharp.");
        }
        if (selfie && n > 1) return new Result(false, "More than one face was found. Only you should be in the photo.");
        if (selfie && eyes < w * 0.10f) return new Result(false, "Your face is too far away. Move closer and try again.");
        if (conf < 0.3f) return new Result(false, "The face is not clear enough. Try again in better light.");
        return new Result(true, selfie ? "Face scan passed." : "ID photo accepted.");
    }

    /** Saves a checked photo in the app's private storage and returns its file name. */
    public static String keep(Context ctx, File tmp, String username, String kind) {
        File dir = new File(ctx.getFilesDir(), "verify");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        String name = username.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "_") + "_" + kind + ".jpg";
        Bitmap b = load(tmp, 1024);
        if (b == null) return "";
        try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
            b.compress(Bitmap.CompressFormat.JPEG, 80, out);
        } catch (IOException e) {
            return "";
        } finally {
            b.recycle();
        }
        return name;
    }

    public static void deleteFiles(Context ctx, User u) {
        File dir = new File(ctx.getFilesDir(), "verify");
        if (!u.getIdPhotoFile().isEmpty()) //noinspection ResultOfMethodCallIgnored
            new File(dir, u.getIdPhotoFile()).delete();
        if (!u.getFacePhotoFile().isEmpty()) //noinspection ResultOfMethodCallIgnored
            new File(dir, u.getFacePhotoFile()).delete();
    }

    public static void clearTemp(Context ctx) {
        File dir = new File(ctx.getCacheDir(), "verify");
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }
}
