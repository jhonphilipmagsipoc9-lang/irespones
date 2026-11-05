package com.rescuelink.app.fragments;

import com.rescuelink.app.utils.ThemeColors;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rescuelink.app.R;
import com.rescuelink.app.activities.AlertsActivity;
import com.rescuelink.app.activities.CitizenActivity;
import com.rescuelink.app.activities.IncidentChatActivity;
import com.rescuelink.app.activities.IncidentDetailActivity;
import com.rescuelink.app.activities.MessagesActivity;
import com.rescuelink.app.activities.ReportActivity;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.emergency.EmergencyActions;
import com.rescuelink.app.emergency.EmergencyActions.Svc;
import com.rescuelink.app.models.AlertItem;
import com.rescuelink.app.models.Incident;
import com.rescuelink.app.net.Config;
import com.rescuelink.app.net.Net;
import com.rescuelink.app.utils.DashUi;
import com.rescuelink.app.utils.LocationHelper;
import com.rescuelink.app.utils.SessionManager;
import com.rescuelink.app.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Citizen home, built like a 911 screen: four big buttons (Police, Fire, DRRMO, Medical). One tap starts the
 * call. Nothing else competes for attention.
 */
public class HomeFragment extends Fragment {
    private static final String PREFS = "rescuelink_home";
    private static final long DOUBLE_TAP_GUARD_MS = 2500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable statusLoop = new Runnable() {
        @Override public void run() {
            refreshStatus();
            refreshMessageBadge();
            showActiveReport();
            handler.postDelayed(this, 3000);
        }
    };

    private TextView tvBellBadge, tvConn, tvModeHint, tvAlertTitle, tvAlertMsg;
    private View cardAlert, messagesBtn;
    private LinearLayout activeContainer, modeGroup;
    private long lastTap;
    private android.animation.Animator pulseAnim;
    private final List<TextView> chips = new ArrayList<>();

    private final String[] modeKeys = {Config.MODE_AUTO, Config.MODE_PHONE, Config.MODE_SMS};
    private final String[] modeNames = {"Auto", "Phone", "SMS"};

    private final ActivityResultLauncher<String[]> permLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            new androidx.activity.result.ActivityResultCallback<java.util.Map<String, Boolean>>() {
                @Override public void onActivityResult(java.util.Map<String, Boolean> r) { refreshStatus(); }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        ((TextView) v.findViewById(R.id.tv_greeting)).setText(UiUtils.greeting() + ", " + SessionManager.firstName() + "!");
        ((TextView) v.findViewById(R.id.tv_avatar)).setText(SessionManager.firstName().substring(0, 1).toUpperCase(Locale.ROOT));
        tvBellBadge = v.findViewById(R.id.tv_bell_badge);
        tvConn = v.findViewById(R.id.tv_conn);
        tvModeHint = v.findViewById(R.id.tv_mode_hint);
        cardAlert = v.findViewById(R.id.card_alert);
        tvAlertTitle = v.findViewById(R.id.tv_alert_title);
        tvAlertMsg = v.findViewById(R.id.tv_alert_msg);
        activeContainer = v.findViewById(R.id.active_container);
        modeGroup = v.findViewById(R.id.mode_group);
        messagesBtn = v.findViewById(R.id.btn_messages);

        v.findViewById(R.id.btn_bell).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { startActivity(new Intent(requireContext(), AlertsActivity.class)); }
        });
        cardAlert.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { startActivity(new Intent(requireContext(), AlertsActivity.class)); }
        });
        v.findViewById(R.id.tv_avatar).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { ((CitizenActivity) requireActivity()).selectTab(R.id.nav_profile); }
        });
        v.findViewById(R.id.btn_report).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { startActivity(new Intent(requireContext(), ReportActivity.class)); }
        });
        messagesBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { startActivity(new Intent(requireContext(), MessagesActivity.class)); }
        });

                     bindService(v.findViewById(R.id.card_police), Svc.POLICE);
        bindService(v.findViewById(R.id.card_fire), Svc.FIRE);
        bindService(v.findViewById(R.id.card_drrmo), Svc.DRRMO);
        bindService(v.findViewById(R.id.card_medical), Svc.MEDICAL);
        buildModeChips();
        pulseAnim = com.rescuelink.app.utils.UiAnim.pulse(v.findViewById(R.id.pulse_ring));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        com.rescuelink.app.utils.UiAnim.stop(pulseAnim);
        pulseAnim = null;
    }

    private static int tintFor(Svc s) {
        switch (s) {
            case POLICE: return R.color.tint_blue;
            case FIRE: return R.color.tint_orange;
            case DRRMO: return R.color.tint_green;
            default: return R.color.tint_pink;
        }
    }

    private void bindService(View card, final Svc s) {
        Context ctx = requireContext();
        card.setBackground(UiUtils.rounded(ThemeColors.color(ctx, tintFor(s)), UiUtils.dp(ctx, 28)));
        card.setClipToOutline(true);
        ImageView icon = card.findViewById(R.id.svc_icon);
        icon.setImageResource(s.icon);
        // White disc behind a coloured icon, like the Medilink tiles.
        icon.setBackground(UiUtils.rounded(0xFFFFFFFF, UiUtils.dp(ctx, 23)));
        int pad = UiUtils.dp(ctx, 10);
        icon.setPadding(pad, pad, pad, pad);
        ImageViewCompat.setImageTintList(icon, android.content.res.ColorStateList.valueOf(ThemeColors.color(ctx, s.color)));
        TextView label = card.findViewById(R.id.svc_label);
        label.setText(s.label);
        label.setTextColor(ThemeColors.color(ctx, R.color.navy));
        label.setTextSize(22);
        label.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        ((TextView) card.findViewById(R.id.svc_sub)).setTextColor(ThemeColors.color(ctx, R.color.text_on_tint));
        card.setContentDescription("Call " + s.label + " now");
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { dispatch(x, s); }
        });
    }

    /** The one place a call is started, for a service tile and for SOS. A second tap must not start a second call. */
    private void dispatch(View hapticSource, Svc s) {
        long now = System.currentTimeMillis();
        if (now - lastTap < DOUBLE_TAP_GUARD_MS) return;
        lastTap = now;
        if (hapticSource != null) hapticSource.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        LocationHelper.refresh(requireContext());
        EmergencyActions.start(requireActivity(), s);
    }

    // ------------------------------------------------------------------ call method chooser
    private void buildModeChips() {
        Context ctx = requireContext();
        modeGroup.removeAllViews();
        chips.clear();
        for (int k = 0; k < modeKeys.length; k++) {
            final String key = modeKeys[k];
            TextView t = new TextView(ctx);
            t.setText(modeNames[k]);
            t.setGravity(android.view.Gravity.CENTER);
            t.setTextSize(14);
            t.setMinHeight(UiUtils.dp(ctx, 44));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginEnd(k == modeKeys.length - 1 ? 0 : UiUtils.dp(ctx, 6));
            t.setLayoutParams(lp);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) {
                    Config.setMode(requireContext(), key);
                    styleChips();
                    refreshStatus();
                }
            });
            chips.add(t);
            modeGroup.addView(t);
        }
        styleChips();
    }

    private void styleChips() {
        Context ctx = requireContext();
        String cur = Config.mode(ctx);
        for (int k = 0; k < chips.size(); k++) {
            boolean on = modeKeys[k].equals(cur);
            TextView t = chips.get(k);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(UiUtils.dp(ctx, 22));
            bg.setColor(on ? ThemeColors.color(ctx, R.color.primary) : 0xFFFFFFFF);
            bg.setStroke(UiUtils.dp(ctx, 1), ThemeColors.color(ctx, on ? R.color.primary : R.color.border));
            t.setBackground(bg);
            t.setTextColor(on ? 0xFFFFFFFF : ThemeColors.color(ctx, R.color.text_main));
            t.setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    /** The green, amber or red line that tells the person, before they need it, what a tap will do. */
    private void refreshStatus() {
        if (!isAdded() || tvConn == null) return;
        Context ctx = requireContext();
        boolean sim = Net.canUseSim(ctx);
        int color;
        String text;
        if (com.rescuelink.app.call.CallLauncher.available(ctx)) {
            color = 0xFF15803D;
            text = sim ? "Online \u00B7 free internet call, phone call and SMS ready"
                    : "Online \u00B7 free internet call ready (no load needed)";
        } else if (sim) {
            color = 0xFF15803D;
            text = "SIM ready \u00B7 phone call and SMS ready";
        } else {
            color = 0xFFD92D20;
            text = "No working SIM \u00B7 calling and SMS unavailable";
        }
        tvConn.setText(text);
        tvConn.setBackground(UiUtils.rounded(color, UiUtils.dp(ctx, 16)));
        tvModeHint.setText(EmergencyActions.describe(ctx));
    }

    /** The Messages button shows how many replies are waiting as a red count. */
    private void refreshMessageBadge() {
        if (!isAdded() || messagesBtn == null) return;
        int msgs = DataRepository.unreadMessagesFor(SessionManager.getCurrentUser());
        ((MaterialButton) messagesBtn).setText(com.rescuelink.app.utils.BadgeUi.label("Messages", msgs));
    }

    private String activeSig = "";

    // ------------------------------------------------------------------ the person's open report
    private void showActiveReport() {
        if (activeContainer == null || !isAdded()) return;
        Incident open = null;
        for (Incident i : DataRepository.getIncidentsFor(SessionManager.username())) {
            if (i.isActive()) { open = i; break; }
        }
        // Rebuilt only when something on the card changed, so it does not flicker while it refreshes.
        int unreadChat = open == null ? 0 : DataRepository.unreadMessages(SessionManager.getCurrentUser(), open);
        String sig = open == null ? "" : open.getReference() + "|" + open.getStatus() + "|" + open.getResponderName()
                + "|" + open.getAssignedStation() + "|" + unreadChat;
        if (sig.equals(activeSig) && (open == null) == (activeContainer.getChildCount() == 0)) return;
        activeSig = sig;
        activeContainer.removeAllViews();
        if (open == null) return;
        final Incident inc = open;
        Context ctx = requireContext();
        LinearLayout c = DashUi.card(ctx, activeContainer, R.color.white, R.color.border);
        ((ViewGroup) c.getParent()).setLayoutParams(DashUi.lp(ctx, 16, 0));
        DashUi.text(ctx, c, "Your active emergency", 12, R.color.critical, true);
        DashUi.text(ctx, c, inc.displayTitle(), 17, R.color.navy, true);
        DashUi.text(ctx, c, inc.getReference() + " \u00B7 " + inc.getStatus().getLabel(), 14, R.color.text_main, false);
        String who = inc.getResponderName() == null
                ? "Responders are being alerted."
                : inc.getResponderName() + (inc.getAssignedStation() == null ? "" : " from " + inc.getAssignedStation()) + " is on it.";
        DashUi.text(ctx, c, who, 13, R.color.text_secondary, false).setPadding(0, UiUtils.dp(ctx, 4), 0, UiUtils.dp(ctx, 8));
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton track = new MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonStyle);
        track.setText("Track");
        com.rescuelink.app.utils.UiUtils.fitButton(track);
        track.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                Intent it = new Intent(requireContext(), IncidentDetailActivity.class);
                it.putExtra("ref", inc.getReference());
                startActivity(it);
            }
        });
        row.addView(track);
        MaterialButton msg = new MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        msg.setText(com.rescuelink.app.utils.BadgeUi.label("Message", unreadChat));
        com.rescuelink.app.utils.UiUtils.fitButton(msg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(UiUtils.dp(ctx, 8));
        msg.setLayoutParams(lp);
        msg.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                Intent it = new Intent(requireContext(), IncidentChatActivity.class);
                it.putExtra(IncidentChatActivity.EXTRA_REF, inc.getReference());
                startActivity(it);
            }
        });
        row.addView(msg);
        c.addView(row);
    }

    // ------------------------------------------------------------------ one-time permission setup
    /** Asked once, up front, so that during a real emergency no permission pop-up ever gets in the way. */
    private void maybeAskPermissions() {
        final Context ctx = requireContext();
        final SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.getBoolean("asked_v1", false)) return;
        final List<String> need = new ArrayList<>();
        String[] all = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CALL_PHONE,
                Manifest.permission.SEND_SMS};
        for (String s : all) if (!Net.has(ctx, s)) need.add(s);
        if (need.isEmpty()) { p.edit().putBoolean("asked_v1", true).apply(); return; }
        new MaterialAlertDialogBuilder(ctx)
                .setTitle("Get ready for an emergency")
                .setMessage("To make help one tap away, iResponse needs:\n\n"
                        + "\u2022 Location: so responders know where you are\n"
                        + "\u2022 Phone calls: to call the station directly\n"
                        + "\u2022 SMS: to send your location by text\n\n"
                        + "Allow them now, so nothing pops up when it matters.")
                .setCancelable(false)
                .setPositiveButton("Continue", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        p.edit().putBoolean("asked_v1", true).apply();
                        permLauncher.launch(need.toArray(new String[0]));
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------ lifecycle
    @Override
    public void onResume() {
        super.onResume();
        int unread = DataRepository.unreadAlertCount();
        tvBellBadge.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
        tvBellBadge.setText(String.valueOf(unread));
        if (unread > 0 && tvBellBadge.getParent() instanceof View) com.rescuelink.app.utils.UiAnim.shake((View) tvBellBadge.getParent());
        AlertItem latest = DataRepository.latestAlert();
        if (latest != null && latest.getLevel() != AlertItem.INFO) {
            cardAlert.setVisibility(View.VISIBLE);
            tvAlertTitle.setText(latest.getTitle());
            tvAlertMsg.setText(latest.getMessage());
        } else {
            cardAlert.setVisibility(View.GONE);
        }
        refreshMessageBadge();
        activeSig = "";
        showActiveReport();
        LocationHelper.refresh(requireContext());
        handler.removeCallbacks(statusLoop);
        statusLoop.run();
        maybeAskPermissions();
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(statusLoop);
    }
}
