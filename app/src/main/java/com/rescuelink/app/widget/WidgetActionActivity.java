package com.rescuelink.app.widget;

import android.content.DialogInterface;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rescuelink.app.emergency.EmergencyActions;
import com.rescuelink.app.emergency.EmergencyActions.Svc;
import com.rescuelink.app.net.Config;

/**
 * Invisible screen that receives a widget tap and starts the emergency call or SMS.
 * Works signed in or not, like the four buttons on the sign-in screen.
 */
public class WidgetActionActivity extends AppCompatActivity {
    public static final String EXTRA_SVC = "svc";

    /**
     * true  = ask "Call now?" first, so a pocket or child tap cannot call the police by accident.
     * false = the tap starts the call at once (one tap faster, but easier to trigger by mistake).
     */
    private static final boolean CONFIRM_FIRST = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Svc s = Svc.fromKey(getIntent().getStringExtra(EXTRA_SVC));
        if (s == null) { finish(); return; }

        final Runnable done = new Runnable() {
            @Override public void run() { finish(); }
        };

        // No SIM: EmergencyActions shows its own explanation and calls done when it is closed.
        if (!CONFIRM_FIRST || EmergencyActions.effectiveMode(this) == null) {
            EmergencyActions.start(this, s, done);
            return;
        }

        boolean sms = Config.MODE_SMS.equals(EmergencyActions.effectiveMode(this));
        new MaterialAlertDialogBuilder(this)
                .setTitle("Emergency: " + s.label)
                .setMessage(sms ? "Send an emergency SMS with your location to " + s.label + "?"
                                : "Call " + s.label + " now?")
                .setPositiveButton(sms ? "Send SMS" : "Call now", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        EmergencyActions.start(WidgetActionActivity.this, s, done);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { done.run(); }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override public void onCancel(DialogInterface d) { done.run(); }
                })
                .show();
    }
}
