package com.rescuelink.app.fragments;

import com.rescuelink.app.utils.ThemeColors;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rescuelink.app.R;
import com.rescuelink.app.activities.AlertsActivity;
import com.rescuelink.app.activities.ContactsActivity;
import com.rescuelink.app.activities.EditProfileActivity;
import com.rescuelink.app.activities.LoginActivity;
import com.rescuelink.app.activities.SettingsActivity;
import com.rescuelink.app.models.User;
import com.rescuelink.app.utils.MenuUi;
import com.rescuelink.app.utils.SessionManager;
import com.rescuelink.app.utils.UiUtils;

import java.util.Locale;

/** Profile: avatar and status on top, then grouped rows (personal info, account, general, log out). */
public class ProfileFragment extends Fragment {
    private View rowUser, rowContact, rowAddress, rowBlood;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        final android.content.Context ctx = requireContext();
        LayoutInflater inf = getLayoutInflater();
        LinearLayout cards = v.findViewById(R.id.profile_cards);
        boolean citizen = User.ROLE_CITIZEN.equals(SessionManager.role());

        View.OnClickListener edit = new View.OnClickListener() {
            @Override public void onClick(View x) { startActivity(new Intent(ctx, EditProfileActivity.class)); }
        };
        v.findViewById(R.id.btn_avatar_edit).setOnClickListener(edit);

        // Personal info (read-only rows)
        LinearLayout info = MenuUi.section(ctx, cards, "Personal info");
        rowUser = MenuUi.row(inf, info, R.drawable.ic_person, R.color.navy, R.color.primary_container,
                "Sign-in number", "", false, null);
        rowContact = MenuUi.row(inf, info, R.drawable.ic_phone, R.color.navy, R.color.primary_container,
                "Contact", "", false, null);
        rowAddress = MenuUi.row(inf, info, R.drawable.ic_map_pin, R.color.navy, R.color.primary_container,
                "Address", "", false, null);
        if (citizen) {
            rowBlood = MenuUi.row(inf, info, R.drawable.ic_cat_medical, R.color.critical, R.color.coral_pale,
                    "Blood type", "", false, null);
        }

        // Account
        LinearLayout account = MenuUi.section(ctx, cards, "Account");
        MenuUi.row(inf, account, R.drawable.ic_edit, "Edit profile", null, edit);
        if (citizen) {
            MenuUi.row(inf, account, R.drawable.ic_people, "Emergency contacts", null, new View.OnClickListener() {
                @Override public void onClick(View x) { startActivity(new Intent(ctx, ContactsActivity.class)); }
            });
        }
        MenuUi.row(inf, account, R.drawable.ic_bell, "Notifications", null, new View.OnClickListener() {
            @Override public void onClick(View x) { startActivity(new Intent(ctx, AlertsActivity.class)); }
        });

        // General
        LinearLayout general = MenuUi.section(ctx, cards, "General");
        MenuUi.row(inf, general, R.drawable.ic_settings, "Settings", null, new View.OnClickListener() {
            @Override public void onClick(View x) { startActivity(new Intent(ctx, SettingsActivity.class)); }
        });
        MenuUi.row(inf, general, R.drawable.ic_info, "About iResponse", null, new View.OnClickListener() {
            @Override public void onClick(View x) {
                new MaterialAlertDialogBuilder(ctx)
                        .setTitle("About iResponse")
                        .setMessage("iResponse connects citizens, responders and the DRRMO operations desk. "
                                + "Maps are from OpenStreetMap contributors. The assistant gives general guidance only. "
                                + "In a life-threatening emergency, call your nearest station.")
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }
        });

        // Log out
        LinearLayout out = MenuUi.section(ctx, cards, null);
        MenuUi.row(inf, out, R.drawable.ic_logout, R.color.critical, R.color.coral_pale, "Log out", null, false,
                new View.OnClickListener() {
                    @Override public void onClick(View x) { confirmLogout(); }
                });
        ((TextView) out.findViewById(R.id.row_label)).setTextColor(
                com.rescuelink.app.utils.ThemeColors.color(ctx, R.color.critical));
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Log out?")
                .setMessage("You will return to the login screen.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log out", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        com.rescuelink.app.utils.LocationShareService.stop(requireContext());
                        com.rescuelink.app.utils.AlarmCenter.stopAll(requireContext());
                        com.rescuelink.app.net.Config.clearSession(requireContext());
                        if (SessionManager.getCurrentUser() != null) com.rescuelink.app.data.CloudSync.signedOut(SessionManager.getCurrentUser().getUsername());
                        SessionManager.clear();
                        Intent i = new Intent(requireContext(), LoginActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                    }
                })
                .show();
    }

    private static String orNotSet(String s) { return s == null || s.trim().isEmpty() ? "Not set" : s; }

    @Override
    public void onResume() {
        super.onResume();
        View v = getView();
        User u = SessionManager.getCurrentUser();
        if (v == null || u == null) return;
        ((TextView) v.findViewById(R.id.tv_profile_avatar))
                .setText(u.getFullName().trim().isEmpty() ? "?" : u.getFullName().trim().substring(0, 1).toUpperCase(Locale.ROOT));
        ImageView photo = v.findViewById(R.id.iv_profile_photo);
        if (u.getPhotoUri() != null) {
            try {
                photo.setImageURI(Uri.parse(u.getPhotoUri()));
                photo.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                photo.setVisibility(View.GONE);
            }
        }
        ((TextView) v.findViewById(R.id.tv_profile_name)).setText(u.getFullName());
        boolean responder = User.ROLE_RESPONDER.equals(u.getRole());
        ((TextView) v.findViewById(R.id.tv_profile_role)).setText(u.getRole()
                + (responder && !u.getStationName().isEmpty() ? " \u00B7 " + u.getStationName() : ""));

        // Profile status chip: duty status for responders, account status for everyone else.
        TextView status = v.findViewById(R.id.tv_profile_status);
        if (!u.isActive()) {
            UiUtils.styleBadge(status, "\u25CF Disabled", UiUtils.GRAY_FG, UiUtils.GRAY_BG);
        } else if (responder) {
            boolean onDuty = User.DUTY_ON.equals(u.getDutyStatus());
            UiUtils.styleBadge(status, "\u25CF " + u.getDutyStatus(),
                    onDuty ? UiUtils.GREEN_FG : UiUtils.AMBER_FG, onDuty ? UiUtils.GREEN_BG : UiUtils.AMBER_BG);
        } else {
            UiUtils.styleBadge(status, "\u25CF Active", UiUtils.GREEN_FG, UiUtils.GREEN_BG);
        }

        MenuUi.setValue(rowUser, orNotSet(u.getUsername()));
        MenuUi.setValue(rowContact, orNotSet(u.getContact()));
        MenuUi.setValue(rowAddress, orNotSet(u.getAddress()));
        if (rowBlood != null) MenuUi.setValue(rowBlood, orNotSet(u.getBloodType()));
    }
}
