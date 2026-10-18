package src.model;

import src.subsystems.Gps;
<<<<<<< HEAD
//this SHOULD work, but i have not tested it yet due to no planes flying overhead when i made it... it uses the haversine formula so the math is right but it may be implemented wrong.
=======

>>>>>>> 82629b2ac5db432443b1889ccd459742a647617e
public class DegreeResolver {
    private double latitude;
    private double longitude;
    private double altitude;
    
    public DegreeResolver(Gps gps) {
        this.latitude = gps.getLatitude();
        this.longitude = gps.getLongitude();
        this.altitude = gps.getAltitude();
    }
    public double getYaw(Plane plane) {
        double lat1 = Math.toRadians(this.latitude);
        double lat2 = Math.toRadians(plane.getLatitude());
        double dLon = Math.toRadians(plane.getLongitude() - this.longitude);
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        double yaw = Math.toDegrees(Math.atan2(y, x));
        return (yaw + 360) % 360;
    }
    public double getPitch(Plane plane) {
        double distance = getDistance(plane);
        double pitch = Math.toDegrees(Math.atan2(plane.getAltitude() - this.altitude, distance));
        return pitch;
    }
    private double getDistance(Plane plane) {
        double dLat = Math.toRadians(plane.getLatitude() - this.latitude);
        double dLon = Math.toRadians(plane.getLongitude() - this.longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(this.latitude)) * Math.cos(Math.toRadians(plane.getLatitude())) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371 * 2 * Math.asin(Math.sqrt(a));
    }
}
