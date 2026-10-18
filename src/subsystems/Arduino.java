package src.subsystems;
import src.model.*;

import com.fazecast.jSerialComm.SerialPort;
//DO NOT USE THIS. The port is incorrect and will fail instantly.
public class Arduino {
    private DegreeResolver degreeResolver;
    public Arduino(DegreeResolver degreeResolver) throws Exception {
        this.degreeResolver = degreeResolver;
        SerialPort port = SerialPort.getCommPort("COM5");
        port.setBaudRate(9600);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 1000, 0);
        
        if (!port.openPort()) {
            throw new Exception("Failed to open COM5");
        }
    }
    public void sendData(String data) throws Exception {
        SerialPort port = SerialPort.getCommPort("COM5");
        if (!port.isOpen()) {
            throw new Exception("COM5 is not open");
        }
        port.getOutputStream().write(data.getBytes());
        port.getOutputStream().flush();
    }
    public String createDataPackage(Plane plane) {
        double yaw = degreeResolver.getYaw(plane);
        double pitch = degreeResolver.getPitch(plane);
        return pitch +" " + yaw;
    }
}
