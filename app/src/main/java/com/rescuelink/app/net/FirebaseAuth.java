package com.rescuelink.app.net;

import android.content.Context;

import com.rescuelink.app.R;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Firebase Authentication over its REST interface (no SDK). Every method blocks: call from {@link Cloud#run}.
 * A sign-in number such as 09171234567 becomes the Firebase email 09171234567@iresponse.app, so people still
 * type only their number. Needs the Web API key in strings.xml (firebase_api_key); see FIREBASE_SETUP.md.
 */
public final class FirebaseAuth {
    private static final String HOST = "https://identitytoolkit.googleapis.com/v1/accounts:";
    private static final String DOMAIN = "@iresponse.app";

    private FirebaseAuth() { }

    public static final class Result {
        public boolean ok;
        /** Firebase error text such as INVALID_LOGIN_CREDENTIALS, EMAIL_EXISTS, or NETWORK. Empty on success. */
        public String error = "";
        public String uid = "";
        public String idToken = "";

        public boolean wrongOrMissing() {
            return error.startsWith("INVALID_LOGIN_CREDENTIALS") || error.startsWith("EMAIL_NOT_FOUND")
                    || error.startsWith("INVALID_PASSWORD");
        }
    }

    public static String apiKey(Context c) { return c.getString(R.string.firebase_api_key).trim(); }

    public static boolean configured(Context c) {
        String k = apiKey(c);
        return k.length() > 20 && !k.startsWith("YOUR_");
    }

    /** Reset-password bumps a counter so the number gets a fresh Firebase account (no old password needed). */
    public static String emailFor(Context c, String username) {
        int gen = c.getApplicationContext().getSharedPreferences("rescuelink_net", Context.MODE_PRIVATE)
                .getInt("fbgen_" + username, 0);
        return username.toLowerCase(java.util.Locale.ROOT) + (gen == 0 ? "" : ".r" + gen) + DOMAIN;
    }

    public static void bumpGeneration(Context c, String username) {
        android.content.SharedPreferences p = c.getApplicationContext().getSharedPreferences("rescuelink_net", Context.MODE_PRIVATE);
        p.edit().putInt("fbgen_" + username, p.getInt("fbgen_" + username, 0) + 1).apply();
    }

    public static Result signIn(Context c, String username, String password) {
        return call(c, "signInWithPassword", body(emailFor(c, username), password));
    }

    public static Result signUp(Context c, String username, String password) {
        return call(c, "signUp", body(emailFor(c, username), password));
    }

    /** Changes the password of the signed-in Firebase account (signs in with the old one first). */
    public static Result changePassword(Context c, String username, String oldPassword, String newPassword) {
        Result in = signIn(c, username, oldPassword);
        if (!in.ok) return in;
        try {
            JSONObject o = new JSONObject();
            o.put("idToken", in.idToken);
            o.put("password", newPassword);
            o.put("returnSecureToken", false);
            Result r = call(c, "update", o.toString());
            if (r.ok) r.uid = in.uid;
            return r;
        } catch (Exception e) {
            Result r = new Result();
            r.error = "ERROR";
            return r;
        }
    }

    /** Signs in, or creates the Firebase account first when this number has none yet (older local accounts). */
    public static Result signInOrCreate(Context c, String username, String password) {
        Result r = signIn(c, username, password);
        if (r.ok || !r.wrongOrMissing()) return r;
        Result up = signUp(c, username, password);
        return up.ok ? up : r;           // EMAIL_EXISTS means the account is there and the password is wrong
    }

    /** Plain words for an error, for a toast or a field error. */
    public static String explain(String err) {
        if (err.equals("NETWORK")) return "No internet. Online sign-in needs Wi-Fi or mobile data.";
        if (err.startsWith("OPERATION_NOT_ALLOWED")) return "Email/Password sign-in is off in Firebase. Turn it on under Authentication > Sign-in method.";
        if (err.startsWith("API_KEY") || err.startsWith("PERMISSION_DENIED") || err.startsWith("CONFIGURATION"))
            return "Firebase is not set up correctly (check the Web API key).";
        if (err.startsWith("TOO_MANY_ATTEMPTS")) return "Sign-in is busy right now. Please try again.";
        if (err.startsWith("USER_DISABLED")) return "This account is disabled.";
        if (err.startsWith("WEAK_PASSWORD")) return "Password is too weak (at least 6 characters).";
        return "Wrong number or password.";
    }

    private static String body(String email, String password) {
        try {
            JSONObject o = new JSONObject();
            o.put("email", email);
            o.put("password", password);
            o.put("returnSecureToken", true);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private static Result call(Context c, String method, String json) {
        Result r = new Result();
        HttpURLConnection con = null;
        try {
            byte[] bytes = json.getBytes("UTF-8");
            con = (HttpURLConnection) new URL(HOST + method + "?key=" + apiKey(c)).openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(12000);
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            con.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = con.getOutputStream()) { out.write(bytes); }
            int code = con.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream();
            String text = "";
            if (in != null) {
                try (InputStream is = in) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] chunk = new byte[4096];
                    int n;
                    while ((n = is.read(chunk)) > 0) buf.write(chunk, 0, n);
                    text = buf.toString("UTF-8");
                }
            }
            JSONObject o = text.isEmpty() ? new JSONObject() : new JSONObject(text);
            if (code >= 200 && code < 300) {
                r.ok = true;
                r.uid = o.optString("localId", "");
                r.idToken = o.optString("idToken", "");
            } else {
                JSONObject e = o.optJSONObject("error");
                r.error = e == null ? "ERROR" : e.optString("message", "ERROR");
            }
        } catch (java.io.IOException e) {
            r.error = "NETWORK";
        } catch (Exception e) {
            r.error = "ERROR";
        } finally {
            if (con != null) con.disconnect();
        }
        return r;
    }
}
