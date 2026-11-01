package com.rescuelink.app.utils;

import android.app.Activity;
import android.content.DialogInterface;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rescuelink.app.data.DataRepository;

/** One-tap data clean-up for the admin. Every action asks first. */
public final class AdminTools {
    private AdminTools() { }

    private static final int CLOSED = 0, DRAFTS = 1, DISABLED = 2, ALERTS = 3, LOG = 4;

    public static void showCleanup(final Activity a, final Runnable onDone) {
        final String[] items = {
                "Delete stations (" + DataRepository.getStations().size() + ")",
                "Delete resolved and cancelled reports (" + DataRepository.countClosedIncidents() + ")",
                "Delete draft reports (" + DataRepository.countDrafts() + ")",
                "Delete disabled accounts (" + DataRepository.countDisabledUsers() + ")",
                "Delete all broadcasts (" + DataRepository.getAlerts().size() + ")",
                "Clear activity log (" + DataRepository.getLog().size() + ")"
        };
        new MaterialAlertDialogBuilder(a)
                .setTitle("Data clean-up")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            a.startActivity(new android.content.Intent(a,
                                    com.rescuelink.app.activities.ManageStationsActivity.class));
                        } else {
                            confirm(a, which - 1, items[which], onDone);
                        }
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private static void confirm(final Activity a, final int what, String label, final Runnable onDone) {
        new MaterialAlertDialogBuilder(a)
                .setTitle("Are you sure?")
                .setMessage(label + "\n\nThis cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        int n;
                        if (what == CLOSED) n = DataRepository.deleteClosedIncidents();
                        else if (what == DRAFTS) n = DataRepository.deleteDrafts();
                        else if (what == DISABLED) n = DataRepository.deleteDisabledUsers();
                        else if (what == ALERTS) n = DataRepository.clearAlerts();
                        else {
                            n = DataRepository.getLog().size();
                            DataRepository.clearLog();
                        }
                        Toast.makeText(a, n + " removed.", Toast.LENGTH_SHORT).show();
                        if (onDone != null) onDone.run();
                    }
                })
                .show();
    }
}
