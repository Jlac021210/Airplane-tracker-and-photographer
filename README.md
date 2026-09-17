Hello, this is a passion project I have been working on for a few months. It uses radio signals transmitted from overhead aircraft to an RTL-SDR tuned to 1090 MHz. It then decodes the data using dump1090.exe to access coordinates, altitude, flight code, etc. It uses this to find the necessary degrees based on the current GPS coordinates, which it gets from a G-Mouse (GPS module). It then communicates via serial to an Arduino, which has a CNC HAT to control stepper motors. These stepper motors point the camera at the necessary location to then take the picture.

  To set this up, you need:
* [RTL SDR Receiver](https://www.aliexpress.us/item/3256811430165848.html)
* [G-Mouse](https://www.aliexpress.us/item/2251801443543615.html)
* [Dump1090](https://github.com/antirez/dump1090) installed, and replacing the existing dump1090 folder
* Any computer that can run Java; this can be an old pc, Raspberry Pi, etc
* All hardware for the project and 3d models for [example](https://www.thingiverse.com/thing:4316563). I had to modify this to fit my needs, as my camera is not the same size and also has a 300mm zoom, and this model does not have room for a slip ring to pass wires through the bottom
