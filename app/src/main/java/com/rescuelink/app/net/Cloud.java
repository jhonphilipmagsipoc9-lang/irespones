package com.rescuelink.app.net;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tiny client for a Firebase Realtime Database over its REST interface (no SDK, no google-services.json).
 * Every method blocks, so call them from {@link #run(Runnable)} or another background thread.
 * Paths look like "directory/200001"; the ".json" suffix is added here.
 */
public final class Cloud {
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    /** Calls get their own threads, so slow background syncing can never hold up a call's connection messages. */
    private static final ExecutorService CALL_IO = Executors.newFixedThreadPool(4);

    private Cloud() { }

    /** Runs network work off the main thread. */
    public static void run(Runnable r) {
        try { IO.execute(r); } catch (RuntimeException ignored) { }
    }

    /** Runs a call's network work on the call-only threads. */
    public static void runCall(Runnable r) {
        try { CALL_IO.execute(r); } catch (RuntimeException ignored) { }
    }

    /** Keys may not contain . $ # [ ] / so anything else odd is replaced. */
    public static String key(String s) {
        if (s == null) return "_";
        String k = s.trim().replaceAll("[^A-Za-z0-9_\\-]", "_");
        return k.isEmpty() ? "_" : k;
    }

    /** GET a node. Returns the JSON text ("null" when the node is empty) or null when the request failed. */
    public static String get(String base, String path) {
        return request(base, "GET", path, null, false);
    }

    /** PUT (replace) a node with raw JSON text. */
    public static boolean put(String base, String path, String json) {
        return request(base, "PUT", path, json, true) != null;
    }

    /** POST (add under a new unique key) raw JSON text. */
    public static boolean post(String base, String path, String json) {
        return request(base, "POST", path, json, true) != null;
    }

    public static boolean delete(String base, String path) {
        return request(base, "DELETE", path, null, true) != null;
    }

    private static String request(String base, String method, String path, String body, boolean silent) {
        if (base == null || base.isEmpty()) return null;
        HttpURLConnection c = null;
        try {
            URL url = new URL(base + "/" + path + ".json" + (silent ? "?print=silent" : ""));
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(9000);
            c.setRequestMethod(method);
            c.setRequestProperty("Accept", "application/json");
            if (body != null) {
                byte[] bytes = body.getBytes("UTF-8");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
            }
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) return null;
            if (code == 204) return "";
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
                return buf.toString("UTF-8");
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
