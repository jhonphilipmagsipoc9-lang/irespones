package com.rescuelink.app.utils;

import android.app.Activity;
import android.os.CancellationSignal;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.rescuelink.app.R;
import com.rescuelink.app.net.Net;

import org.json.JSONObject;

/**
 * "Continue with Google": the standard Google account chooser, like most apps. Google hands the app a signed
 * ID token that names the person (a stable id, their email and name). No password is ever typed or stored.
 * Needs the OAuth client id in strings.xml (google_web_client_id); see GOOGLE_LOGIN_SETUP.md.
 */
public final class GoogleAuth {
    private GoogleAuth() { }

    public static final class Profile {
        /** Google's permanent id for the person. Never changes, even if they change their email. */
        public String sub = "";
        public String email = "";
        public String name = "";
    }

    public interface Callback {
        void onSuccess(Profile p);
        /** {@code message} is empty when the person simply closed the chooser. */
        void onError(String message);
    }

    /** The sign-in number a Google account gets inside the app. Letters and digits only, so it is safe everywhere. */
    public static String usernameFor(String sub) { return "g" + sub; }

    public static boolean configured(Activity a) {
        String id = a.getString(R.string.google_web_client_id).trim();
        return id.endsWith(".apps.googleusercontent.com") && !id.startsWith("YOUR_");
    }

    public static void signIn(final Activity a, final Callback cb) {
        if (!configured(a)) { cb.onError("not_configured"); return; }
        if (!Net.hasInternet(a)) { cb.onError("Google sign-in needs internet. Turn on Wi-Fi or mobile data and try again."); return; }
        final String clientId = a.getString(R.string.google_web_client_id).trim();
        try {
            CredentialManager manager = CredentialManager.create(a);
            GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(clientId).build();
            GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(option).build();
            manager.getCredentialAsync(a, request, new CancellationSignal(), ContextCompat.getMainExecutor(a),
                    new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override public void onResult(GetCredentialResponse result) {
                            if (a.isFinishing() || a.isDestroyed()) return;
                            handle(result.getCredential(), clientId, cb);
                        }

                        @Override public void onError(@NonNull GetCredentialException e) {
                            if (a.isFinishing() || a.isDestroyed()) return;
                            if (e instanceof GetCredentialCancellationException) cb.onError("");
                            else if (e instanceof NoCredentialException) cb.onError("No Google account was found on this phone. Add one in the phone's Settings, then try again.");
                            else cb.onError("Google sign-in did not work. Check that the app's Google setup is finished, then try again.");
                        }
                    });
        } catch (Exception e) {
            cb.onError("Google sign-in is not available on this phone.");
        }
    }

    private static void handle(Credential credential, String clientId, Callback cb) {
        try {
            if (!(credential instanceof CustomCredential)
                    || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                cb.onError("Google sign-in did not work. Try again.");
                return;
            }
            GoogleIdTokenCredential g = GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData());
            Profile p = readToken(g.getIdToken(), clientId);
            if (p == null) { cb.onError("Google sign-in could not be verified. Try again."); return; }
            if (p.email.isEmpty()) p.email = g.getId() == null ? "" : g.getId();
            if (p.name.isEmpty() && g.getDisplayName() != null) p.name = g.getDisplayName();
            cb.onSuccess(p);
        } catch (Exception e) {
            cb.onError("Google sign-in did not work. Try again.");
        }
    }

    /**
     * Reads the person out of the ID token and checks it was issued by Google for this app and is not expired.
     * The token comes straight from Google Play services on this phone. Accounts live on this phone too, so the
     * signature is not re-checked here; if accounts ever move to a server, verify the token there.
     */
    static Profile readToken(String jwt, String clientId) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return null;
            byte[] raw = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            JSONObject o = new JSONObject(new String(raw, "UTF-8"));
            String iss = o.optString("iss", "");
            if (!"accounts.google.com".equals(iss) && !"https://accounts.google.com".equals(iss)) return null;
            if (!clientId.equals(o.optString("aud", ""))) return null;
            if (o.optLong("exp", 0L) * 1000L < System.currentTimeMillis()) return null;
            String sub = o.optString("sub", "");
            if (sub.isEmpty() || !sub.matches("[0-9A-Za-z_-]{1,64}")) return null;
            Profile p = new Profile();
            p.sub = sub;
            p.email = o.optString("email", "").toLowerCase(java.util.Locale.ROOT);
            p.name = o.optString("name", "");
            return p;
        } catch (Exception e) {
            return null;
        }
    }
}
