package com.rescuelink.app.utils;

import android.app.Activity;
import android.content.DialogInterface;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** The "enter the 6-digit code" box. Sends the code, checks it, and calls back only when it is right. */
public final class OtpDialog {
    private OtpDialog() { }

    public static void send(Activity a, String mobile) {
        String[] shown = new String[1];
        OtpManager.Sent how = OtpManager.issue(a, mobile, shown);
        if (how == OtpManager.Sent.FAILED) {
            Toast.makeText(a, "Could not send the code. Check your SIM and try again.", Toast.LENGTH_LONG).show();
        } else if (how == OtpManager.Sent.ON_SCREEN) {
            new MaterialAlertDialogBuilder(a).setTitle("Test mode")
                    .setMessage("This phone cannot send SMS, so the code is shown here: " + shown[0])
                    .setPositiveButton("OK", null).show();
        } else {
            Toast.makeText(a, "Code sent to " + mobile + ".", Toast.LENGTH_SHORT).show();
        }
    }

    public static void show(final Activity a, final String mobile, final Runnable onVerified) {
        final EditText code = new EditText(a);
        code.setInputType(InputType.TYPE_CLASS_NUMBER);
        code.setHint("6-digit code");
        code.setMaxLines(1);
        code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        int pad = (int) (20 * a.getResources().getDisplayMetrics().density);
        FrameLayout box = new FrameLayout(a);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(code);
        send(a, mobile);
        final AlertDialog d = new MaterialAlertDialogBuilder(a)
                .setTitle("Verify " + mobile)
                .setMessage("Enter the code we sent to this number.")
                .setView(box)
                .setCancelable(false)
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface x, int w) { OtpManager.clear(); }
                })
                .setNeutralButton("Resend", null)
                .setPositiveButton("Verify", null)
                .create();
        d.show();
        final long[] last = {System.currentTimeMillis()};
        d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (System.currentTimeMillis() - last[0] < OtpManager.RESEND_MS) {
                    Toast.makeText(a, "Wait a moment before asking for another code.", Toast.LENGTH_SHORT).show();
                    return;
                }
                last[0] = System.currentTimeMillis();
                send(a, mobile);
            }
        });
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                OtpManager.Result r = OtpManager.check(mobile, code.getText().toString());
                if (r == OtpManager.Result.OK) { d.dismiss(); onVerified.run(); }
                else if (r == OtpManager.Result.WRONG) code.setError("Wrong code");
                else code.setError(r == OtpManager.Result.EXPIRED ? "Code expired. Tap Resend." : "Too many tries. Tap Resend.");
            }
        });
    }
}
