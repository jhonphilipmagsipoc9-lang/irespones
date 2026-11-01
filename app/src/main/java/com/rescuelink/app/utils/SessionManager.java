package com.rescuelink.app.utils;

import com.rescuelink.app.models.User;

/** Holds the signed-in user in memory. */
public final class SessionManager {
    private static User currentUser;

    private SessionManager() { }

    public static User getCurrentUser() { return currentUser; }

    /** Signed in last time and never signed out: pick the saved account back up so the app opens at once. */
    public static void restore(android.content.Context c) {
        if (!com.rescuelink.app.net.Config.hasSession(c)) return;
        User u = com.rescuelink.app.data.DataRepository.findUser(com.rescuelink.app.net.Config.user(c));
        if (u != null && u.isActive()) currentUser = u;
    }
    public static void setCurrentUser(User user) { currentUser = user; }
    public static void clear() {
        currentUser = null;
    }

    /** True when a real, active account is signed in. Otherwise sends the person to Login and closes the screen. */
    public static boolean requireLogin(android.app.Activity a) {
        User u = currentUser;
        boolean ok = u != null && u.isActive()
                && com.rescuelink.app.data.DataRepository.findUser(u.getUsername()) == u;
        if (ok) return true;
        currentUser = null;
        toLogin(a);
        return false;
    }

    /** Like requireLogin, and the signed-in account must also have the given role. */
    public static boolean requireRole(android.app.Activity a, String role) {
        if (!requireLogin(a)) return false;
        if (role.equals(currentUser.getRole())) return true;
        toLogin(a);
        return false;
    }

    private static void toLogin(android.app.Activity a) {
        android.content.Intent i = new android.content.Intent(a, com.rescuelink.app.activities.LoginActivity.class);
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(i);
        a.finish();
    }

    public static String username() { return currentUser == null ? "" : currentUser.getUsername(); }
    public static String role() { return currentUser == null ? User.ROLE_CITIZEN : currentUser.getRole(); }

    public static String firstName() {
        if (currentUser == null) return "there";
        String[] parts = currentUser.getFullName().trim().split("\\s+");
        return parts.length == 0 || parts[0].isEmpty() ? "there" : parts[0];
    }
}
