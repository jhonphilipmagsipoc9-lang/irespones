package com.rescuelink.app.models;

import android.graphics.Color;

/** Report statuses. */
public enum IncidentStatus {
    DRAFT("Draft", "#64748B", "#EEF1F5"),
    SUBMITTED("Submitted", "#2563EB", "#E0EAFF"),
    UNDER_REVIEW("Under Review", "#B45309", "#FEF3C7"),
    ASSIGNED("Assigned", "#0A1F44", "#DCE6FA"),
    RESPONDING("Responding", "#6D28D9", "#EDE9FE"),
    ON_SCENE("On Scene", "#0F766E", "#CCFBF1"),
    RESOLVED("Resolved", "#15803D", "#DCFCE7"),
    CANCELLED("Cancelled", "#B42318", "#FDECEA");

    /** Normal progression shown in the tracking timeline. */
    public static final IncidentStatus[] FLOW = {
            SUBMITTED, UNDER_REVIEW, ASSIGNED, RESPONDING, ON_SCENE, RESOLVED
    };

    private final String label;
    private final int textColor;
    private final int bgColor;

    IncidentStatus(String label, String text, String bg) {
        this.label = label;
        this.textColor = Color.parseColor(text);
        this.bgColor = Color.parseColor(bg);
    }

    public String getLabel() { return label; }
    public int getTextColor() { return textColor; }
    public int getBgColor() { return bgColor; }
}
