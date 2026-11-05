package com.rescuelink.app.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.rescuelink.app.R;

/** Small view builders shared by the responder and admin dashboards. */
public final class DashUi {
    private DashUi() { }

    public static LinearLayout.LayoutParams lp(Context c, int topDp, int bottomDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = UiUtils.dp(c, topDp);
        p.bottomMargin = UiUtils.dp(c, bottomDp);
        return p;
    }

    public static TextView text(Context c, ViewGroup parent, String s, int sp, int colorRes, boolean medium) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(ThemeColors.color(c, colorRes));
        if (medium) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        parent.addView(t);
        return t;
    }

    public static void section(Context c, LinearLayout parent, String title, String action, Runnable onAction) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setLayoutParams(lp(c, 16, 6));
        TextView t = new TextView(c);
        t.setText(title);
        t.setTextSize(17);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setTextColor(ThemeColors.color(c, R.color.text_main));
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);
        if (action != null) {
            TextView b = new TextView(c);
            b.setText(action);
            b.setTextSize(14);
            b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            b.setTextColor(ThemeColors.color(c, R.color.primary));
            b.setPadding(UiUtils.dp(c, 8), UiUtils.dp(c, 8), UiUtils.dp(c, 4), UiUtils.dp(c, 8));
            b.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    onAction.run();
                }
            });
            row.addView(b);
        }
        parent.addView(row);
    }

    /** Row of equal tiles. Returns nothing; values and labels must be the same length. */
    public static void statRow(LayoutInflater inf, LinearLayout parent, String[] values, String[] labels, int[] colors) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < values.length; i++) {
            View t = inf.inflate(R.layout.tile_stat, row, false);
            TextView v = t.findViewById(R.id.stat_value);
            int asInt = -1;
            try { asInt = Integer.parseInt(values[i]); } catch (NumberFormatException ignored) { }
            if (asInt >= 0) UiAnim.countUp(v, asInt); else v.setText(values[i]);
            if (colors != null) v.setTextColor(colors[i]);
            ((TextView) t.findViewById(R.id.stat_label)).setText(labels[i]);
            if (t instanceof MaterialCardView) {
                int[] tints = {R.color.tint_blue, R.color.tint_pink, R.color.tint_lime, R.color.tint_green};
                MaterialCardView mc = (MaterialCardView) t;
                mc.setCardBackgroundColor(ThemeColors.color(parent.getContext(), tints[i % tints.length]));
                mc.setStrokeWidth(0);
            }
            row.addView(t);
        }
        parent.addView(row);
    }

    /** White rounded card; returns its inner vertical layout. */
    public static LinearLayout card(Context c, LinearLayout parent, int bgColorRes, int strokeColorRes) {
        MaterialCardView card = new MaterialCardView(c);
        card.setCardBackgroundColor(ThemeColors.color(c, bgColorRes));
        card.setRadius(UiUtils.dp(c, 12));
        card.setCardElevation(0f);
        card.setStrokeColor(ThemeColors.color(c, strokeColorRes));
        card.setStrokeWidth(UiUtils.dp(c, 1));
        card.setLayoutParams(lp(c, 0, 10));
        LinearLayout inner = new LinearLayout(c);
        inner.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(c, 16);
        inner.setPadding(pad, pad, pad, pad);
        card.addView(inner);
        parent.addView(card);
        return inner;
    }

    /** Labelled horizontal bar, e.g. incidents per category. */
    public static void bar(Context c, LinearLayout parent, String label, int value, int max, int color) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setLayoutParams(lp(c, 4, 4));

        TextView l = new TextView(c);
        l.setText(label);
        l.setTextSize(13);
        l.setTextColor(ThemeColors.color(c, R.color.text_main));
        l.setLayoutParams(new LinearLayout.LayoutParams(UiUtils.dp(c, 104), ViewGroup.LayoutParams.WRAP_CONTENT));
        l.setMaxLines(1);
        row.addView(l);

        ProgressBar pb = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(Math.max(1, max) * 100);
        android.animation.ObjectAnimator.ofInt(pb, "progress", 0, value * 100).setDuration(700).start();
        pb.setProgressTintList(ColorStateList.valueOf(color));
        pb.setProgressBackgroundTintList(ColorStateList.valueOf(0xFFE1E6F0));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, UiUtils.dp(c, 10), 1f);
        pl.setMarginStart(UiUtils.dp(c, 8));
        pl.setMarginEnd(UiUtils.dp(c, 8));
        pb.setLayoutParams(pl);
        row.addView(pb);

        TextView n = new TextView(c);
        n.setText(String.valueOf(value));
        n.setTextSize(13);
        n.setTextColor(ThemeColors.color(c, R.color.text_secondary));
        n.setMinWidth(UiUtils.dp(c, 22));
        n.setGravity(android.view.Gravity.END);
        row.addView(n);
        parent.addView(row);
    }
}
