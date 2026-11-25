package com.altbryson.deckremote;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.io.IOException;
import java.util.Arrays;

/**
 * Elgato Stream Deck Original V2 (USB 0fd9:006d) and MK.2 (0fd9:0080) over Android USB host: 15 keys (5 x 3),
 * 72x72 JPEG key images. Protocol as documented by the open-source python-elgato-streamdeck library:
 * input report 0x01 = 4 header bytes + one byte per key (1 = pressed); key images = output report 0x02 in 1024-byte
 * pages [02 07 key last lenLo lenHi pageLo pageHi] + up to 1016 bytes of JPEG; feature 0x03 0x08 = brightness,
 * 0x03 0x02 = reset. Images must be stored rotated 180 degrees (the assets already are).
 */
final class StreamDeck {
    static final int VENDOR = 0x0fd9;
    static final int[] PRODUCTS = {0x006d, 0x0080};
    static final int KEYS = 15;
    private static final int PAGE = 1024, HEADER = 8;

    private final UsbDeviceConnection conn;
    private final UsbInterface intf;
    private final UsbEndpoint in, out;

    static boolean isDeck(UsbDevice d) {
        if (d.getVendorId() != VENDOR) return false;
        for (int p : PRODUCTS) if (d.getProductId() == p) return true;
        return false;
    }

    StreamDeck(UsbManager usb, UsbDevice dev) throws IOException {
        UsbInterface hid = null;
        for (int i = 0; i < dev.getInterfaceCount(); i++) if (dev.getInterface(i).getInterfaceClass() == UsbConstants.USB_CLASS_HID) { hid = dev.getInterface(i); break; }
        if (hid == null) throw new IOException("no HID interface");
        UsbEndpoint ein = null, eout = null;
        for (int i = 0; i < hid.getEndpointCount(); i++) {
            UsbEndpoint e = hid.getEndpoint(i);
            if (e.getDirection() == UsbConstants.USB_DIR_IN) ein = e; else eout = e;
        }
        if (ein == null) throw new IOException("no input endpoint");
        UsbDeviceConnection c = usb.openDevice(dev);
        if (c == null) throw new IOException("can't open the Stream Deck (no USB permission?)");
        if (!c.claimInterface(hid, true)) { c.close(); throw new IOException("can't claim the Stream Deck"); }
        conn = c; intf = hid; in = ein; out = eout;
        android.util.Log.i(DeckService.TAG, "deck endpoints: in " + ein.getAddress() + "/" + ein.getMaxPacketSize()
                + (eout == null ? " no out" : " out " + eout.getAddress() + "/" + eout.getMaxPacketSize() + " type " + eout.getType()));
        reset(); // a fresh start, in case another process left it mid-transfer
        try { Thread.sleep(150); } catch (InterruptedException ignored) { }
    }

    /** Blocks up to timeoutMs; returns the 15 key states, or null if nothing arrived. Throws when unplugged. */
    boolean[] readKeys(int timeoutMs) throws IOException {
        byte[] buf = new byte[512];
        int n = conn.bulkTransfer(in, buf, buf.length, timeoutMs);
        if (n < 0) return null; // timeout (or a transient error; a real unplug is caught by the service)
        if (n < 4 + KEYS || buf[0] != 0x01) return null;
        boolean[] s = new boolean[KEYS];
        for (int i = 0; i < KEYS; i++) s[i] = buf[4 + i] != 0;
        return s;
    }

    void setBrightness(int percent) {
        byte[] r = new byte[32];
        r[0] = 0x03; r[1] = 0x08; r[2] = (byte) Math.max(0, Math.min(100, percent));
        featureReport(r);
    }

    void reset() { byte[] r = new byte[32]; r[0] = 0x03; r[1] = 0x02; featureReport(r); }

    void setImage(int key, byte[] jpeg) throws IOException {
        int page = 0, sent = 0;
        while (sent < jpeg.length) {
            int len = Math.min(PAGE - HEADER, jpeg.length - sent);
            byte[] p = new byte[PAGE];
            p[0] = 0x02; p[1] = 0x07; p[2] = (byte) key; p[3] = (byte) (sent + len >= jpeg.length ? 1 : 0);
            p[4] = (byte) (len & 0xff); p[5] = (byte) (len >> 8); p[6] = (byte) (page & 0xff); p[7] = (byte) (page >> 8);
            System.arraycopy(jpeg, sent, p, HEADER, len);
            outputReport(p);
            sent += len; page++;
        }
    }

    void clear(int key) throws IOException { setImage(key, BLACK); }

    void close() {
        try { conn.releaseInterface(intf); } catch (Exception ignored) { }
        conn.close();
    }

    /** Interrupt OUT endpoint first; if that fails, the same report as a HID SET_REPORT(output) control transfer. */
    private void outputReport(byte[] p) throws IOException {
        int n = -1;
        if (out != null) n = conn.bulkTransfer(out, p, p.length, 1000);
        if (n < 0) n = conn.controlTransfer(0x21, 0x09, (0x02 << 8) | (p[0] & 0xff), intf.getId(), p, p.length, 1000);
        if (n < 0) throw new IOException("image write failed");
    }

    private void featureReport(byte[] r) {
        conn.controlTransfer(0x21, 0x09, (0x03 << 8) | (r[0] & 0xff), intf.getId(), r, r.length, 1000);
    }

    // A tiny all-black 72x72 baseline JPEG (built once at startup by the service from a Bitmap).
    static byte[] BLACK = new byte[0];

    @Override public String toString() { return "StreamDeck" + Arrays.toString(new int[]{in.getAddress(), out == null ? -1 : out.getAddress()}); }
}
