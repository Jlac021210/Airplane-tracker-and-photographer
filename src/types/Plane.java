package src.types;
import src.types.Coordinate;
import java.util.ArrayList;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.TimeUnit;

public class Plane {
    private String icaOaci;
    private String callsign;
    private Coordinate coordinate;
    private long lastSeen;
    private double speedMPS;
    private double climbRate;
    private double heading;
    private  static Cache<String, Plane> cache = CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.SECONDS).build();

    public Plane(String icaOaci, String callsign, double latitude, double longitude, double altitude, double speedMPS, double climbRate, double heading) {
        updateTimestamp();
        this.icaOaci = icaOaci;
        this.callsign = callsign;
        this.speedMPS = speedMPS;
        this.climbRate = climbRate;
        this.heading = heading;
        this.coordinate = new Coordinate(latitude, longitude, altitude);
        cache.put(callsign, this);
    }
    public String getIcaOaci() { return icaOaci; }
    public String getCallsign() { return callsign; }
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
        cache.put(callsign, this);
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

        double nextLat = coordinate.getLatitude() + northMeters / 111320.0;
        double nextLon = coordinate.getLongitude() + eastMeters / (111320.0 * Math.cos(Math.toRadians(coordinate.getLatitude())));
        double nextAltitude = coordinate.getAltitude() + climbRate * seconds;
        return new Coordinate(nextLat, nextLon, nextAltitude);
    }
    public static Plane getByCallsign(String callsign) {
        return cache.getIfPresent(callsign);
    }
    public static Plane getClosestPlane() {
        Plane closest = null;
        double minDistance = Double.MAX_VALUE;
        for (Plane plane : cache.asMap().values()) {
            double distance = plane.getCoordinate().getDistance(Coordinate.getCurrentPosition());
            if (distance < minDistance) {
                minDistance = distance;
                closest = plane;
            }
        }
        return closest;
    }
    
}
