package src.types;
import src.subsystems.Gps;
public class Coordinate {
    private double latitude;
    private double longitude;
    private double altitude;
    private static Gps gps;//as long as ur gps isnt changing this works


    public Coordinate(double latitude, double longitude, double altitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
    }
    public static void setGps(Gps gpsInstance) { gps = gpsInstance; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getAltitude() { return altitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public void setAltitude(double altitude) { this.altitude = altitude; }  

    public double getYaw(Coordinate coordinate) {
        requireGps();
        double lat1 = Math.toRadians(gps.getLatitude());
        double lat2 = Math.toRadians(coordinate.getLatitude());
        double dLon = Math.toRadians(coordinate.getLongitude() - gps.getLongitude());
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        double yaw = Math.toDegrees(Math.atan2(y, x));
        return (yaw + 360) % 360;
    }
    public double getPitch(Coordinate coordinate) {
        requireGps();
        double distance = getDistance(coordinate);
        double pitch = Math.toDegrees(Math.atan2(this.altitude - gps.getAltitude(), distance));
        return pitch;
    }
    public double getDistance(Coordinate coordinate) {
        requireGps();
        double northMeters = (coordinate.getLatitude() - gps.getLatitude()) * 111320.0;
        double eastMeters = (coordinate.getLongitude() - gps.getLongitude()) * 111320.0 * Math.cos(Math.toRadians(gps.getLatitude()));
        return Math.hypot(northMeters, eastMeters);
    }
    private static void requireGps() {
        if (gps == null) {
            throw new IllegalStateException("GPS instance must be set");
        }
    }
    public static Coordinate getCurrentPosition() {
        requireGps();
        return new Coordinate(gps.getLatitude(), gps.getLongitude(), gps.getAltitude());
    }
}
