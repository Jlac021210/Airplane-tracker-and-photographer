<h1 align="center">Airplane Tracker and Photographer</h1>

<p align="center">
  <strong>This project is in progress. I will try to update GitHub more often as I work on it.</strong>
</p>

This project is written in Java and uses [dump1090](https://github.com/gvanem/Dump1090) by gvanem to decode aircraft signals.
       

Hello, this is a passion project I have been working on for a few months. It receives ADS-B radio signals from overhead aircraft with an RTL-SDR tuned to 1090 MHz, decodes aircraft positions and flight codes, and uses a G-Mouse GPS fix to calculate pointing angles. It will communicate with an Arduino and CNC HAT to control stepper motors that point the camera.

### To set this up, you need:

* An [RTL-SDR V2 receiver](https://www.aliexpress.us/item/3256811430165848.html) with the **FC0013** tuner option for 1090 MHz reception (the FC0012 option does not tune to 1090 MHz)
* [G-Mouse](https://www.aliexpress.us/item/2251801443543615.html)
* A Windows computer with Java 11 or newer for the current setup
* All hardware for the project and 3d models for [example](https://www.thingiverse.com/thing:4316563). I had to modify this to fit my needs, as my camera is not the same size and also has a 300mm zoom, and this model does not have room for a slip ring to pass wires through the bottom

### Receiving aircraft positions

Java starts the bundled `dump1090` decoder after getting a GPS fix, then prints its aircraft JSON once a second. Camera and motor control are still in progress.

Install a WinUSB-compatible driver for the RTL-SDR and close any other apps using it. Set your GPS port in `src/subsystems/Gps.java` (mine is `COM6`), then run `src.Main` from the project root or press F5 in VS Code. Type `quit` to stop. Decoder output goes to `dump1090/java-process.log`.
<p align="center">
  <strong>GPT-6.1 Sol assisted in writing RTLSDR.java due to many libraries that were needed being new to me.</strong>
</p>
