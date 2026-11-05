package com.rescuelink.app.models;

/** App user (citizen, responder or admin). */
public class User {
    public static final String ROLE_CITIZEN = "Citizen";
    public static final String ROLE_RESPONDER = "Responder";
    public static final String ROLE_ADMIN = "Admin";

    /** The four services. Each has its own admin, who creates and manages that service's responders. */
    public static final String[] SERVICES = {"Police", "Fire", "DRRMO", "Medical"};
    public static final String[] ADMIN_SERVICES = {"Police", "Fire", "DRRMO", "Medical"};

    public static final String DUTY_ON = "On duty";
    public static final String DUTY_BUSY = "Busy";
    public static final String DUTY_OFF = "Off duty";

    private String fullName;
    private int age;
    private String address;
    private final String username;
    /** Salted SHA-256 of the password. The plain password is never kept or saved. */
    private String passwordHash;
    private String salt;
    private long createdAt = System.currentTimeMillis();
    private String contact;
    /** Google account email for people who signed up with Google. Empty otherwise. */
    private String email = "";
    private final String role;
    private boolean active = true;
    private String dutyStatus = DUTY_ON;
    private String bloodType = "";
    private String allergies = "";
    private String conditions = "";
    private String photoUri;
    private String stationName = "";
    /** Police, Fire, DRRMO or Medical for responders and service admins. Empty for citizens and the Command Center. */
    private String service = "";
    /** Private file names (inside the app) of the ID photo and the face scan taken at registration. */
    private String idPhotoFile = "";
    private String facePhotoFile = "";
    private boolean faceVerified;
    private long verifiedAt;
    private double lat = Double.NaN;
    private double lon = Double.NaN;
    /** Not saved: when this phone last heard that the person is online (0 = not online), and whether the account lives on another phone. */
    private long lastSeen;
    private boolean remote;

    public User(String fullName, int age, String address, String username,
                String password, String contact, String role) {
        this.fullName = fullName;
        this.age = age;
        this.address = address;
        this.username = username;
        this.salt = newSalt();
        this.passwordHash = hash(salt, password);
        this.contact = contact;
        this.role = role;
    }

    /** Rebuilds an account that was saved on the device. */
    public static User restore(String fullName, int age, String address, String username,
                               String salt, String passwordHash, String contact, String role) {
        User u = new User(fullName, age, address, username, "", contact, role);
        u.salt = salt;
        u.passwordHash = passwordHash;
        return u;
    }

    private static String newSalt() {
        byte[] b = new byte[16];
        new java.security.SecureRandom().nextBytes(b);
        return toHex(b);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte x : bytes) sb.append(String.format(java.util.Locale.US, "%02x", x));
        return sb.toString();
    }

    private static String hash(String salt, String password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return toHex(md.digest((salt + ":" + password).getBytes("UTF-8")));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** False for an account restored without password data, which nobody can sign in to. */
    public boolean hasPassword() { return passwordHash != null && !passwordHash.isEmpty() && salt != null && !salt.isEmpty(); }
    public String getSalt() { return salt; }
    public String getPasswordHash() { return passwordHash; }
    public void setCreatedAt(long v) { createdAt = v; }

    public String getFullName() { return fullName; }
    public void setFullName(String v) { fullName = v; }
    public int getAge() { return age; }
    public void setAge(int v) { age = v; }
    public String getAddress() { return address; }
    public void setAddress(String v) { address = v; }
    public String getUsername() { return username; }
    public String getContact() { return contact; }
    public String getEmail() { return email == null ? "" : email; }
    public void setEmail(String v) { email = v == null ? "" : v; }
    public void setContact(String v) { contact = v; }
    public String getRole() { return role; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { active = v; }
    /** Responders are always on duty (or Busy while handling an incident). They are never Off duty. */
    public String getDutyStatus() { return ROLE_RESPONDER.equals(role) && DUTY_OFF.equals(dutyStatus) ? DUTY_ON : dutyStatus; }
    public void setDutyStatus(String v) { dutyStatus = ROLE_RESPONDER.equals(role) && DUTY_OFF.equals(v) ? DUTY_ON : v; }
    public String getBloodType() { return bloodType; }
    public void setBloodType(String v) { bloodType = v; }
    public String getAllergies() { return allergies; }
    public void setAllergies(String v) { allergies = v; }
    public String getConditions() { return conditions; }
    public void setConditions(String v) { conditions = v; }
    /** Name of the station this responder belongs to (empty for citizens and the admin). */
    public String getStationName() { return stationName == null ? "" : stationName; }
    public void setStationName(String v) { stationName = v == null ? "" : v; }
    public String getService() { return service == null ? "" : service; }
    public void setService(String v) { service = v == null ? "" : v; }
    /** The all-services admin (the account created on the first start). */
    public boolean isCommandCenter() { return ROLE_ADMIN.equals(role) && getService().isEmpty(); }
    /** An admin who only looks after one service. */
    public boolean isServiceAdmin() { return ROLE_ADMIN.equals(role) && !getService().isEmpty(); }
    public String getIdPhotoFile() { return idPhotoFile == null ? "" : idPhotoFile; }
    public String getFacePhotoFile() { return facePhotoFile == null ? "" : facePhotoFile; }
    public boolean isFaceVerified() { return faceVerified; }
    public long getVerifiedAt() { return verifiedAt; }
    public void setVerification(String idFile, String faceFile, boolean verified, long at) {
        idPhotoFile = idFile == null ? "" : idFile;
        facePhotoFile = faceFile == null ? "" : faceFile;
        faceVerified = verified;
        verifiedAt = at;
    }
    public String getPhotoUri() { return photoUri; }
    public void setPhotoUri(String v) { photoUri = v; }
    public boolean hasPosition() { return !Double.isNaN(lat) && !Double.isNaN(lon); }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public void setPosition(double lat, double lon) { this.lat = lat; this.lon = lon; }
    /** True when the person is signed in on some phone right now (heard from within the last minute). */
    public boolean isLive() { return lastSeen > 0 && System.currentTimeMillis() - lastSeen < 60000L; }
    public void setLastSeen(long v) { lastSeen = v; }
    /** An account known only from another phone's presence. Nobody can sign in to it here. */
    public boolean isRemote() { return remote; }
    public void setRemote(boolean v) { remote = v; }
    public boolean passwordMatches(String input) { return input != null && passwordHash.equals(hash(salt, input)); }
    /** Only the account owner may change the password, and only with the current one. */
    public boolean changePassword(String current, String next) {
        if (!passwordMatches(current)) return false;
        salt = newSalt();
        passwordHash = hash(salt, next);
        return true;
    }
    /** Used only after the owner proved the mobile number with a one-time code (forgot password). */
    public void resetPassword(String next) {
        salt = newSalt();
        passwordHash = hash(salt, next);
    }
    public long getCreatedAt() { return createdAt; }
}
