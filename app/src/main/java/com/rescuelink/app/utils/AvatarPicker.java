package com.rescuelink.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rescuelink.app.models.User;

import java.util.ArrayList;
import java.util.List;

/**
 * "Change photo" menu: take a photo, choose one from the gallery, or remove it. Create it while the screen is
 * being created (it registers the picker screens), then call {@link #show(Context)} when the person taps the avatar.
 */
public final class AvatarPicker {
    private final ActivityResultLauncher<String> gallery;
    private final ActivityResultLauncher<Void> camera;
    private final Runnable onChanged;
    private Context ctx;

    public AvatarPicker(ActivityResultCaller host, Runnable onChanged) {
        this.onChanged = onChanged;
        gallery = host.registerForActivityResult(new ActivityResultContracts.GetContent(),
                new androidx.activity.result.ActivityResultCallback<Uri>() {
                    @Override public void onActivityResult(Uri uri) {
                        if (uri == null || ctx == null) return;
                        apply(AvatarUtil.load(ctx, uri));
                    }
                });
        camera = host.registerForActivityResult(new ActivityResultContracts.TakePicturePreview(),
                new androidx.activity.result.ActivityResultCallback<Bitmap>() {
                    @Override public void onActivityResult(Bitmap bmp) {
                        if (bmp == null || ctx == null) return;
                        apply(bmp);
                    }
                });
    }

    private void apply(Bitmap bmp) {
        User me = SessionManager.getCurrentUser();
        if (me != null && AvatarUtil.save(ctx, me, bmp)) {
            Toast.makeText(ctx, "Profile photo updated.", Toast.LENGTH_SHORT).show();
            if (onChanged != null) onChanged.run();
        } else {
            Toast.makeText(ctx, "That picture could not be used. Try another one.", Toast.LENGTH_SHORT).show();
        }
    }

    public void show(final Context context) {
        ctx = context.getApplicationContext();
        final User me = SessionManager.getCurrentUser();
        if (me == null) return;
        final List<String> items = new ArrayList<>();
        items.add("Take a photo");
        items.add("Choose from gallery");
        final boolean has = me.getPhotoUri() != null && !me.getPhotoUri().isEmpty();
        if (has) items.add("Remove photo");
        new MaterialAlertDialogBuilder(context)
                .setTitle("Profile photo")
                .setItems(items.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        try {
                            if (which == 0) camera.launch(null);
                            else if (which == 1) gallery.launch("image/*");
                            else {
                                AvatarUtil.remove(me);
                                if (onChanged != null) onChanged.run();
                            }
                        } catch (RuntimeException e) {
                            Toast.makeText(ctx, "No camera or gallery app is available.", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
