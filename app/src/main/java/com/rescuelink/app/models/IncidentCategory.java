package com.rescuelink.app.models;

import android.graphics.Color;

public class IncidentCategory {
    private final String name;
    private final int iconRes;
    private final int bgColor;

    public IncidentCategory(String name, int iconRes, String bgColorHex) {
        this.name = name;
        this.iconRes = iconRes;
        this.bgColor = Color.parseColor(bgColorHex);
    }

    public String getName() { return name; }
    public int getIconRes() { return iconRes; }
    public int getBgColor() { return bgColor; }
}
