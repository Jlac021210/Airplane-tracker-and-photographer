package src.subsystems;

import com.fazecast.jSerialComm.SerialPort;
import java.util.Scanner;
import net.sf.marineapi.nmea.parser.SentenceFactory;
import net.sf.marineapi.nmea.sentence.GGASentence;
import net.sf.marineapi.nmea.util.Position;

public class Gps { 
    private final Position position;
    private final double altitudeMeters;

    public Gps() throws Exception {
        SerialPort port = SerialPort.getCommPort("COM6");//physical port mine is plugged into update yours to whatever port its actually in
        port.setBaudRate(9600);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 1000, 0);
        
        if (!port.openPort()) {
            throw new Exception("Failed to open COM6");//same here
        }

        try (Scanner scanner = new Scanner(port.getInputStream())) {
            SentenceFactory factory = SentenceFactory.getInstance();
            Position pos = null;
            double alt = 0.0;
            long timeout = System.currentTimeMillis() + 30000;

            while (System.currentTimeMillis() < timeout) {
                if (!scanner.hasNextLine()) {
                    continue;
                }

                String line = scanner.nextLine();
                try {
                    if (line.contains("GGA")) {//GGA is the part that contains alt long and lat
                        GGASentence gga = (GGASentence) factory.createParser(line);
                        Position position = gga.getPosition();//finds the part of the sentance that has it
                        
                        if (position != null && position.getLatitude() != 0.0 && position.getLongitude() != 0.0) {//updates it
                            pos = position;
                            alt = gga.getAltitude(); 
                            break;//leaves the while loop as it found
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            if (pos == null) {
                throw new Exception("Gps is not working");
            }

            this.position = pos;
            this.altitudeMeters = alt;
        } finally {
            port.closePort();//dont keep it open 
        }
    }

    public double getLatitude() {
        return position.getLatitude();
    }

    public double getLongitude() {
        return position.getLongitude();
    }

    public double getAltitude() {
        return altitudeMeters;
    }
}
