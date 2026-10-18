<h1 align="center">Airplane Tracker and Photgrapher </h1>

<p align="center">
  <strong>This project is in progress. I will try to update GitHub more often as I work on this more</strong>
</p>
       

Hello, this is a passion project I have been working on for a few months. It receives ADS-B radio signals from overhead aircraft with an RTL-SDR tuned to 1090 MHz, decodes aircraft positions and flight codes, and uses a G-Mouse GPS fix to calculate pointing angles. It will communicate with an Arduino and CNC HAT to control stepper motors that point the camera.

  ### To set this up, you need:
* [RTL SDR Receiver](https://www.aliexpress.us/item/3256811430165848.html)
* [G-Mouse](https://www.aliexpress.us/item/2251801443543615.html)
* An RTL-SDR V2 with the **FC0013** tuner option for 1090 MHz reception (the FC0012 option does not tune to 1090 MHz)
* Any computer that can run Java; this can be an old pc, Raspberry Pi, etc
* All hardware for the project and 3d models for [example](https://www.thingiverse.com/thing:4316563). I had to modify this to fit my needs, as my camera is not the same size and also has a 300mm zoom, and this model does not have room for a slip ring to pass wires through the bottom

### Receiving aircraft positions

The Java `RTLSDR` subsystem accesses the RTL2832U over USB using usb4java, tunes the FC0013 to 1090 MHz, captures I/Q samples, detects Mode-S frames, and decodes them using the bundled `libadsb` library. It prints aircraft identification and decoded latitude, longitude, and altitude as messages arrive. Recent aircraft positions are available through `RTLSDR.getPlanes()`.

The project includes usb4java's Windows x64 native binding. The RTL-SDR must be installed with a WinUSB-compatible driver and must not be in use by another application. Start the Java program and type `quit` to stop receiving.
