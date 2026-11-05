package com.rescuelink.app.utils;

import android.app.Activity;
import android.content.DialogInterface;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.User;

/**
 * What an admin may do to an account: look at its public status, enable or disable it, or delete it.
 * Privacy rule: the admin can never read or change a user's personal information (age, address, phone,
 * medical ID) and can never reset a password. Only the owner of an account can change those, from their own profile.
 */
public final class UserAdminActions {
    private UserAdminActions() { }

    public static void show(final Activity a, final User u, final Runnable onChanged) {
        boolean protectedAccount = !DataRepository.canManage(SessionManager.getCurrentUser(), u);
        if (protectedAccount) {
            new MaterialAlertDialogBuilder(a)
                    .setTitle(u.getFullName())
                    .setMessage(details(u) + "\n\nYou cannot disable or delete this account.")
                    .setPositiveButton("Close", null)
                    .show();
            return;
        }
        User viewer = SessionManager.getCurrentUser();
        final boolean canSeeId = viewer != null && viewer.isCommandCenter() && User.ROLE_CITIZEN.equals(u.getRole());
        final String[] items = canSeeId ? new String[]{
                "View account status",
                "View ID photo and face scan",
                u.isActive() ? "Disable account" : "Enable account",
                "Delete account"
        } : new String[]{
                "View account status",
                u.isActive() ? "Disable account" : "Enable account",
                "Delete account"
        };
        new MaterialAlertDialogBuilder(a)
                .setTitle(u.getFullName() + " (" + u.getRole() + ")")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (canSeeId && which == 1) { showVerification(a, u); return; }
                        if (canSeeId && which > 1) which--;        // the rest of the list is the same as for other accounts
                        if (which == 0) {
                            new MaterialAlertDialogBuilder(a).setTitle(u.getFullName())
                                    .setMessage(details(u)).setPositiveButton("Close", null).show();
                        } else if (which == 1) {
                            applyNow(a, u, u.isActive() ? "disable" : "enable", new Runnable() {
                                @Override public void run() {
                                    u.setActive(!u.isActive());
                                    if (!u.isActive()) u.setDutyStatus(User.DUTY_OFF);
                                    DataRepository.log((u.isActive() ? "Account enabled: " : "Account disabled: ") + u.getFullName());
                                    DataRepository.save();
                                    Toast.makeText(a, u.getFullName() + (u.isActive() ? " enabled." : " disabled."),
                                            Toast.LENGTH_SHORT).show();
                                    if (onChanged != null) onChanged.run();
                                }
                            });
                        } else {
                            confirmDelete(a, u, onChanged);
                        }
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    /** Main admin only: the ID photo and face scan the citizen gave when signing up. */
    public static void showVerification(final Activity a, final User u) {
        final int pad = UiUtils.dp(a, 16);
        final android.widget.LinearLayout box = new android.widget.LinearLayout(a);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        final android.widget.TextView status = new android.widget.TextView(a);
        status.setText(u.isFaceVerified()
                ? "Passed the ID and face check on " + UiUtils.formatTime(u.getVerifiedAt()) + "."
                : "Not verified.");
        box.addView(status);
        final android.widget.TextView note = new android.widget.TextView(a);
        note.setText("Loading the pictures\u2026");
        note.setPadding(0, pad / 2, 0, 0);
        box.addView(note);
        android.widget.ScrollView scroll = new android.widget.ScrollView(a);
        scroll.addView(box);
        final androidx.appcompat.app.AlertDialog dlg = new MaterialAlertDialogBuilder(a)
                .setTitle(u.getFullName() + " \u00B7 identity")
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show();
        com.rescuelink.app.data.VerifySync.fetch(a, u, new com.rescuelink.app.data.VerifySync.Callback() {
            @Override public void done(android.graphics.Bitmap id, android.graphics.Bitmap face, boolean found) {
                if (!dlg.isShowing()) return;
                if (!found) {
                    note.setText("No pictures yet. They appear once this citizen opens the app online "
                            + "(accounts made before this update need one sign-in).");
                    return;
                }
                box.removeView(note);
                addPhoto(a, box, "ID photo", id);
                addPhoto(a, box, "Face scan", face);
            }
        });
    }

    private static void addPhoto(Activity a, android.widget.LinearLayout box, String label, android.graphics.Bitmap b) {
        android.widget.TextView t = new android.widget.TextView(a);
        t.setText(label);
        t.setPadding(0, UiUtils.dp(a, 12), 0, UiUtils.dp(a, 4));
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        box.addView(t);
        if (b == null) {
            android.widget.TextView none = new android.widget.TextView(a);
            none.setText("Not available");
            box.addView(none);
            return;
        }
        android.widget.ImageView iv = new android.widget.ImageView(a);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        iv.setImageBitmap(b);
        box.addView(iv, new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, UiUtils.dp(a, 260)));
    }

    /** Accounts live on this phone, so enabling, disabling and deleting take effect right away. */
    private static void applyNow(Activity a, User u, String action, Runnable onOk) { onOk.run(); }

    /** Only non-personal facts. No age, address, phone number, Medical ID or password. */
    public static String details(User u) {
        int reports = DataRepository.getIncidentsFor(u.getUsername()).size();
        int handled = DataRepository.getAssignedTo(u.getFullName()).size();
        StringBuilder sb = new StringBuilder();
        sb.append(u.getRole()).append(" · ").append(u.isActive() ? "Active" : "Disabled").append('\n');
        sb.append("Sign-in number: ").append(u.getUsername()).append('\n');
        sb.append("Joined: ").append(UiUtils.formatTime(u.getCreatedAt()));
        if (User.ROLE_ADMIN.equals(u.getRole())) {
            sb.append("\nScope: ").append(u.isServiceAdmin() ? u.getService() + " admin" : "Command Center (all services)");
        }
        if (User.ROLE_RESPONDER.equals(u.getRole())) {
            sb.append("\nService: ").append(DataRepository.serviceOf(u).isEmpty() ? "Not set" : DataRepository.serviceOf(u));
            sb.append("\nStation: ").append(u.getStationName().isEmpty() ? "Not set" : u.getStationName());
            sb.append("\nDuty: ").append(u.getDutyStatus());
            sb.append("\nReports handled: ").append(handled);
        } else if (User.ROLE_CITIZEN.equals(u.getRole())) {
            sb.append("\nReports filed: ").append(reports);
            sb.append("\nIdentity: ").append(u.isFaceVerified()
                    ? "ID photo and face scan passed on " + UiUtils.formatTime(u.getVerifiedAt())
                    : "Not verified");
        }
        sb.append("\n\nPersonal details and passwords are private. Only the account owner can view or change them.");
        return sb.toString();
    }

    private static void confirmDelete(final Activity a, final User u, final Runnable onChanged) {
        String extra = User.ROLE_RESPONDER.equals(u.getRole())
                ? "Reports assigned to this responder will go back to review."
                : "All reports filed by this account will also be deleted.";
        new MaterialAlertDialogBuilder(a)
                .setTitle("Delete " + u.getFullName() + "?")
                .setMessage("This account will be removed permanently. " + extra)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        applyNow(a, u, "delete", new Runnable() {
                            @Override public void run() {
                                DataRepository.deleteUser(u);
                                Toast.makeText(a, u.getFullName() + " was deleted.", Toast.LENGTH_SHORT).show();
                                if (onChanged != null) onChanged.run();
                            }
                        });
                    }
                })
                .show();
    }
}
