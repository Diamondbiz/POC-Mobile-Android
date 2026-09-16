package com.hotplay.automation.core;

import com.hotplay.automation.config.TestConfig;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Low-level wrapper around the adb command-line tool.
 * Every adb call in the framework goes through here.
 */
public class DeviceController {

    private final String udid;

    public DeviceController(String udid) { this.udid = udid; }
    public DeviceController() { this(TestConfig.DEVICE_UDID); }

    // ---- process plumbing -------------------------------------------------

    private String sh(String... args) {
        try {
            String[] cmd = new String[args.length + 3];
            cmd[0] = "adb"; cmd[1] = "-s"; cmd[2] = udid;
            System.arraycopy(args, 0, cmd, 3, args.length);
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = read(p.getInputStream());
            p.waitFor(20, TimeUnit.SECONDS);
            return out.trim();
        } catch (Exception e) {
            throw new RuntimeException("adb " + String.join(" ", args), e);
        }
    }

    private void shNoOut(String... args) {
        try {
            String[] cmd = new String[args.length + 3];
            cmd[0] = "adb"; cmd[1] = "-s"; cmd[2] = udid;
            System.arraycopy(args, 0, cmd, 3, args.length);
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            drain(p.getInputStream());
            p.waitFor(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("adb " + String.join(" ", args), e);
        }
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[4096]; int n;
        while ((n = in.read(buf)) != -1) b.write(buf, 0, n);
        return b.toString(StandardCharsets.UTF_8);
    }

    private static void drain(InputStream in) throws IOException {
        byte[] buf = new byte[4096];
        while (in.read(buf) != -1) { /* discard */ }
    }

    // ---- connection -------------------------------------------------------

    public boolean isConnected() {
        try { return sh("get-state").contains("device"); }
        catch (Exception e) { return false; }
    }

    public void connect() {
        try {
            new ProcessBuilder("adb", "connect", udid)
                    .redirectErrorStream(true).start().waitFor();
        } catch (Exception e) {
            throw new RuntimeException("adb connect failed", e);
        }
    }

    // ---- app lifecycle ----------------------------------------------------

    public void clearAppData(String pkg) {
        shNoOut("shell", "pm", "clear", pkg);
    }

    public void forceStopApp(String pkg) {
        shNoOut("shell", "am", "force-stop", pkg);
    }

    public void launchApp(String pkg, String activity) {
        shNoOut("shell", "am", "start", "-n", pkg + "/" + activity);
    }

    // ---- permissions ------------------------------------------------------

    public void grantPermission(String pkg, String permission) {
        shNoOut("shell", "pm", "grant", pkg, permission);
    }

    public void revokePermission(String pkg, String permission) {
        shNoOut("shell", "pm", "revoke", pkg, permission);
    }

    // ---- UI hierarchy -----------------------------------------------------

    public UiNode dumpUi() {
        return XmlParser.parse(dumpUiXml());
    }

    public String dumpUiXml() {
        shNoOut("shell", "uiautomator", "dump", "/sdcard/window_dump.xml");
        return sh("shell", "cat", "/sdcard/window_dump.xml");
    }

    public String dumpUiXmlToFile(String tag) {
        String xml = dumpUiXml();
        try {
            new File(TestConfig.XML_DIR).mkdirs();
            File f = new File(TestConfig.XML_DIR,
                    tag + "_" + System.currentTimeMillis() + ".xml");
            try (Writer w = new OutputStreamWriter(
                    new FileOutputStream(f), StandardCharsets.UTF_8)) {
                w.write(xml);
            }
            return f.getAbsolutePath();
        } catch (IOException e) {
            return null;
        }
    }

    // ---- screenshots ------------------------------------------------------
    //
    // Writes a PNG to the device, pulls it, deletes the remote copy. This is
    // the reliable path — `adb exec-out screencap -p` corrupts the byte
    // stream on some macOS adb builds (identical PNGs for every screen).
    //
    // Three round-trips, but byte-exact on every adb version we've tested.

    public void screenshot(String localPath) {
        File f = new File(localPath);
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) {
            boolean ok = parent.mkdirs();
            if (!ok && !parent.exists()) {
                System.err.println("  [warn] could not create dir: " + parent);
            }
        }

        String remote = "/sdcard/__hot_screenshot.png";
        shNoOut("shell", "screencap", "-p", remote);
        shNoOut("pull", remote, localPath);
        shNoOut("shell", "rm", remote);

        if (f.exists() && f.length() > 0) {
            System.out.println("  screenshot saved: " + f.getAbsolutePath()
                    + " (" + f.length() + " bytes)");
        } else {
            System.err.println("  [warn] screenshot NOT written: "
                    + f.getAbsolutePath());
        }
    }

    // ---- input ------------------------------------------------------------

    public void tap(int x, int y) {
        shNoOut("shell", "input", "tap",
                String.valueOf(x), String.valueOf(y));
    }

    public void tap(UiNode node) {
        tap(node.centerX(), node.centerY());
    }

    public void key(int keyCode) {
        shNoOut("shell", "input", "keyevent", String.valueOf(keyCode));
    }

    public void text(String s) {
        shNoOut("shell", "input", "text", s.replace(" ", "%s"));
    }
}