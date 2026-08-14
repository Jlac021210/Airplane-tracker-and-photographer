package src.model;

public class Plane {
    private String icaOaci;
    private String callsign;
    private double latitude;
    private double longitude;
    private double altitude;

    public Plane(String icaOaci, String callsign, double latitude, double longitude, double altitude) {
        this.icaOaci = icaOaci;
        this.callsign = callsign;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
    }
    public String getIcaOaci() {
        return icaOaci;
    }
    public String getCallsign() {
        return callsign;
    }
    public double getLatitude() {
        return latitude;
    }
    public double getLongitude() {
        return longitude;
    }
    public double getAltitude() {
        return altitude;
    }
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }
}
