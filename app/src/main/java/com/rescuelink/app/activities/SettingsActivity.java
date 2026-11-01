package com.rescuelink.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.rescuelink.app.R;
import com.rescuelink.app.net.Config;
import com.rescuelink.app.utils.MenuUi;
import com.rescuelink.app.utils.SessionManager;
import com.rescuelink.app.utils.ThemeManager;
import com.rescuelink.app.utils.UiAnim;

/** App settings: emergency call method, reduced motion, and a shortcut to the system permission screen. */
public class SettingsActivity extends AppCompatActivity {
    private static final String[] MODE_KEYS = {Config.MODE_AUTO, Config.MODE_APP, Config.MODE_PHONE, Config.MODE_SMS};
    private static final String[] MODE_NAMES = {"Auto (internet first, then SIM)", "Internet call (Wi-Fi or data)", "Phone call", "SMS"};
    private static final String[] MODE_SHORT = {"Auto", "Internet", "Phone", "SMS"};

    private View callRow;
    private View cloudRow;
    private View themeRow;
    private View mapRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SessionManager.requireLogin(this)) return;
        setContentView(R.layout.activity_settings);
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        LinearLayout cards = findViewById(R.id.settings_cards);
        LayoutInflater inf = getLayoutInflater();

        LinearLayout emergency = MenuUi.section(this, cards, "Emergency");
        callRow = MenuUi.row(inf, emergency, R.drawable.ic_phone, R.color.critical, R.color.coral_pale,
                "Call method", modeShort(), true, new View.OnClickListener() {
                    @Override public void onClick(View v) { chooseMode(); }
                });

        LinearLayout online = MenuUi.section(this, cards, "Online service");
        cloudRow = MenuUi.row(inf, online, R.drawable.ic_broadcast, R.color.navy, R.color.primary_container,
                "Server address", cloudValue(), true, new View.OnClickListener() {
                    @Override public void onClick(View v) { editCloud(); }
                });

        LinearLayout look = MenuUi.section(this, cards, "Appearance");
        themeRow = MenuUi.row(inf, look, R.drawable.ic_dashboard, R.color.navy, R.color.primary_container,
                "Theme color", ThemeManager.OPTIONS[ThemeManager.index(this)].name, true, new View.OnClickListener() {
                    @Override public void onClick(View v) { chooseTheme(); }
                });
        mapRow = MenuUi.row(inf, look, R.drawable.ic_map, R.color.navy, R.color.primary_container,
                "Map key (MapTiler)", mapKeyValue(), true, new View.OnClickListener() {
                    @Override public void onClick(View v) { editMapKey(); }
                });
        View motion = MenuUi.row(inf, look, R.drawable.ic_layers, R.color.navy, R.color.primary_container,
                "Reduce motion", null, false, null);
        motion.setClickable(true);
        final MaterialSwitch sw = motion.findViewById(R.id.row_switch);
        sw.setVisibility(View.VISIBLE);
        sw.setChecked(UiAnim.isReduced());
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                UiAnim.setReduced(SettingsActivity.this, on);
            }
        });
        motion.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sw.toggle(); }
        });

        LinearLayout device = MenuUi.section(this, cards, "Device");
        MenuUi.row(inf, device, R.drawable.ic_lock, "App permissions", "Location, calls, SMS",
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", getPackageName(), null));
                        startActivity(i);
                    }
                });
    }

    /** A row of colour circles. Tapping one applies it to the whole app straight away. */
    private void chooseTheme() {
        final int dp = (int) getResources().getDisplayMetrics().density;
        final int selected = ThemeManager.index(this);
        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(4);
        grid.setPadding(16 * dp, 12 * dp, 16 * dp, 4 * dp);
        final android.app.Dialog[] dialog = new android.app.Dialog[1];
        for (int i = 0; i < ThemeManager.OPTIONS.length; i++) {
            final int index = i;
            ThemeManager.Option o = ThemeManager.OPTIONS[i];
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            cell.setPadding(6 * dp, 6 * dp, 6 * dp, 10 * dp);
            android.widget.TextView dot = new android.widget.TextView(this);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(o.swatch);
            if (i == selected) g.setStroke(3 * dp, 0xFF9CA3AF);
            dot.setBackground(g);
            dot.setGravity(android.view.Gravity.CENTER);
            dot.setTextColor(0xFFFFFFFF);
            dot.setTextSize(20);
            dot.setText(i == selected ? "\u2713" : "");
            cell.addView(dot, new LinearLayout.LayoutParams(48 * dp, 48 * dp));
            android.widget.TextView label = new android.widget.TextView(this);
            label.setText(o.name);
            label.setTextSize(12);
            label.setGravity(android.view.Gravity.CENTER);
            label.setPadding(0, 4 * dp, 0, 0);
            cell.addView(label);
            cell.setContentDescription(o.name + " theme");
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (dialog[0] != null) dialog[0].dismiss();
                    if (index == ThemeManager.index(SettingsActivity.this)) return;
                    ThemeManager.set(SettingsActivity.this, index);
                    recreate();      // this screen redraws now; other screens redraw when you return to them
                }
            });
            android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams();
            lp.width = 72 * dp;
            lp.setMargins(0, 0, 0, 0);
            grid.addView(cell, lp);
        }
        dialog[0] = new MaterialAlertDialogBuilder(this)
                .setTitle("Theme color")
                .setView(grid)
                .setNegativeButton("Close", null)
                .show();
    }

    private String mapKeyValue() {
        return Config.mapKey(this).isEmpty() ? "Default map" : "MapTiler style";
    }

    /** Paste a MapTiler API key to switch the map to MapTiler's colourful OpenStreetMap style. */
    private void editMapKey() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("MapTiler API key");
        et.setSingleLine(true);
        et.setText(Config.mapKey(this));
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(et);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Map key")
                .setMessage("Paste a free MapTiler key (cloud.maptiler.com) for the detailed OpenStreetMap style. Leave empty for the standard map. Reopen the map to see the change.")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        Config.setMapKey(SettingsActivity.this, et.getText().toString());
                        MenuUi.setValue(mapRow, mapKeyValue());
                    }
                })
                .show();
    }

    private String cloudValue() {
        return Config.cloudEnabled(this) ? "On" : "Not set";
    }

    /** Where to paste the Firebase Realtime Database address that connects the phones. */
    private void editCloud() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("https://your-project-default-rtdb.firebasedatabase.app");
        et.setSingleLine(true);
        et.setText(Config.cloudUrl(this));
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(et);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Online service")
                .setMessage("Paste your Realtime Database address to share live responder locations, reports and free internet calls between phones. Leave it empty to work offline on this phone only.")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        Config.setCloudUrl(SettingsActivity.this, et.getText().toString());
                        com.rescuelink.app.data.CloudSync.reset();
                        MenuUi.setValue(cloudRow, cloudValue());
                    }
                })
                .show();
    }

    private String modeShort() {
        String cur = Config.mode(this);
        for (int i = 0; i < MODE_KEYS.length; i++) if (MODE_KEYS[i].equals(cur)) return MODE_SHORT[i];
        return MODE_SHORT[0];
    }

    private void chooseMode() {
        String cur = Config.mode(this);
        int checked = 0;
        for (int i = 0; i < MODE_KEYS.length; i++) if (MODE_KEYS[i].equals(cur)) checked = i;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Emergency call method")
                .setSingleChoiceItems(MODE_NAMES, checked, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        Config.setMode(SettingsActivity.this, MODE_KEYS[which]);
                        MenuUi.setValue(callRow, modeShort());
                        d.dismiss();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
