# Epson SX130 direct USB printing investigation

## Goal
Implement direct USB printing from Android to the Epson Stylus SX130 without routing jobs through a third-party Android printing app.

## Confirmed repository state
The current Android flow detects a USB device, requests USB permission, selects a document, and invokes Android's Print Framework. This is not a direct printer driver: Android's print service still has to provide a compatible backend.

## Findings from the Epson 201101w source package
- The SX130 PPD identifies the model as `Epson Stylus SX130 Series` and USB ID string `MFG:EPSON;MDL:Epson Stylus SX130`.
- The PPD declares a CUPS raster filter: `application/vnd.cups-raster` -> `epson_inkjet_printer_filter`.
- The PPD references Epson's proprietary core library `libEpson_201101w.so.1.0.0` and model resource `Epson_201101w.1.data`.
- The filter source uses CUPS headers and dynamically loads the Epson core library. The open filter is therefore not a self-contained printer protocol implementation.
- The supplied Epson core libraries are Linux ELF binaries (both i386 and x86-64 variants), not Android ARM/ARM64 libraries. They cannot be loaded directly by a normal Android app.
- The PPD exposes useful model settings such as A4 default paper, color/grayscale modes, plain/photo media choices, and 360/720 dpi modes. These are configuration metadata, not proof of the USB wire protocol.

## Current implementation boundary
USB discovery and permission handling are separate from generating printer-ready data. A successful USB connection does not mean the app can print. The existing Android Print Framework call must not be described as direct USB printing.

## Safe next steps
1. Keep the existing `main` branch unchanged.
2. Determine whether Epson offers a documented Android SDK or supported protocol for this exact model.
3. If no supported SDK exists, evaluate an independently licensed open-source implementation that explicitly supports the SX130. Do not reverse engineer or redistribute Epson's proprietary library.
4. Only after a legitimate protocol/driver path is identified, implement an Android raster pipeline and USB bulk-transfer layer, then test with a real SX130.
5. Keep physical print success as an explicit acceptance test; compilation and USB detection alone are insufficient.

## Acceptance criteria
- The app sends a real print job directly over Android USB Host.
- A plain A4 test page prints correctly in black-and-white.
- A color test page prints correctly.
- USB disconnects, permission denial, and printer errors are reported clearly.
- No NokoPrint or other intermediary Android print app is required.

## Status
Investigation only. No printer protocol has yet been confirmed, and no direct print has been validated on hardware.
