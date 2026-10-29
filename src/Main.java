package src;

import java.util.Scanner;
import src.types.Coordinate;
import src.subsystems.*;

public class Main { 
    public static void main(String[] args) throws Exception { 
        Gps gps = new Gps(); 
        Coordinate.setGps(gps);
        //Arduino arduino = new Arduino();//to be implemented
        //Camera camera = new Camera();//to be implemented
        System.out.println("GPS fix acquired: " + gps.getLatitude() + ", " + gps.getLongitude() + ", Altitude: " + gps.getAltitude() + " meters");
        RTLSDR rtl = new RTLSDR(gps.getLatitude(), gps.getLongitude());
        Scanner scanner = new Scanner(System.in);
        System.out.println("Type 'quit' to exit");

        while (true) {
            String input = scanner.nextLine().trim();
            
            if (input.equalsIgnoreCase("quit")) {
                rtl.stop(); 
                break;      
            }
        }
        scanner.close();
        System.out.println("Program exited safely.");
    } 
}