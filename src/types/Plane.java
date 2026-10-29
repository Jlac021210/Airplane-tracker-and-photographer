package src.types;
import src.types.Coordinate;
import java.util.ArrayList;

public class Plane {
    private String icaOaci;
    private String callsign;
    private Coordinate coordinate;
    private long lastSeen;
    private double speedMPS;
    private double climbRate;
    private double heading;

    public Plane(String icaOaci, String callsign, double latitude, double longitude, double altitude, double speedMPS, double climbRate, double heading) {
        updateTimestamp();
        this.icaOaci = icaOaci;
        this.callsign = callsign;
        this.speedMPS = speedMPS;
        this.climbRate = climbRate;
        this.heading = heading;
        this.coordinate = new Coordinate(latitude, longitude, altitude);
    }
    public String getIcaOaci() { return icaOaci; }
    public String getCallsign() { return callsign; }
    public double getLatitude() { return this.coordinate.getLatitude(); }
    public double getLongitude() { return this.coordinate.getLongitude(); }
    public double getAltitude() { return this.coordinate.getAltitude(); }
    public Coordinate getCoordinate() { return coordinate; }
    public long getLastSeen(){ return lastSeen; }
    public double getSpeedMPS() { return speedMPS; }
    public double getClimbRate() { return climbRate; }
    public double getHeading() { return heading; }
    public void update(double latitude, double longitude, double altitude) {
        updateTimestamp();
        this.coordinate.setLatitude(latitude);
        this.coordinate.setLongitude(longitude);
        this.coordinate.setAltitude(altitude);
    }
    private void updateTimestamp(){
        this.lastSeen = System.currentTimeMillis();
    }
    public Coordinate predictPosition(int seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("Can't be negative");
        }
        double bearing = Math.toRadians(heading);
        double distance = speedMPS * seconds;
        double northMeters = distance * Math.cos(bearing);
        double eastMeters = distance * Math.sin(bearing);

        double nextLat = getLatitude() + northMeters / 111320.0;
        double nextLon = getLongitude() + eastMeters / (111320.0 * Math.cos(Math.toRadians(getLatitude())));
        double nextAltitude = getAltitude() + climbRate * seconds;
        return new Coordinate(nextLat, nextLon, nextAltitude);
    }
    
    //add cache map to store planes based on call sign update it using rtl sdr but use google guava to remove planes after 5 seconds without an update

    //add find closest plane try to also find the one that has been updated reecently 
    
}
