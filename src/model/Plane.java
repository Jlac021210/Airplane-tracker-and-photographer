package src.model;


public class Plane {
    private String icaOaci;
    private String callsign;
    private double latitude;
    private double longitude;
    private double altitude;
    private long lastSeen;

    public Plane(String icaOaci, String callsign, double latitude, double longitude, double altitude) {
        updateTimestamp();
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
    public long getLastSeen(){
        return lastSeen;
    }
    public void setLatitude(double latitude) {
        updateTimestamp();
        this.latitude = latitude;
    }
    public void setLongitude(double longitude) {
        updateTimestamp();
        this.longitude = longitude;
    }
    public void setAltitude(double altitude) {
        updateTimestamp();
        this.altitude = altitude;
    }
    private void updateTimestamp(){
        this.lastSeen = System.currentTimeMillis();
    }
    //add cache map to store planes based on call sign update it using rtl sdr but use google guava to remove planes after 5 seconds without an update

    //add find closest plane try to also find the one that has been updated reecently 
    
    //add plane position predictor based on the speed and velocity using old data to predict where it will be
}
