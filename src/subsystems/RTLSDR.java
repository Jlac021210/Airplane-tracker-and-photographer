package src.subsystems;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.opensky.libadsb.ModeSDecoder;
import org.opensky.libadsb.Position;
import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.exceptions.UnspecifiedFormatError;
import org.opensky.libadsb.msgs.AirbornePositionV0Msg;
import org.opensky.libadsb.msgs.IdentificationMsg;
import org.opensky.libadsb.msgs.ModeSReply;
import org.opensky.libadsb.msgs.SurfacePositionV0Msg;
import org.opensky.libadsb.tools;
import org.usb4java.BufferUtils;
import org.usb4java.ConfigDescriptor;
import org.usb4java.Context;
import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.DeviceHandle;
import org.usb4java.DeviceList;
import org.usb4java.EndpointDescriptor;
import org.usb4java.InterfaceDescriptor;
import org.usb4java.LibUsb;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import src.model.Plane;

public class RTLSDR {
    private static final int USB_VENDOR_ID = 0x0bda;
    private static final int USB_PRODUCT_ID = 0x2832;
    private static final int FC0013_I2C_ADDRESS = 0xc6;
    private static final int FC0013_CHIP_ID = 0xa3;
    private static final int RTL_SAMPLE_RATE = 2_000_000;
    private static final int ADSB_FREQUENCY = 1_090_000_000;
    private static final int USB_INTERFACE = 0;
    private static final int BULK_IN_ENDPOINT = 0x81;
    private static final int USB_READ_SIZE = 16 * 1024;
    private static final byte VENDOR_OUT =
        (byte) (LibUsb.REQUEST_TYPE_VENDOR | LibUsb.ENDPOINT_OUT);

    private final Cache<String, Plane> planeCache = CacheBuilder.newBuilder()
        .expireAfterAccess(Duration.ofSeconds(60))
        .maximumSize(4096)
        .build();
    private final Cache<String, String> callsignCache = CacheBuilder.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(5))
        .maximumSize(4096)
        .build();
    private final Cache<String, Boolean> reportedAircraft = CacheBuilder.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(10))
        .maximumSize(4096)
        .build();
    private final Position receiverPosition;
    private final ModeSDecoder decoder = new ModeSDecoder();
    private final Thread receiverThread;
    private volatile boolean running = true;
    private long rejectedFrameCount;

    public RTLSDR(double lat, double lon) {
        if (!Double.isFinite(lat) || lat < -90 || lat > 90
            || !Double.isFinite(lon) || lon < -180 || lon > 180) {
            throw new IllegalArgumentException("Receiver latitude/longitude must be valid degrees.");
        }
        receiverPosition = new Position(lon, lat, 0.0);
        receiverThread = new Thread(this::receive, "rtl-sdr-adsb-receiver");
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    private void receive() {
        System.out.println("Starting standalone RTL-SDR ADS-B receiver (FC0013, 1090 MHz).");
        try (Rtl2832Device device = Rtl2832Device.open()) {
            device.initialize();
            System.out.printf(Locale.ROOT,
                "RTL-SDR configured: FC0013 tuned to %.3f MHz at %.3f MS/s; listening for ADS-B.%n",
                ADSB_FREQUENCY / 1_000_000.0, RTL_SAMPLE_RATE / 1_000_000.0);
            ModeSFrameDemodulator demodulator = new ModeSFrameDemodulator(this::decodeFrame);
            ByteBuffer samples = BufferUtils.allocateByteBuffer(USB_READ_SIZE);
            IntBuffer transferred = BufferUtils.allocateIntBuffer();

            while (running) {
                samples.clear();
                transferred.clear();
                int result = LibUsb.bulkTransfer(device.handle, (byte) BULK_IN_ENDPOINT,
                    samples, transferred, 1000);
                if (result == LibUsb.ERROR_TIMEOUT) {
                    continue;
                }
                checkUsb(result, "reading RTL-SDR sample data");
                if (transferred.get(0) > 0) {
                    demodulator.accept(samples, transferred.get(0));
                }
            }
        } catch (RuntimeException e) {
            if (running) {
                System.err.println("RTL-SDR receiver stopped: " + e.getMessage());
                e.printStackTrace(System.err);
            }
        } finally {
            running = false;
        }
    }

    private void decodeFrame(byte[] frame) {
        try {
            ModeSReply reply = decoder.decode(frame);
            if (!tools.isZero(reply.getParity()) && !reply.checkParity()) {
                rejectedFrameCount++;
                if (rejectedFrameCount % 100_000 == 0) {
                    System.err.println("Rejected " + rejectedFrameCount
                        + " Mode-S candidates with invalid parity; check antenna and signal quality.");
                }
                return;
            }
            String icao = String.format(Locale.ROOT, "%06X", reply.getTransponderAddress());

            if (reply instanceof IdentificationMsg) {
                String callsign = new String(((IdentificationMsg) reply).getIdentity()).trim();
                if (!callsign.isEmpty()) {
                    String oldCallsign = callsignCache.getIfPresent(icao);
                    callsignCache.put(icao, callsign);
                    Plane previous = planeCache.getIfPresent(icao);
                    if (previous != null) {
                        planeCache.put(icao, new Plane(icao, callsign, previous.getLatitude(),
                            previous.getLongitude(), previous.getAltitude()));
                    }
                    if (!callsign.equals(oldCallsign)) {
                        System.out.printf("Aircraft %s identified as %s%n", icao, callsign);
                    }
                }
            }

            if (!ModeSDecoder.isPosition(reply)) {
                reportDetectionIfNew(icao);
                return;
            }

            Position position;
            double altitude = 0.0;
            if (ModeSDecoder.isAirbornePosition(reply)) {
                AirbornePositionV0Msg airborne = (AirbornePositionV0Msg) reply;
                if (airborne.hasAltitude()) {
                    altitude = airborne.getAltitude();
                }
                position = airborne.getLocalPosition(receiverPosition);
                if (position == null) {
                    position = decoder.decodePosition(System.currentTimeMillis(), airborne, receiverPosition);
                }
            } else {
                SurfacePositionV0Msg surface = (SurfacePositionV0Msg) reply;
                position = surface.getLocalPosition(receiverPosition);
                if (position == null) {
                    position = decoder.decodePosition(System.currentTimeMillis(), surface, receiverPosition);
                }
            }

            Plane previous = planeCache.getIfPresent(icao);
            if (previous != null && altitude == 0.0) {
                altitude = previous.getAltitude();
            }
            String callsign = callsignCache.getIfPresent(icao);

            if (position == null || position.getLatitude() == null || position.getLongitude() == null) {
                reportDetectionIfNew(icao);
                return;
            }

            double latitude = position.getLatitude();
            double longitude = position.getLongitude();
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                return;
            }

            planeCache.put(icao, new Plane(icao, callsign, latitude, longitude, altitude));
            if (previous == null || Math.abs(previous.getLatitude() - latitude) > 0.00001
                || Math.abs(previous.getLongitude() - longitude) > 0.00001) {
                System.out.printf(Locale.ROOT,
                    "Aircraft %s%s: lat=%.5f lon=%.5f alt=%.0f ft%n",
                    icao, callsign == null ? "" : " (" + callsign + ")",
                    latitude, longitude, altitude);
            }
        } catch (BadFormatException | UnspecifiedFormatError e) {
            // A preamble candidate that fails Mode-S validation is normal in noisy RF samples.
        }
    }

    private void reportDetectionIfNew(String icao) {
        if (reportedAircraft.asMap().putIfAbsent(icao, Boolean.TRUE) == null) {
            System.out.println("ADS-B aircraft detected: " + icao + " (waiting for a decodable position)");
        }
    }

    public List<Plane> getPlanes() {
        return new ArrayList<>(planeCache.asMap().values());
    }

    public void stop() {
        running = false;
        receiverThread.interrupt();
        try {
            receiverThread.join(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted while stopping the RTL-SDR receiver.");
        }
    }

    private static void checkUsb(int result, String operation) {
        if (result != LibUsb.SUCCESS) {
            throw new IllegalStateException(operation + " failed: " + LibUsb.strError(result));
        }
    }

    /*
     * RTL2832U register setup and FC0013 tuning are adapted from osmocom rtl-sdr,
     * distributed under GPL-2.0-or-later: https://github.com/osmocom/rtl-sdr
     */
    private static final class Rtl2832Device implements AutoCloseable {
        private final Context context;
        private final DeviceHandle handle;
        private boolean claimed;

        private Rtl2832Device(Context context, DeviceHandle handle) {
            this.context = context;
            this.handle = handle;
        }

        private static Rtl2832Device open() {
            Context context = new Context();
            checkUsb(LibUsb.init(context), "initializing usb4java");
            DeviceList devices = new DeviceList();
            DeviceHandle handle = null;
            boolean claimed = false;
            try {
                long count = LibUsb.getDeviceList(context, devices);
                if (count < 0) {
                    throw new IllegalStateException("Unable to enumerate USB devices: "
                        + LibUsb.strError((int) count));
                }

                for (Device device : devices) {
                    DeviceDescriptor descriptor = new DeviceDescriptor();
                    checkUsb(LibUsb.getDeviceDescriptor(device, descriptor), "reading USB device descriptor");
                    if ((descriptor.idVendor() & 0xffff) == USB_VENDOR_ID
                        && (descriptor.idProduct() & 0xffff) == USB_PRODUCT_ID) {
                        handle = new DeviceHandle();
                        checkUsb(LibUsb.open(device, handle), "opening RTL2832U USB device");
                        break;
                    }
                }

                if (handle == null) {
                    throw new IllegalStateException("RTL2832U device 0BDA:2832 was not found.");
                }

                checkUsb(LibUsb.claimInterface(handle, USB_INTERFACE), "claiming RTL-SDR USB interface");
                claimed = true;
                checkUsb(LibUsb.setInterfaceAltSetting(handle, USB_INTERFACE, 0),
                    "selecting RTL-SDR USB alternate setting");
                verifyBulkEndpoint(devices, handle);
                LibUsb.freeDeviceList(devices, true);

                return new Rtl2832Device(context, handle);
            } catch (RuntimeException e) {
                LibUsb.freeDeviceList(devices, true);
                if (claimed) {
                    LibUsb.releaseInterface(handle, USB_INTERFACE);
                }
                if (handle != null) {
                    LibUsb.close(handle);
                }
                LibUsb.exit(context);
                throw e;
            }
        }

        private static void verifyBulkEndpoint(DeviceList devices, DeviceHandle handle) {
            boolean bulkInputFound = false;
            for (Device device : devices) {
                DeviceDescriptor descriptor = new DeviceDescriptor();
                checkUsb(LibUsb.getDeviceDescriptor(device, descriptor), "reading USB device descriptor");
                if ((descriptor.idVendor() & 0xffff) == USB_VENDOR_ID
                    && (descriptor.idProduct() & 0xffff) == USB_PRODUCT_ID) {
                    ConfigDescriptor config = new ConfigDescriptor();
                    checkUsb(LibUsb.getActiveConfigDescriptor(device, config),
                        "reading RTL-SDR USB configuration");
                    try {
                        for (org.usb4java.Interface usbInterface : config.iface()) {
                            for (InterfaceDescriptor alternate : usbInterface.altsetting()) {
                                for (EndpointDescriptor endpoint : alternate.endpoint()) {
                                    if ((endpoint.bEndpointAddress() & 0xff) == BULK_IN_ENDPOINT
                                        && (endpoint.bmAttributes() & 0x03) == 0x02) {
                                        bulkInputFound = true;
                                    }
                                }
                            }
                        }
                    } finally {
                        LibUsb.freeConfigDescriptor(config);
                    }
                    break;
                }
            }
            if (!bulkInputFound) {
                throw new IllegalStateException("RTL-SDR bulk sample endpoint 0x81 was not found.");
            }
        }

        private void initialize() {
            initializeBaseband();

            writeDemodulator(1, 0x01, 0x18, 1);
            int tunerId = readTunerRegister(0x00);
            if (tunerId != FC0013_CHIP_ID) {
                writeDemodulator(1, 0x01, 0x10, 1);
                throw new IllegalStateException(String.format(Locale.ROOT,
                    "Expected an FC0013 tuner (chip ID 0xA3), read 0x%02X. "
                        + "Check the purchased tuner variant.", tunerId));
            }
            System.out.println("Detected FC0013 tuner.");

            initializeFc0013();
            setFc0013Frequency(ADSB_FREQUENCY);
            writeDemodulator(1, 0x01, 0x10, 1);
            setSampleRate(RTL_SAMPLE_RATE);
            writeRegister(1, 0x2148, 0x1002, 2);
            writeRegister(1, 0x2148, 0x0000, 2);
        }

        private void initializeBaseband() {
            writeRegister(1, 0x2000, 0x09, 1);
            writeRegister(1, 0x2158, 0x0002, 2);
            writeRegister(1, 0x2148, 0x1002, 2);
            writeRegister(2, 0x300b, 0x22, 1);
            writeRegister(2, 0x3000, 0xe8, 1);
            writeDemodulator(1, 0x01, 0x14, 1);
            writeDemodulator(1, 0x01, 0x10, 1);
            writeDemodulator(1, 0x15, 0x00, 1);
            writeDemodulator(1, 0x16, 0x0000, 2);
            for (int register = 0x16; register < 0x1c; register++) {
                writeDemodulator(1, register, 0x00, 1);
            }
            writeDefaultFir();
            writeDemodulator(0, 0x19, 0x05, 1);
            writeDemodulator(1, 0x93, 0xf0, 1);
            writeDemodulator(1, 0x94, 0x0f, 1);
            writeDemodulator(1, 0x11, 0x00, 1);
            writeDemodulator(1, 0x04, 0x00, 1);
            writeDemodulator(0, 0x61, 0x60, 1);
            writeDemodulator(0, 0x06, 0x80, 1);
            writeDemodulator(1, 0xb1, 0x1b, 1);
            writeDemodulator(0, 0x0d, 0x83, 1);
        }

        private void writeDefaultFir() {
            int[] coefficients = {
                -54, -36, -41, -40, -32, -14, 14, 53,
                101, 156, 215, 273, 327, 372, 404, 421
            };
            byte[] packed = new byte[20];
            for (int i = 0; i < 8; i++) {
                packed[i] = (byte) coefficients[i];
            }
            for (int i = 0; i < 8; i += 2) {
                int first = coefficients[8 + i];
                int second = coefficients[8 + i + 1];
                int offset = 8 + i * 3 / 2;
                packed[offset] = (byte) (first >> 4);
                packed[offset + 1] = (byte) ((first << 4) | ((second >> 8) & 0x0f));
                packed[offset + 2] = (byte) second;
            }
            for (int i = 0; i < packed.length; i++) {
                writeDemodulator(1, 0x1c + i, packed[i] & 0xff, 1);
            }
        }

        private void initializeFc0013() {
            int[] registers = {
                0x00, 0x09, 0x16, 0x00, 0x00, 0x17, 0x02, 0x2a,
                0xff, 0x6e, 0xb8, 0x82, 0xfe, 0x01, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x50, 0x01
            };
            for (int i = 1; i < registers.length; i++) {
                writeTunerRegister(i, registers[i]);
            }
        }

        private void setFc0013Frequency(int frequencyHz) {
            int vhfTrack = frequencyHz < 300_000_000 ? 0x04 : 0x1c;
            writeTunerRegister(0x1d, (readTunerRegister(0x1d) & 0xe3) | vhfTrack);

            int filter = readTunerRegister(0x07);
            writeTunerRegister(0x07, filter & 0xef);
            int band = readTunerRegister(0x14);
            writeTunerRegister(0x14, (band & 0x1f) | 0x40);

            int multiplier;
            int divider5;
            int divider6;
            if (frequencyHz < 37_084_000) {
                multiplier = 96;
                divider5 = 0x82;
                divider6 = 0x00;
            } else if (frequencyHz < 55_625_000) {
                multiplier = 64;
                divider5 = 0x02;
                divider6 = 0x02;
            } else if (frequencyHz < 74_167_000) {
                multiplier = 48;
                divider5 = 0x42;
                divider6 = 0x00;
            } else if (frequencyHz < 111_250_000) {
                multiplier = 32;
                divider5 = 0x82;
                divider6 = 0x02;
            } else if (frequencyHz < 148_334_000) {
                multiplier = 24;
                divider5 = 0x22;
                divider6 = 0x00;
            } else if (frequencyHz < 222_500_000) {
                multiplier = 16;
                divider5 = 0x42;
                divider6 = 0x02;
            } else if (frequencyHz < 296_667_000) {
                multiplier = 12;
                divider5 = 0x12;
                divider6 = 0x00;
            } else if (frequencyHz < 445_000_000) {
                multiplier = 8;
                divider5 = 0x22;
                divider6 = 0x02;
            } else if (frequencyHz < 593_334_000) {
                multiplier = 6;
                divider5 = 0x0a;
                divider6 = 0x00;
            } else if (frequencyHz < 950_000_000) {
                multiplier = 4;
                divider5 = 0x12;
                divider6 = 0x02;
            } else {
                multiplier = 2;
                divider5 = 0x0a;
                divider6 = 0x02;
            }

            long vcoFrequency = (long) frequencyHz * multiplier;
            boolean useHighVcoRange = vcoFrequency >= 3_060_000_000L;
            if (useHighVcoRange) {
                divider6 |= 0x08;
            }
            int halfCrystalHz = 14_400_000;
            int xdiv = (int) ((vcoFrequency + halfCrystalHz / 2L) / halfCrystalHz);
            int pm = xdiv / 8;
            int am = xdiv - 8 * pm;
            if (am < 2) {
                am += 8;
                pm--;
            }
            if (pm > 31) {
                writeTunerRegister(0x01, am + 8 * (pm - 31));
                writeTunerRegister(0x02, 31);
            } else {
                writeTunerRegister(0x01, am);
                writeTunerRegister(0x02, pm);
            }
            if (am > 15 || pm < 11) {
                throw new IllegalStateException("FC0013 could not find a valid PLL setting for 1090 MHz.");
            }

            divider6 = (divider6 & 0x3f) | 0x20;
            long remainder = vcoFrequency - (vcoFrequency / halfCrystalHz) * halfCrystalHz;
            long xin = (remainder / 1000L) * 32768L / (halfCrystalHz / 1000L);
            if (xin >= 16384) {
                xin += 32768;
            }
            writeTunerRegister(0x03, (int) (xin >> 8) & 0xff);
            writeTunerRegister(0x04, (int) xin & 0xff);
            writeTunerRegister(0x05, divider5 | 0x07);
            writeTunerRegister(0x06, divider6);

            int clock = readTunerRegister(0x11);
            writeTunerRegister(0x11, multiplier == 64 ? clock | 0x04 : clock & 0xfb);
            writeTunerRegister(0x0e, 0x80);
            writeTunerRegister(0x0e, 0x00);
            writeTunerRegister(0x0e, 0x00);
        }

        private void setSampleRate(int sampleRate) {
            long ratio = (28_800_000L * (1L << 22)) / sampleRate;
            ratio &= 0x0fff_fffcL;
            writeDemodulator(1, 0x9f, (int) (ratio >> 16) & 0xffff, 2);
            writeDemodulator(1, 0xa1, (int) ratio & 0xffff, 2);
            writeDemodulator(1, 0x3f, 0x00, 1);
            writeDemodulator(1, 0x3e, 0x00, 1);
            writeDemodulator(1, 0x01, 0x14, 1);
            writeDemodulator(1, 0x01, 0x10, 1);
        }

        private int readTunerRegister(int register) {
            byte[] address = {(byte) register};
            transferControl(VENDOR_OUT,
                (short) FC0013_I2C_ADDRESS, (short) (6 << 8 | 0x10), address, "selecting FC0013 register");
            ByteBuffer value = BufferUtils.allocateByteBuffer(1);
            int result = LibUsb.controlTransfer(handle,
                (byte) (LibUsb.REQUEST_TYPE_VENDOR | LibUsb.ENDPOINT_IN), (byte) 0,
                (short) FC0013_I2C_ADDRESS, (short) (6 << 8), value, 1000);
            if (result != 1) {
                throw new IllegalStateException("Reading FC0013 register failed: "
                    + (result < 0 ? LibUsb.strError(result) : "short USB read"));
            }
            return value.get(0) & 0xff;
        }

        private void writeTunerRegister(int register, int value) {
            byte[] data = {(byte) register, (byte) value};
            transferControl(VENDOR_OUT,
                (short) FC0013_I2C_ADDRESS, (short) (6 << 8 | 0x10), data, "writing FC0013 register");
        }

        private void writeRegister(int block, int address, int value, int length) {
            byte[] data = registerBytes(value, length);
            transferControl(VENDOR_OUT,
                (short) address, (short) (block << 8 | 0x10), data, "writing RTL2832U register");
        }

        private void writeDemodulator(int page, int register, int value, int length) {
            int address = register << 8 | 0x20;
            int index = 0x10 | page;
            transferControl(VENDOR_OUT,
                (short) address, (short) index, registerBytes(value, length),
                "writing RTL2832U demodulator register");
            ByteBuffer acknowledged = BufferUtils.allocateByteBuffer(1);
            int result = LibUsb.controlTransfer(handle,
                (byte) (LibUsb.REQUEST_TYPE_VENDOR | LibUsb.ENDPOINT_IN), (byte) 0,
                (short) 0x0120, (short) 0x0a, acknowledged, 1000);
            if (result != 1) {
                throw new IllegalStateException("RTL2832U demodulator write acknowledgement failed: "
                    + (result < 0 ? LibUsb.strError(result) : "short USB read"));
            }
        }

        private void transferControl(byte requestType, short value, short index, byte[] data, String operation) {
            ByteBuffer buffer = BufferUtils.allocateByteBuffer(data.length);
            buffer.put(data).flip();
            int result = LibUsb.controlTransfer(handle, requestType, (byte) 0,
                value, index, buffer, 1000);
            if (result != data.length) {
                throw new IllegalStateException(operation + " failed: "
                    + (result < 0 ? LibUsb.strError(result) : "short USB write"));
            }
        }

        private byte[] registerBytes(int value, int length) {
            if (length == 1) {
                return new byte[] {(byte) value};
            }
            return new byte[] {(byte) (value >> 8), (byte) value};
        }

        @Override
        public void close() {
            if (claimed) {
                int result = LibUsb.releaseInterface(handle, USB_INTERFACE);
                if (result != LibUsb.SUCCESS) {
                    System.err.println("Failed to release RTL-SDR USB interface: " + LibUsb.strError(result));
                }
            }
            LibUsb.close(handle);
            LibUsb.exit(context);
        }
    }

    private static final class ModeSFrameDemodulator {
        private static final int PREAMBLE_SAMPLES = 16;
        private static final int SAMPLE_RING_SIZE = 512;
        private static final int SAMPLE_RING_MASK = SAMPLE_RING_SIZE - 1;
        private static final int MODE_S_SHORT_BYTES = 7;
        private static final int MODE_S_LONG_BYTES = 14;

        private final int[] sampleRing = new int[SAMPLE_RING_SIZE];
        private final Consumer<byte[]> frameConsumer;
        private long sampleCount;
        private long candidateStart = -1;
        private long nextSearchSample;
        private int expectedFrameBytes;

        private ModeSFrameDemodulator(Consumer<byte[]> frameConsumer) {
            this.frameConsumer = frameConsumer;
        }

        private void accept(ByteBuffer iqSamples, int byteCount) {
            int evenByteCount = byteCount & ~1;
            for (int i = 0; i < evenByteCount; i += 2) {
                int inPhase = (iqSamples.get(i) & 0xff) - 127;
                int quadrature = (iqSamples.get(i + 1) & 0xff) - 127;
                sampleRing[(int) sampleCount & SAMPLE_RING_MASK] =
                    inPhase * inPhase + quadrature * quadrature;
                sampleCount++;
                processNewestSample();
            }
        }

        private void processNewestSample() {
            long newest = sampleCount - 1;
            if (candidateStart < 0) {
                long start = newest - PREAMBLE_SAMPLES + 1;
                if (start >= nextSearchSample && hasModeSPreamble(start)) {
                    candidateStart = start;
                    expectedFrameBytes = 0;
                }
                return;
            }

            long firstDataSample = candidateStart + PREAMBLE_SAMPLES;
            if (expectedFrameBytes == 0 && newest >= firstDataSample + 9) {
                int downlinkFormat = 0;
                for (int bit = 0; bit < 5; bit++) {
                    if (readBit(firstDataSample + bit * 2L) != 0) {
                        downlinkFormat |= 1 << (4 - bit);
                    }
                }
                expectedFrameBytes = ModeSReply.getExpectedLength((byte) downlinkFormat);
                if (expectedFrameBytes != MODE_S_SHORT_BYTES && expectedFrameBytes != MODE_S_LONG_BYTES) {
                    discardCandidate(candidateStart + 1);
                    return;
                }
            }

            if (expectedFrameBytes == 0
                || newest < firstDataSample + expectedFrameBytes * 16L - 1) {
                return;
            }

            byte[] frame = new byte[expectedFrameBytes];
            for (int bit = 0; bit < expectedFrameBytes * 8; bit++) {
                if (readBit(firstDataSample + bit * 2L) != 0) {
                    frame[bit / 8] |= (byte) (1 << (7 - bit % 8));
                }
            }
            long frameStart = candidateStart;
            candidateStart = -1;
            nextSearchSample = frameStart + 1;
            frameConsumer.accept(frame);
        }

        private int readBit(long firstHalfSample) {
            return sampleAt(firstHalfSample) > sampleAt(firstHalfSample + 1) ? 1 : 0;
        }

        private boolean hasModeSPreamble(long start) {
            int pulse0 = sampleAt(start);
            int pulse1 = sampleAt(start + 2);
            int pulse2 = sampleAt(start + 7);
            int pulse3 = sampleAt(start + 9);
            int pulseTotal = pulse0 + pulse1 + pulse2 + pulse3;
            int minimumPulse = Math.min(Math.min(pulse0, pulse1), Math.min(pulse2, pulse3));
            int maximumQuiet = 0;
            for (int offset = 1; offset < PREAMBLE_SAMPLES; offset++) {
                if (offset != 2 && offset != 7 && offset != 9) {
                    maximumQuiet = Math.max(maximumQuiet, sampleAt(start + offset));
                }
            }
            int quietTotal = 0;
            for (int offset = 0; offset < PREAMBLE_SAMPLES; offset++) {
                if (offset != 0 && offset != 2 && offset != 7 && offset != 9) {
                    quietTotal += sampleAt(start + offset);
                }
            }
            return pulseTotal > 256
                && pulseTotal * 10 > quietTotal * 6
                && minimumPulse * 240 > quietTotal * 23
                && maximumQuiet * 40 < pulseTotal * 9;
        }

        private int sampleAt(long index) {
            return sampleRing[(int) index & SAMPLE_RING_MASK];
        }

        private void discardCandidate(long nextStart) {
            candidateStart = -1;
            nextSearchSample = nextStart;
        }
    }
}
