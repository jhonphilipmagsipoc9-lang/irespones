package com.rescuelink.app.utils;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;

/** Red count pill ("Messages  3") drawn inside a button label, so a count is easy to spot. */
public final class BadgeUi {
    private BadgeUi() { }

    /** The label alone when the count is zero, otherwise the label followed by a red pill with the count. */
    public static CharSequence label(String text, int count) {
        if (count <= 0) return text;
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        sb.append("  ");
        int start = sb.length();
        sb.append(count > 99 ? "99+" : String.valueOf(count));
        sb.setSpan(new PillSpan(0xFFD92D20, 0xFFFFFFFF), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    private static final class PillSpan extends ReplacementSpan {
        private final int bg, fg;
        private final float density = Resources.getSystem().getDisplayMetrics().density;

        PillSpan(int bg, int fg) { this.bg = bg; this.fg = fg; }

        private float textSize(Paint p) { return p.getTextSize() * 0.85f; }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            float old = paint.getTextSize();
            paint.setTextSize(textSize(paint));
            float w = paint.measureText(text, start, end);
            paint.setTextSize(old);
            return Math.round(w + 14 * density);
        }

        @Override
        public void draw(@NonNull Canvas c, CharSequence text, int start, int end, float x, int top, int y,
                         int bottom, @NonNull Paint paint) {
            float oldSize = paint.getTextSize();
            int oldColor = paint.getColor();
            paint.setTextSize(textSize(paint));
            float tw = paint.measureText(text, start, end);
            float w = tw + 14 * density;
            Paint.FontMetrics fm = paint.getFontMetrics();
            float h = (fm.descent - fm.ascent) + 2 * density;
            float cy = y + (fm.ascent + fm.descent) / 2f;
            RectF r = new RectF(x, cy - h / 2f, x + Math.max(w, h), cy + h / 2f);
            paint.setColor(bg);
            paint.setAntiAlias(true);
            c.drawRoundRect(r, h / 2f, h / 2f, paint);
            paint.setColor(fg);
            c.drawText(text, start, end, r.left + (r.width() - tw) / 2f, y, paint);
            paint.setColor(oldColor);
            paint.setTextSize(oldSize);
        }
    }
}
