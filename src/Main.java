package src;

import java.util.Scanner;
import src.subsystems.*;

public class Main { 
    public static void main(String[] args) throws Exception { 
        Gps gps = new Gps(); 
        System.out.println("GPS fix acquired: " + gps.getLatitude() + ", " + gps.getLongitude() + ", Altitude: " + gps.getAltitude() + " ft");
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