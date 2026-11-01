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
        final String[] items = {
                "View account status",
                u.isActive() ? "Disable account" : "Enable account",
                "Delete account"
        };
        new MaterialAlertDialogBuilder(a)
                .setTitle(u.getFullName() + " (" + u.getRole() + ")")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            new MaterialAlertDialogBuilder(a).setTitle(u.getFullName())
                                    .setMessage(details(u)).setPositiveButton("Close", null).show();
                        } else if (which == 1) {
                            u.setActive(!u.isActive());
                            if (!u.isActive()) u.setDutyStatus(User.DUTY_OFF);
                            DataRepository.log((u.isActive() ? "Account enabled: " : "Account disabled: ") + u.getFullName());
                            DataRepository.save();
                            Toast.makeText(a, u.getFullName() + (u.isActive() ? " enabled." : " disabled."),
                                    Toast.LENGTH_SHORT).show();
                            if (onChanged != null) onChanged.run();
                        } else {
                            confirmDelete(a, u, onChanged);
                        }
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

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
                        DataRepository.deleteUser(u);
                        Toast.makeText(a, u.getFullName() + " was deleted.", Toast.LENGTH_SHORT).show();
                        if (onChanged != null) onChanged.run();
                    }
                })
                .show();
    }
}
