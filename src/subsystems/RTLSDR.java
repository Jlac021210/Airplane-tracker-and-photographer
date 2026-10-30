package src.subsystems;
import src.types.Coordinate;
import src.types.Plane;

import java.util.ArrayList;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
/** Starts dump1090 and prints aircraft JSON once per second. */
public class RTLSDR {
    private final Process process;
    private final Thread receiverThread;
    private String tempHex;
    private Coordinate tempCoordinate;
    private double tempSpeedMPS;
    private double tempClimbRate;
    private double tempHeading;

    public RTLSDR(double lat, double lon) throws IOException {
        Path executable = Path.of(System.getProperty("dump1090.executable","dump1090/dump1090.exe")).toAbsolutePath();
        Path directory = executable.getParent();
        int port = Integer.getInteger("dump1090.port", 8080);

        Path decoderConfig = directory.resolve("dump1090.cfg");
        if (!Files.exists(decoderConfig)) {//checks to see if config exists
            Files.copy(directory.resolve("dump1090.example.cfg"), decoderConfig);
        }
        Path mapConfig = directory.resolve("web_root-Tar1090/config.js");
        if (!Files.exists(mapConfig)) {//checks to see if config exists
            Files.copy(directory.resolve("web_root-Tar1090/config.example.js"), mapConfig);
        }

        Path config = directory.resolve("java-receiver.cfg");
        Files.writeString(config, "include = " + decoderConfig + "\n"+ "location = false\nhomepos = " + lat + "," + lon + "\n"+ "http-url4 = tcp://127.0.0.1:" + port + "\n"+ "http-url6 = tcp://[::1]:" + port + "\nsilent = true\n"); //updates config with current cords from Gps.java
        process = new ProcessBuilder(executable.toString(), "--config", config.toString(), "--net").directory(directory.toFile()).redirectErrorStream(true).redirectOutput(directory.resolve("java-process.log").toFile()).start();//creates new process

        receiverThread = new Thread(() -> receive(port));
        receiverThread.setDaemon(true);
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
        receiverThread.start();
    }

    private void receive(int port) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:" + port + "/data/aircraft.json")).timeout(Duration.ofSeconds(2)).build();//gets json data
        try {
            while (process.isAlive()) {
                try {
                    String json = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
                    parseAircraftJson(json);
                } catch (IOException ignored) {
                    // prbly still starting up
                }
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    private void parseAircraftJson(String json) {
        //add arraylist
        System.out.println(json);
        
    }
    private void createOrUpdatePlane(String hex, double latitude, double longitude, double altitude, double speedMPS, double climbRate, double heading){
        if (Plane.getByHex(hex)!=null){
            
        }
    }
    public void stop() {//shutdown will run when quit is typed can be called to also stop the process
        receiverThread.interrupt();
        process.destroyForcibly();
    }
}
