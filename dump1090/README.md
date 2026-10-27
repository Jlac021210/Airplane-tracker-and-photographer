# Bundled Dump1090 for Windows

This directory bundles the native x64 decoder and default Tar1090 web assets from
[gvanem/Dump1090 1.0.1](https://github.com/gvanem/Dump1090/releases/tag/v1.0.1).
Original archive: `Dump1090-for-Windows-1.0.1-x64.zip`.
SHA-256: `d4ebc85c9f55a15026645d44438286cb88ab3fb962d44a8e627e906017e736cf`.

Upstream source: https://github.com/gvanem/Dump1090
The upstream license is included in LICENSE. Notices within the web assets are
preserved. The two airport CSV files are upstream public reference data.

Java copies dump1090.example.cfg to the ignored local dump1090.cfg on first run.
It also creates the ignored web_root-Tar1090/config.js from config.example.js.
Edit those local copies for personal settings. The generated java-receiver.cfg
contains the GPS coordinates and an absolute local path and must stay ignored.
Logs, downloaded aircraft databases, captures and runtime JSON are excluded.
Do not force-add ignored files.
