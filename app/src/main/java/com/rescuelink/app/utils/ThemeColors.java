package com.rescuelink.app.utils;

import android.content.Context;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;

import com.rescuelink.app.R;

/**
 * Colour lookup that follows the theme colour the person picked in Settings. Brand colours (navy, blue and their
 * pale tints) resolve from the current theme; every other colour comes straight from the resources as before.
 */
public final class ThemeColors {
    private ThemeColors() { }

    @AttrRes
    private static int attrFor(@ColorRes int id) {
        if (id == R.color.navy || id == R.color.primary) return R.attr.rlPrimary;
        if (id == R.color.secondary_blue) return R.attr.rlAccent;
        if (id == R.color.primary_container) return R.attr.rlPrimaryContainer;
        if (id == R.color.secondary_container) return R.attr.rlSecondaryContainer;
        if (id == R.color.pale_blue) return R.attr.rlPale;
        if (id == R.color.hero_start) return R.attr.rlHeroStart;
        if (id == R.color.hero_end) return R.attr.rlHeroEnd;
        return 0;
    }

    /** Drop-in replacement for ContextCompat.getColor that honours the chosen theme colour. */
    @ColorInt
    public static int color(Context c, @ColorRes int id) {
        int attr = attrFor(id);
        if (attr != 0) {
            TypedValue tv = new TypedValue();
            if (c.getTheme().resolveAttribute(attr, tv, true) && tv.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return tv.data;
            }
        }
        return ContextCompat.getColor(c, id);
    }

    /** The colour of a theme attribute (for example R.attr.rlPrimary) in this context. */
    @ColorInt
    public static int attr(Context c, @AttrRes int attr, @ColorInt int fallback) {
        TypedValue tv = new TypedValue();
        if (c.getTheme().resolveAttribute(attr, tv, true) && tv.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) return tv.data;
        return fallback;
    }
}
