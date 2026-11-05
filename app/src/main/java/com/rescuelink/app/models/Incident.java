package com.rescuelink.app.models;

import java.util.ArrayList;
import java.util.List;

/** An incident report. */
public class Incident {
    private String reference;
    private final String ownerUsername;
    private String category = "";
    private String title = "";
    private String description = "";
    private String location = "";
    private String landmark = "";
    private String severity = "Moderate";
    private int affected = 1;
    private IncidentStatus status = IncidentStatus.DRAFT;
    private long createdAt = System.currentTimeMillis();
    private String assignedStation;
    private String responderName;
    private boolean feedbackGiven;
    private double lat = Double.NaN;
    private double lon = Double.NaN;
    private boolean sos;
    private String photoUri;
    private final List<StatusEvent> history = new ArrayList<>();

    public Incident(String reference, String ownerUsername) {
        this.reference = reference;
        this.ownerUsername = ownerUsername;
    }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getOwnerUsername() { return ownerUsername; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getLandmark() { return landmark; }
    public void setLandmark(String landmark) { this.landmark = landmark; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public int getAffected() { return affected; }
    public void setAffected(int affected) { this.affected = affected; }
    public IncidentStatus getStatus() { return status; }
    public void setStatus(IncidentStatus status) { this.status = status; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public String getAssignedStation() { return assignedStation; }
    public void setAssignedStation(String assignedStation) { this.assignedStation = assignedStation; }
    public String getResponderName() { return responderName; }
    public void setResponderName(String responderName) { this.responderName = responderName; }
    public boolean isFeedbackGiven() { return feedbackGiven; }
    public void setFeedbackGiven(boolean feedbackGiven) { this.feedbackGiven = feedbackGiven; }
    public boolean hasPosition() { return !Double.isNaN(lat) && !Double.isNaN(lon); }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public void setPosition(double lat, double lon) { this.lat = lat; this.lon = lon; }
    public void clearPosition() { lat = Double.NaN; lon = Double.NaN; }
    public boolean isSos() { return sos; }
    public void setSos(boolean sos) { this.sos = sos; }
    public String getPhotoUri() { return photoUri; }
    public void setPhotoUri(String photoUri) { this.photoUri = photoUri; }
    public boolean isActive() {
        return status != IncidentStatus.DRAFT && status != IncidentStatus.RESOLVED
                && status != IncidentStatus.CANCELLED;
    }
    public boolean isCritical() { return "Critical".equals(severity) || "High".equals(severity); }
    public List<StatusEvent> getHistory() { return history; }
    public void addEvent(StatusEvent e) { history.add(e); }

    public StatusEvent findEvent(IncidentStatus s) {
        for (StatusEvent e : history) {
            if (e.getStatus() == s) return e;
        }
        return null;
    }

    public boolean isCancellable() {
        return status == IncidentStatus.SUBMITTED || status == IncidentStatus.UNDER_REVIEW;
    }

    public String displayTitle() {
        return title == null || title.trim().isEmpty() ? "Untitled draft" : title;
    }
}
