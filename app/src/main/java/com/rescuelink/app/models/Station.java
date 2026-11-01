package com.rescuelink.app.models;

/** A response station, evacuation centre or relief point shown on the map and in lists. */
public class Station {
    private String name;
    private String category;
    private String address;
    private double lat;
    private double lon;
    private boolean available;
    private String phone;

    public Station(String name, String category, String address,
                   double lat, double lon, boolean available, String phone) {
        this.name = name;
        this.category = category;
        this.address = address;
        this.lat = lat;
        this.lon = lon;
        this.available = available;
        this.phone = phone;
    }

    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getAddress() { return address; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public String getPhone() { return phone; }

    public void setLocation(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    public void setDetails(String name, String category, String address, String phone) {
        this.name = name;
        this.category = category;
        this.address = address;
        this.phone = phone;
    }
}
