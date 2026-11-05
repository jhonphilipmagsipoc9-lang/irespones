package com.rescuelink.app.utils;

import android.content.Context;
import android.os.Build;
import android.telephony.SmsManager;

import com.rescuelink.app.net.Net;

import java.security.SecureRandom;

/**
 * One-time codes for verifying a citizen's mobile number.
 * The code is kept only in memory, expires after 5 minutes and allows 5 wrong tries.
 *
 * There is no server in this app, so the code is sent as an SMS from this phone's own SIM.
 * When that is not possible (no SIM, or the SMS permission was refused) and
 * {@link #SHOW_CODE_WHEN_SMS_UNAVAILABLE} is true, the code is shown on screen so the flow can still be tested.
 * Turn that flag off for a real release, and move sending to an SMS gateway on a server.
 */
public final class OtpManager {
    public static final boolean SHOW_CODE_WHEN_SMS_UNAVAILABLE = true;
    public static final long VALID_MS = 5 * 60 * 1000L;
    public static final long RESEND_MS = 30 * 1000L;
    private static final int MAX_TRIES = 5;

    public enum Sent { SMS, ON_SCREEN, FAILED }

    private static final SecureRandom RNG = new SecureRandom();
    private static String number = "";
    private static String code = "";
    private static long issuedAt;
    private static int tries;

    private OtpManager() { }

    /** Makes a new code for this number and sends it. {@code shownCode[0]} is filled only for the on-screen fallback. */
    public static synchronized Sent issue(Context c, String mobile, String[] shownCode) {
        return issue(c, mobile, shownCode, SHOW_CODE_WHEN_SMS_UNAVAILABLE);
    }

    /**
     * Same as above, but the caller decides whether the code may be shown on screen when no SMS can be sent.
     * Signing in and resetting a password pass false: showing the code there would let anyone who types
     * another person's number get in.
     */
    public static synchronized Sent issue(Context c, String mobile, String[] shownCode, boolean allowOnScreen) {
        number = mobile;
        code = String.format(java.util.Locale.US, "%06d", RNG.nextInt(1000000));
        issuedAt = System.currentTimeMillis();
        tries = 0;
        String msg = "iResponse code: " + code + ". It expires in 5 minutes. Do not share it.";
        if (Net.canUseSim(c) && Net.canSendSms(c)) {
            try {
                SmsManager sm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        ? c.getSystemService(SmsManager.class) : SmsManager.getDefault();
                sm.sendTextMessage(mobile, null, msg, null, null);
                return Sent.SMS;
            } catch (Exception ignored) { }
        }
        if (allowOnScreen) {
            if (shownCode != null && shownCode.length > 0) shownCode[0] = code;
            return Sent.ON_SCREEN;
        }
        clear();
        return Sent.FAILED;
    }

    public enum Result { OK, WRONG, EXPIRED, TOO_MANY }

    public static synchronized Result check(String mobile, String entered) {
        if (code.isEmpty() || !number.equals(mobile)) return Result.EXPIRED;
        if (System.currentTimeMillis() - issuedAt > VALID_MS) { clear(); return Result.EXPIRED; }
        if (tries >= MAX_TRIES) { clear(); return Result.TOO_MANY; }
        if (code.equals(entered == null ? "" : entered.trim())) { clear(); return Result.OK; }
        tries++;
        if (tries >= MAX_TRIES) { clear(); return Result.TOO_MANY; }
        return Result.WRONG;
    }

    public static synchronized void clear() { number = ""; code = ""; tries = 0; }
}
