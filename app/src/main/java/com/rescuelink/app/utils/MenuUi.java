package com.rescuelink.app.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.card.MaterialCardView;
import com.rescuelink.app.R;

/** Grouped menu rows (icon disc, label, optional value, chevron) used by Profile and Settings. */
public final class MenuUi {
    private MenuUi() { }

    /** Adds a small section title and an empty rounded card under it; returns the card's inner list. */
    public static LinearLayout section(Context c, LinearLayout parent, String title) {
        if (title != null) {
            TextView t = new TextView(c);
            t.setText(title);
            t.setTextSize(13);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setTextColor(ThemeColors.color(c, R.color.text_secondary));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.topMargin = UiUtils.dp(c, 22);
            tp.bottomMargin = UiUtils.dp(c, 8);
            tp.setMarginStart(UiUtils.dp(c, 4));
            t.setLayoutParams(tp);
            parent.addView(t);
        }
        MaterialCardView card = new MaterialCardView(c);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (title == null) cp.topMargin = UiUtils.dp(c, 22);
        card.setLayoutParams(cp);
        LinearLayout inner = new LinearLayout(c);
        inner.setOrientation(LinearLayout.VERTICAL);
        card.addView(inner, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(card);
        return inner;
    }

    /**
     * Adds one row. value may be null. click may be null for a read-only row (no ripple, no chevron).
     * Returns the row so callers can reach R.id.row_value or R.id.row_switch.
     */
    public static View row(LayoutInflater inf, LinearLayout card, int iconRes, int iconColorRes, int discColorRes,
                           String label, String value, boolean chevron, View.OnClickListener click) {
        Context c = card.getContext();
        if (card.getChildCount() > 0) {
            View d = new View(c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UiUtils.dp(c, 1));
            lp.setMarginStart(UiUtils.dp(c, 68));
            d.setLayoutParams(lp);
            d.setBackgroundColor(ThemeColors.color(c, R.color.border));
            card.addView(d);
        }
        View row = inf.inflate(R.layout.row_menu, card, false);
        View disc = row.findViewById(R.id.row_icon_bg);
        disc.setBackground(UiUtils.rounded(ThemeColors.color(c, discColorRes), UiUtils.dp(c, 14)));
        ImageView icon = row.findViewById(R.id.row_icon);
        icon.setImageResource(iconRes);
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(ThemeColors.color(c, iconColorRes)));
        TextView l = row.findViewById(R.id.row_label);
        l.setText(label);
        setValue(row, value);
        row.findViewById(R.id.row_chevron).setVisibility(chevron && click != null ? View.VISIBLE : View.GONE);
        if (click == null) {
            row.setBackground(null);
            row.setClickable(false);
            row.setFocusable(false);
        } else {
            row.setOnClickListener(click);
        }
        card.addView(row);
        return row;
    }

    /** Convenience for the common navy-on-pale-blue row. */
    public static View row(LayoutInflater inf, LinearLayout card, int iconRes, String label, String value,
                           View.OnClickListener click) {
        return row(inf, card, iconRes, R.color.navy, R.color.primary_container, label, value, true, click);
    }

    public static void setValue(View row, String value) {
        TextView v = row.findViewById(R.id.row_value);
        if (value == null || value.isEmpty()) {
            v.setVisibility(View.GONE);
        } else {
            v.setText(value);
            v.setVisibility(View.VISIBLE);
        }
    }
}
