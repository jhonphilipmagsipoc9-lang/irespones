package com.rescuelink.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rescuelink.app.R;
import com.rescuelink.app.models.User;

import java.util.ArrayList;
import java.util.List;

/**
 * An optional profile picture on a form (register, create account). The person taps the picture, takes a photo or
 * picks one, and it is saved for the new account after the account is created. Nothing is required.
 */
public final class PhotoField {
    private final Context ctx;
    private final ImageView preview;
    private final ActivityResultLauncher<String> gallery;
    private final ActivityResultLauncher<Void> camera;
    private Bitmap bitmap;

    public PhotoField(ActivityResultCaller host, Context context, View tapTarget, ImageView preview) {
        this.ctx = context;
        this.preview = preview;
        if (preview.getParent() instanceof View) {
            View frame = (View) preview.getParent();
            frame.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            frame.setClipToOutline(true);
        }
        gallery = host.registerForActivityResult(new ActivityResultContracts.GetContent(), new ActivityResultCallback<Uri>() {
            @Override public void onActivityResult(Uri uri) {
                if (uri == null) return;
                Bitmap b = AvatarUtil.load(ctx, uri);
                if (b == null) Toast.makeText(ctx, "That picture could not be used. Try another one.", Toast.LENGTH_SHORT).show();
                else set(b);
            }
        });
        camera = host.registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), new ActivityResultCallback<Bitmap>() {
            @Override public void onActivityResult(Bitmap b) { if (b != null) set(b); }
        });
        tapTarget.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { show(); }
        });
    }

    private void set(Bitmap b) {
        bitmap = b;
        preview.setPadding(0, 0, 0, 0);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setImageBitmap(b);
    }

    private void clear() {
        bitmap = null;
        int pad = (int) (14 * ctx.getResources().getDisplayMetrics().density);
        preview.setPadding(pad, pad, pad, pad);
        preview.setImageResource(R.drawable.ic_person);
    }

    private void show() {
        final List<String> items = new ArrayList<>();
        items.add("Take a photo");
        items.add("Choose from gallery");
        if (bitmap != null) items.add("Remove photo");
        new MaterialAlertDialogBuilder(preview.getContext())
                .setTitle("Profile photo (optional)")
                .setItems(items.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        try {
                            if (which == 0) camera.launch(null);
                            else if (which == 1) gallery.launch("image/*");
                            else clear();
                        } catch (RuntimeException e) {
                            Toast.makeText(ctx, "No camera or gallery app is available.", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public boolean has() { return bitmap != null; }

    /** Saves the chosen picture as this account's profile picture and shares it online. Does nothing when none was chosen. */
    public void applyTo(User u) {
        if (bitmap != null && u != null) AvatarUtil.save(ctx, u, bitmap);
    }
}
