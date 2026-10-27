package src.subsystems;

import com.fazecast.jSerialComm.SerialPort;

public class Camera {

    public Camera() throws Exception {
            SerialPort port = SerialPort.getCommPort("COM5");
        port.setBaudRate(9600);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 1000, 0);
        
        if (!port.openPort()) {
            throw new Exception("Failed to open COM5");
        }
    }
}
