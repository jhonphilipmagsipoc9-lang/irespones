package com.rescuelink.app.utils;

import android.text.TextUtils;

public final class Validator {
    private Validator() { }

    public static boolean isBlank(String s) { return s == null || TextUtils.isEmpty(s.trim()); }

    public static boolean isValidAge(String s) {
        try {
            int age = Integer.parseInt(s.trim());
            return age >= 5 && age <= 120;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isValidCount(String s) {
        try {
            int n = Integer.parseInt(s.trim());
            return n >= 1 && n <= 999;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Mobile number: 11 digits starting with 09. */
    /** Station numbers may be landlines or short codes, so only digits, +, -, spaces and brackets are required. */
    public static boolean isValidPhoneLoose(String s) { return s != null && s.trim().matches("[0-9+()\\- ]{3,20}"); }
    public static boolean isValidContact(String s) { return s != null && s.trim().matches("09\\d{9}"); }

    /** Staff (responder / admin) sign-in number: digits only, 4 to 10, so it can never clash with an 11-digit mobile number. */
    public static boolean isValidStaffNumber(String s) { return s != null && s.trim().matches("\\d{4,10}"); }

    /** Turns +639171234567, 639171234567 or 0917 123 4567 into 09171234567. Anything else is returned trimmed. */
    public static String normalizeNumber(String s) {
        if (s == null) return "";
        String t = s.trim().replaceAll("[\\s\\-()]", "");
        if (t.matches("\\+639\\d{9}")) return "0" + t.substring(3);
        if (t.matches("639\\d{9}")) return "0" + t.substring(2);
        return t;
    }
}
