package com.easyprint.app;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int PICK_FILE = 41;
    private static final String ACTION_USB_PERMISSION = "com.easyprint.app.USB_PERMISSION";
    // Epson Stylus SX130 USB identity (shared by some regional rebadges).
    private static final int EPSON_VENDOR_ID = 0x04B8;
    private static final int SX130_PRODUCT_ID = 0x0883;
    private TextView status, fileLabel;
    private Uri selectedUri;
    private String selectedName = "Aucun fichier choisi / لم يتم اختيار ملف";
    private UsbManager usbManager;
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                Toast.makeText(context, granted ? "USB autorisé / تم السماح" : "USB refusé / تم رفض USB", Toast.LENGTH_SHORT).show();
                refreshUsbStatus();
            }
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        usbManager = (UsbManager) getSystemService(USB_SERVICE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_PERMISSION), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_PERMISSION));
        buildUi();
        refreshUsbStatus();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(20));
        root.setBackgroundColor(Color.rgb(247, 246, 243));

        TextView brand = new TextView(this);
        brand.setText("EASY PRINT"); brand.setTextSize(27); brand.setTextColor(Color.rgb(30,30,30)); brand.setGravity(Gravity.CENTER);
        brand.setTypeface(null, 1); root.addView(brand, matchWrap());
        TextView sub = new TextView(this);
        sub.setText("Impression simple • طباعة سهلة"); sub.setTextSize(14); sub.setTextColor(Color.DKGRAY); sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = matchWrap(); sp.bottomMargin = dp(26); root.addView(sub, sp);

        TextView usbTitle = new TextView(this); usbTitle.setText("ÉTAT DE L'IMPRIMANTE / حالة الطابعة"); usbTitle.setTextSize(13); usbTitle.setTypeface(null,1); usbTitle.setTextColor(Color.rgb(90,70,40)); root.addView(usbTitle, matchWrap());
        status = new TextView(this); status.setTextSize(16); status.setTextColor(Color.BLACK); status.setPadding(dp(14),dp(14),dp(14),dp(14)); status.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams stp = matchWrap(); stp.topMargin=dp(8); stp.bottomMargin=dp(22); root.addView(status, stp);

        Button scan = makeButton("Rechercher imprimante USB / البحث عن طابعة USB");
        scan.setOnClickListener(v -> { refreshUsbStatus(); requestUsbPermissionIfFound(); }); root.addView(scan, buttonParams());
        Button identity = makeButton("Lire identité USB / قراءة هوية الطابعة");
        identity.setOnClickListener(v -> readUsbPrinterIdentity()); root.addView(identity, buttonParams());
        Button choose = makeButton("Choisir une photo ou un PDF / اختيار صورة أو PDF");
        choose.setOnClickListener(v -> pickFile()); root.addView(choose, buttonParams());
        fileLabel = new TextView(this); fileLabel.setText(selectedName); fileLabel.setTextColor(Color.DKGRAY); fileLabel.setTextSize(14); fileLabel.setPadding(4,dp(7),4,dp(12)); root.addView(fileLabel, matchWrap());
        Button print = makeButton("Ouvrir service Android / فتح خدمة الطباعة");
        print.setTextColor(Color.WHITE); print.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(39,39,39)));
        print.setOnClickListener(v -> startPrint()); root.addView(print, buttonParams());

        TextView note = new TextView(this);
        note.setText("Remarque : le pilote USB direct pour la SX130 n'est pas encore intégré. La compatibilité avec les pilotes ESC/P2/Gutenprint doit être vérifiée pour ce modèle précis avant tout envoi de données.\n\nملاحظة: تعريف USB المباشر لطابعة SX130 لم يُدمج بعد. يجب التحقق من توافق تعريف ESC/P2 أو Gutenprint مع هذا الموديل تحديداً قبل إرسال بيانات الطباعة.");
        note.setTextColor(Color.rgb(95,95,95)); note.setTextSize(12); note.setPadding(0,dp(22),0,0); root.addView(note, matchWrap());
        setContentView(root);
    }

    private void refreshUsbStatus() {
        if (usbManager == null) { status.setText("USB Host indisponible / USB غير متاح"); return; }
        List<UsbDevice> devices = new ArrayList<>(usbManager.getDeviceList().values());
        if (devices.isEmpty()) { status.setText("Aucune imprimante USB détectée.\nلم يتم العثور على طابعة USB. Connectez OTG ثم allumez l'imprimante."); return; }
        StringBuilder s = new StringBuilder("USB détecté / تم اكتشاف USB:\n");
        for (UsbDevice d : devices) {
            boolean sx130 = d.getVendorId() == EPSON_VENDOR_ID && d.getProductId() == SX130_PRODUCT_ID;
            s.append(sx130 ? "✓ Epson SX130" : "• USB device")
             .append(" (VID:").append(String.format("%04X", d.getVendorId()))
             .append(" PID:").append(String.format("%04X", d.getProductId())).append(")\n")
             .append("Interfaces: ").append(d.getInterfaceCount()).append("\n")
             .append(usbManager.hasPermission(d) ? "USB permission: granted" : "USB permission: not granted").append("\n");
            for (int i = 0; i < d.getInterfaceCount(); i++) {
                android.hardware.usb.UsbInterface intf = d.getInterface(i);
                s.append("  Interface ").append(i).append(": class ").append(intf.getInterfaceClass())
                 .append(", subclass ").append(intf.getInterfaceSubclass())
                 .append(", protocol ").append(intf.getInterfaceProtocol()).append("\n");
                for (int j = 0; j < intf.getEndpointCount(); j++) {
                    android.hardware.usb.UsbEndpoint ep = intf.getEndpoint(j);
                    s.append("    Endpoint ").append(j).append(": address 0x")
                     .append(Integer.toHexString(ep.getAddress())).append(", type ").append(ep.getType())
                     .append(", direction ").append(ep.getDirection()).append("\n");
                }
            }
        }
        status.setText(s.toString());
    }

    

private void readUsbPrinterIdentity() {
    if (usbManager == null) {
        status.setText("USB Host indisponible / USB غير متاح");
        return;
    }
    for (UsbDevice device : usbManager.getDeviceList().values()) {
        if (device.getVendorId() != EPSON_VENDOR_ID || device.getProductId() != SX130_PRODUCT_ID) continue;
        if (!usbManager.hasPermission(device)) {
            status.setText("Autorisation USB requise / يلزم السماح بالاتصال");
            requestUsbPermissionIfFound();
            return;
        }
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            android.hardware.usb.UsbInterface intf = device.getInterface(i);
            if (intf.getInterfaceClass() != 7) continue;
            android.hardware.usb.UsbDeviceConnection connection = null;
            boolean claimed = false;
            try {
                connection = usbManager.openDevice(device);
                if (connection == null) throw new IllegalStateException("USB connection unavailable");
                claimed = connection.claimInterface(intf, true);
                if (!claimed) throw new IllegalStateException("Cannot claim printer interface");
                // USB Printer Class GET_DEVICE_ID is a read-only request (0xA1 / 0x00).
                byte[] response = new byte[1024];
                int count = connection.controlTransfer(0xA1, 0x00, 0, intf.getId(), response, response.length, 2000);
                if (count < 2) {
                    status.setText("Interface USB trouvée, mais identité non fournie.\nتم العثور على منفذ الطباعة لكن الطابعة لم ترسل هويتها.");
                    return;
                }
                int declaredLength = ((response[0] & 0xFF) << 8) | (response[1] & 0xFF);
                int dataLength = Math.min(Math.max(0, declaredLength - 2), count - 2);
                String deviceId = new String(response, 2, dataLength, java.nio.charset.StandardCharsets.US_ASCII).trim();
                status.setText("Epson SX130 — identité USB:\n" + (deviceId.isEmpty() ? "Réponse vide / رد فارغ" : deviceId)
                        + "\n\nLecture seule: aucune donnée d'impression envoyée.\nقراءة فقط: لم يتم إرسال أي أمر طباعة.");
                return;
            } catch (Exception e) {
                status.setText("Lecture identité USB échouée: " + e.getMessage()
                        + "\nتعذرت قراءة هوية الطابعة.");
                return;
            } finally {
                if (connection != null) {
                    if (claimed) connection.releaseInterface(intf);
                    connection.close();
                }
            }
        }
        status.setText("Interface USB Printer Class introuvable / لم يتم العثور على واجهة الطباعة");
        return;
    }
    status.setText("Epson SX130 non détectée / الطابعة غير متصلة");
}

private void requestUsbPermissionIfFound() {
    if (usbManager == null) {
        status.setText("USB indisponible / USB غير متاح");
        return;
    }

    try {
        boolean found = false;

        for (UsbDevice d : usbManager.getDeviceList().values()) {
            if (d.getVendorId() == EPSON_VENDOR_ID
                    && d.getProductId() == SX130_PRODUCT_ID) {

                found = true;

                if (!usbManager.hasPermission(d)) {
                    Intent permissionIntent =
                            new Intent(ACTION_USB_PERMISSION);

                    // Android 14 / 15 compatibility
                    permissionIntent.setPackage(getPackageName());

                    PendingIntent pi = PendingIntent.getBroadcast(
                            this,
                            0,
                            permissionIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                                    | PendingIntent.FLAG_MUTABLE
                    );

                    usbManager.requestPermission(d, pi);

                    status.setText(
                            "Demande d'autorisation USB...\n"
                            + "يرجى السماح بالاتصال بالطابعة"
                    );
                } else {
                    status.setText(
                            "Epson SX130 connectée / الطابعة متصلة"
                    );
                }
            }
        }

        if (!found) {
            status.setText(
                    "Epson SX130 non détectée.\n"
                    + "لم يتم العثور على Epson SX130"
            );
        }

    } catch (RuntimeException e) {
        status.setText(
                "Erreur USB: " + e.getClass().getSimpleName()
                + "\n" + e.getMessage()
        );
    }
}


    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "application/pdf"});
        startActivityForResult(i, PICK_FILE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE && resultCode == RESULT_OK && data != null) {
            selectedUri = data.getData();
            if (selectedUri != null) {
                try { getContentResolver().takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (SecurityException ignored) {}
                selectedName = selectedUri.getLastPathSegment();
                fileLabel.setText("Fichier / الملف: " + selectedName);
            }
        }
    }

    private void startPrint() {
        if (selectedUri == null) { Toast.makeText(this, "Choisissez un fichier / اختر ملفاً أولاً", Toast.LENGTH_LONG).show(); return; }
        String type = getContentResolver().getType(selectedUri);
        if (type == null) type = "";
        PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        if (pm == null) { Toast.makeText(this, "Service d'impression Android indisponible / خدمة الطباعة غير متاحة", Toast.LENGTH_LONG).show(); return; }
        String name = selectedName == null ? "EasyPrint" : selectedName;
        pm.print("Easy Print - " + name, new FilePrintAdapter(selectedUri, type), new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setResolution(new PrintAttributes.Resolution("easyprint", "Default", 300, 300)).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build());
    }

    private class FilePrintAdapter extends PrintDocumentAdapter {
        private final Uri uri; private final String mime; private int pages = 1;
        FilePrintAdapter(Uri u, String m) { uri=u; mime=m; }
        @Override public void onLayout(PrintAttributes oldA, PrintAttributes newA, CancellationSignal signal, LayoutResultCallback callback, android.os.Bundle extras) {
            if (signal.isCanceled()) { callback.onLayoutCancelled(); return; }
            if (mime.equals("application/pdf")) {
                try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) { android.graphics.pdf.PdfRenderer r = new android.graphics.pdf.PdfRenderer(pfd); pages = r.getPageCount(); r.close(); }
                catch (Exception e) { callback.onLayoutFailed("Impossible de lire le PDF / تعذر قراءة PDF"); return; }
            } else pages = 1;
            PrintDocumentInfo info = new PrintDocumentInfo.Builder("easy-print.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pages).build();
            callback.onLayoutFinished(info, true);
        }
        @Override public void onWrite(PageRange[] ranges, ParcelFileDescriptor destination, CancellationSignal signal, WriteResultCallback callback) {
            PdfDocument out = new PdfDocument();
            try {
                int width = 595, height = 842;
                if (mime.equals("application/pdf")) {
                    try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) {
                        android.graphics.pdf.PdfRenderer renderer = new android.graphics.pdf.PdfRenderer(pfd);
                        for (int n=0;n<renderer.getPageCount();n++) {
                            if (signal.isCanceled()) { callback.onWriteCancelled(); renderer.close(); out.close(); return; }
                            android.graphics.pdf.PdfRenderer.Page src = renderer.openPage(n);
                            Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                            b.eraseColor(Color.WHITE); src.render(b, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT); src.close();
                            PdfDocument.Page page = out.startPage(new PdfDocument.PageInfo.Builder(width,height,n+1).create()); page.getCanvas().drawBitmap(b,0,0,null); out.finishPage(page); b.recycle();
                        }
                        renderer.close();
                    }
                } else {
                    Bitmap b;
                    try (InputStream in = getContentResolver().openInputStream(uri)) { b = BitmapFactory.decodeStream(in); }
                    if (b == null) throw new Exception("Image invalide");
                    PdfDocument.Page page = out.startPage(new PdfDocument.PageInfo.Builder(width,height,1).create());
                    Canvas c = page.getCanvas(); c.drawColor(Color.WHITE);
                    float scale = Math.min((float)width/b.getWidth(), (float)height/b.getHeight());
                    float w=b.getWidth()*scale, h=b.getHeight()*scale;
                    c.drawBitmap(b,null,new android.graphics.RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),new Paint(Paint.FILTER_BITMAP_FLAG));
                    out.finishPage(page); b.recycle();
                }
                try (FileOutputStream fos = new FileOutputStream(destination.getFileDescriptor())) { out.writeTo(fos); }
                out.close(); callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
            } catch (Exception e) { out.close(); callback.onWriteFailed(e.getMessage()); }
        }
    }

    private Button makeButton(String text) { Button b=new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(14); b.setTextColor(Color.rgb(35,35,35)); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(224,215,199))); return b; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1,-2); }
    private LinearLayout.LayoutParams buttonParams() { LinearLayout.LayoutParams p=matchWrap(); p.bottomMargin=dp(10); p.height=dp(54); return p; }
    private int dp(int v) { return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    @Override protected void onDestroy() { super.onDestroy(); try { unregisterReceiver(usbReceiver); } catch (Exception ignored) {} }
}
