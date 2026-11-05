package com.rescuelink.app.utils;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import com.rescuelink.app.models.Station;

import java.util.Locale;

/**
 * "Starts with" search for stations: typing "b" finds every station with a word beginning with B, and the
 * matching letters are highlighted in the result.
 */
public final class SearchUi {
    private SearchUi() { }

    public static String clean(String raw) { return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT); }

    /** Index where a word of {@code text} begins with {@code q}, or -1. The first word is tried first. */
    public static int wordStart(String text, String q) {
        if (text == null || q == null || q.isEmpty()) return -1;
        for (int k = 0; k + q.length() <= text.length(); k++) {
            boolean boundary = k == 0 || !Character.isLetterOrDigit(text.charAt(k - 1));
            if (boundary && text.regionMatches(true, k, q, 0, q.length())) return k;
        }
        return -1;
    }

    /** Text with the matched start of a word shown bold on a highlighted background. */
    public static CharSequence highlight(String text, String q) {
        String safe = text == null ? "" : text;
        String c = clean(q);
        int at = wordStart(safe, c);
        if (at < 0) return safe;
        SpannableString sp = new SpannableString(safe);
        int end = at + c.length();
        sp.setSpan(new BackgroundColorSpan(0xFFFFE9A8), at, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sp.setSpan(new ForegroundColorSpan(0xFF0A1F44), at, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sp.setSpan(new StyleSpan(Typeface.BOLD), at, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sp;
    }

    /**
     * How well a station matches the typed letters: 0 = its name starts with them, 1 = a later word in the name
     * does, 2 = its type does, 3 = a word in its address does, -1 = no match. Empty query matches nothing.
     */
    public static int rank(Station s, String q) {
        String c = clean(q);
        if (c.isEmpty() || s == null) return -1;
        if (s.getName().toLowerCase(Locale.ROOT).startsWith(c)) return 0;
        if (wordStart(s.getName(), c) >= 0) return 1;
        if (wordStart(s.getCategory(), c) >= 0) return 2;
        if (wordStart(s.getAddress(), c) >= 0) return 3;
        return -1;
    }
}
