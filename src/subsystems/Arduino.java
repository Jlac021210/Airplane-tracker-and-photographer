package src.subsystems;
import src.model.*;

import com.fazecast.jSerialComm.SerialPort;
//DO NOT USE THIS. The port is incorrect and will fail instantly.
public class Arduino {
    private DegreeResolver degreeResolver;
    public Arduino(DegreeResolver degreeResolver) throws Exception {
        this.degreeResolver = degreeResolver;
        SerialPort port = SerialPort.getCommPort("COM5");//placeholder
        port.setBaudRate(9600);
        
        if (!port.openPort()) {
            throw new Exception("Failed to open COM5");//placeholder
        }
    }
    public void sendDegrees(Plane plane) throws Exception {
        SerialPort port = SerialPort.getCommPort("COM5");//placeholder
        if (!port.isOpen()) {
            throw new Exception("COM5 is not open");//placeholder
        }
        port.getOutputStream().write(createDataPackage(plane).getBytes());
        port.getOutputStream().flush();
    }
    public String createDataPackage(Plane plane) {
        double yaw = degreeResolver.getYaw(plane);
        double pitch = degreeResolver.getPitch(plane);
        return pitch +" " + yaw;
    }
}
