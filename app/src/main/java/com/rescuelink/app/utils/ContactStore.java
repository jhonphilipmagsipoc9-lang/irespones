package com.rescuelink.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.rescuelink.app.models.EmergencyContact;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Personal emergency contacts, saved on the device per user. */
public final class ContactStore {
    private static final String PREFS = "rescuelink_contacts";

    private ContactStore() { }

    private static String key() { return "contacts_" + SessionManager.username(); }

    public static List<EmergencyContact> load(Context ctx) {
        List<EmergencyContact> list = new ArrayList<>();
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray(p.getString(key(), "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new EmergencyContact(o.getString("name"), o.getString("number"),
                        o.optString("relation", "")));
            }
        } catch (Exception ignored) { }
        return list;
    }

    private static void save(Context ctx, List<EmergencyContact> list) {
        JSONArray arr = new JSONArray();
        try {
            for (EmergencyContact c : list) {
                JSONObject o = new JSONObject();
                o.put("name", c.getName());
                o.put("number", c.getNumber());
                o.put("relation", c.getRelation());
                arr.put(o);
            }
        } catch (Exception ignored) { }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key(), arr.toString()).apply();
    }

    public static void add(Context ctx, EmergencyContact c) {
        List<EmergencyContact> list = load(ctx);
        list.add(c);
        save(ctx, list);
    }

    public static void remove(Context ctx, int index) {
        List<EmergencyContact> list = load(ctx);
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            save(ctx, list);
        }
    }
}
