package src.subsystems; 

import java.io.BufferedReader; 
import java.io.InputStreamReader; 
import java.net.Socket; 
import java.util.ArrayList; 
import java.util.HashMap; 
import java.util.List; 
import java.util.Map;

import src.model.Plane;

import java.io.IOException; 
import java.nio.file.Files; 
import java.nio.file.Path; 
import java.io.File; 

public class RTLSDR { 
    private String host = "127.0.0.1"; 
    private int port = 30003; 
    private boolean running = false; 
    private Process dump1090Process; 
    private final Map<String, Plane> planeMap = new HashMap<>(); 

    public RTLSDR(double lat, double lon) { 
        String cfgFile = "dump1090/dump1090.cfg";
        try { 
            Path path = Path.of(cfgFile); 
            String content = Files.readString(path); 
            content = content.replaceAll( 
                "(?m)^homepos\\s*=.*$", 
                "homepos = " + lat + "," + lon 
            ); 
            Files.writeString(path, content); 
            System.out.println("Updated homepos in dump1090.cfg to: " + lat + ", " + lon);
        } catch (Exception e) { 
            e.printStackTrace(); 
        } 

        String dump1090Exe = "dump1090.exe";
        try { 
        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "start", "\"dump1090\"", dump1090Exe, "--net"); 
            pb.directory(new File("dump1090"));
            System.out.println("process built");
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            
            dump1090Process = pb.start(); 
        } catch (IOException e) { 
            e.printStackTrace(); 
        } 
    } 

    public void stop() { 
        if (dump1090Process != null && dump1090Process.isAlive()) { 
            dump1090Process.destroy(); 
        } 
        try{
            System.out.println("Stopping dump1090.exe via taskkill...");
            new ProcessBuilder("taskkill", "/F", "/IM", "dump1090.exe", "/T").start();
            System.out.println("dump1090 process stopped."); 
        }catch (IOException e) {
            System.err.println("Failed to kill dump1090.exe process: " + e.getMessage());
        }
    }
}
