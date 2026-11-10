# Easy Print — Android project

Arabic/French Android starter app for Epson Stylus SX130 USB OTG detection.

## Build APK with GitHub Actions
1. Upload the contents of this `EasyPrint` folder to the root of a GitHub repository (not the enclosing folder itself).
2. Use the `main` branch.
3. Open the repository's **Actions** tab and select **Build Easy Print APK**.
4. Choose **Run workflow** if it has not started automatically.
5. When the run finishes, download the `Easy-Print-debug-apk` artifact. It contains `app-debug.apk`.

The workflow uses Java 17 and Gradle 8.9 with Android Gradle Plugin 8.7.3.

## Current functionality and limitations
- Arabic/French interface text.
- USB OTG discovery and Epson USB identity recognition (VID `04B8`, PID `0883`).
- Android USB permission request for a matching device.
- Image/PDF selection and handoff to Android's Print Framework.

**Direct USB printing is not implemented yet.** USB detection and permission are not a printer driver. The SX130 requires a compatible ESC/P-R backend. The app currently hands documents to Android Print Framework; successful printing depends on an installed compatible print service/driver. Direct ESC/P-R raster output over OTG has not yet been integrated or tested on a physical SX130.
