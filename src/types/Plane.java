package src.types;
import src.types.Coordinate;
import java.util.ArrayList;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.time.Duration;
public class Plane {
    private String hex;
    private Coordinate coordinate;
    private double speedMPS;
    private double climbRate;
    private double heading;
    private double timeStamp;
    private static Duration duration = Duration.ofSeconds(5);
    private  static Cache<String, Plane> cache = CacheBuilder.newBuilder().expireAfterWrite(duration).build();

    public Plane(String hex, double latitude, double longitude, double altitude, double speedMPS, double climbRate, double heading) {
        updateTimeStamp();
        this.hex = hex;
        this.speedMPS = speedMPS;
        this.climbRate = climbRate;
        this.heading = heading;
        this.coordinate = new Coordinate(latitude, longitude, altitude);
        cache.put(hex, this);
    }
    private void updateTimeStamp() {
        this.timeStamp = System.currentTimeMillis();
    } 
    public Coordinate getCoordinate() { return coordinate; }
    public void update(double latitude, double longitude, double altitude) {
        updateTimeStamp();
        this.coordinate.setLatitude(latitude);
        this.coordinate.setLongitude(longitude);
        this.coordinate.setAltitude(altitude);
        cache.put(hex, this);
    }
    public Coordinate predictPosition(int seconds) {
        if (seconds < 0) { throw new IllegalArgumentException("Can't be negative"); }
        double time = (System.currentTimeMillis()-this.timeStamp) / 1000 + seconds;
        double bearing = Math.toRadians(heading);
        double distance = speedMPS * time;
        double northMeters = distance * Math.cos(bearing);
        double eastMeters = distance * Math.sin(bearing);

        double nextLat = coordinate.getLatitude() + northMeters / 111320.0;
        double nextLon = coordinate.getLongitude() + eastMeters / (111320.0 * Math.cos(Math.toRadians(coordinate.getLatitude())));
        double nextAltitude = coordinate.getAltitude() + climbRate * time;
        return new Coordinate(nextLat, nextLon, nextAltitude);
    }
    public static Plane getByHex(String hex) {
        return cache.getIfPresent(hex);
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
