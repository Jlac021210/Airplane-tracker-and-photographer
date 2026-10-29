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

    public double getYaw(int inXSeconds) {//will replace so it will predict pos where the plane is as radio signals are delayed
        requireGps();
        double lat1 = Math.toRadians(gps.getLatitude());
        double lat2 = Math.toRadians(this.latitude);
        double dLon = Math.toRadians(this.longitude - gps.getLongitude());
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        double yaw = Math.toDegrees(Math.atan2(y, x));
        return (yaw + 360) % 360;
    }
    public double getPitch(int inXseconds) {//will replace so it will predict pos where the plane is as radio signals are delayed
        requireGps();
        double distance = getDistance(inXseconds);
        double pitch = Math.toDegrees(Math.atan2(this.altitude - gps.getAltitude(), distance));
        return pitch;
    }
    private double getDistance(int inXSeconds) {//will replace so it will predict pos where the plane is as radio signals are delayed
        requireGps();
        double northMeters = (this.latitude - gps.getLatitude()) * 111320.0;
        double eastMeters = (this.longitude - gps.getLongitude())
                * 111320.0 * Math.cos(Math.toRadians(gps.getLatitude()));
        return Math.hypot(northMeters, eastMeters);
    }
    private static void requireGps() {
        if (gps == null) {
            throw new IllegalStateException("GPS instance must be set");
        }
    }
}
