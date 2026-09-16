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
    //
    // pm clear revokes all runtime permissions, so Android 13+ re-prompts for
    // POST_NOTIFICATIONS on next launch. Re-grant non-interactively right
    // after clear so the system dialog never appears.

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
    // Streams the PNG from the device via `adb exec-out screencap -p`, which
    // avoids the temp file dance (write to /sdcard, pull, rm) and works on
    // every Android version. Creates parent directories if missing, and
    // prints the saved path + size so failures are visible in the log.

    public void screenshot(String localPath) {
        File f = new File(localPath);
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) {
            boolean ok = parent.mkdirs();
            if (!ok && !parent.exists()) {
                System.err.println("  [warn] could not create dir: " + parent);
            }
        }
        try {
            String[] cmd = { "adb", "-s", udid, "exec-out", "screencap", "-p" };
            Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
            long bytes;
            try (InputStream in = p.getInputStream();
                 OutputStream out = new FileOutputStream(f)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                bytes = f.length();
            }
            p.waitFor(20, TimeUnit.SECONDS);
            System.out.println("  screenshot saved: " + f.getAbsolutePath()
                    + " (" + bytes + " bytes)");
        } catch (Exception e) {
            System.err.println("  [warn] screenshot failed: " + e.getMessage());
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