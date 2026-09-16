package com.hotplay.automation.validators;

import com.hotplay.automation.config.TestConfig;
import com.hotplay.automation.core.DeviceController;
import com.hotplay.automation.core.UiNode;
import com.hotplay.automation.profiles.ScreenMarker;
import com.hotplay.automation.profiles.ScreenProfile;
import com.hotplay.automation.utils.ImageComparator;

import java.io.File;

/**
 * Generic validator: walks any ScreenProfile against the live UiNode tree
 * and reports pass/fail for every expected element and crop.
 *
 * Screenshots for crop comparison are written to the current run's folder
 * (TestConfig.RUN_DIR) so each run preserves its own artifacts.
 */
public class ScreenValidator {

    private final DeviceController device;

    public ScreenValidator(DeviceController device) { this.device = device; }

    public ScreenAssertionResult validate(ScreenProfile profile) {
        ScreenAssertionResult r = new ScreenAssertionResult(profile.name);
        UiNode root = device.dumpUi();

        // ---- marker check ----
        if (!checkMarker(root, profile.marker)) {
            r.fail("marker (" + profile.marker.value + ")");
            System.out.println("[FAIL] " + profile.name
                    + ": marker missing — aborting element checks");
            return r;
        }
        System.out.println("  ✔ marker: " + profile.marker.value);

        // ---- element checks ----
        for (ScreenProfile.Element e : profile.elements) {
            boolean ok = checkElement(root, e);
            if (ok) { r.pass(e.human); System.out.println("  ✔ " + e.human); }
            else    { r.fail(e.human); System.out.println("  ✘ " + e.human
                    + "  (" + e.value + ")"); }
        }

        // ---- crops (image comparison, optional) ----
        for (ScreenProfile.Crop c : profile.crops) {
            if (c.baseline == null || !new File(c.baseline).exists()) continue;
            String current = TestConfig.RUN_DIR + "/" + profile.name
                    + "_" + c.name + ".png";
            device.screenshot(current);
            boolean ok = ImageComparator.matches(current, c.baseline);
            if (ok) { r.pass("crop:" + c.name); System.out.println("  ✔ crop " + c.name); }
            else    { r.fail("crop:" + c.name); System.out.println("  ✘ crop " + c.name); }
        }

        return r;
    }

    // ------------------------------------------------------------------

    private static boolean checkMarker(UiNode root, ScreenMarker m) {
        switch (m.type) {
            case RESOURCE_ID:           return root.findById(m.value) != null;
            case TEXT:                  return root.findByText(m.value) != null;
            case TEXT_PRESENT_ANYWHERE: return root.findByTextContaining(m.value) != null;
            default:                    return false;
        }
    }

    private static boolean checkElement(UiNode root, ScreenProfile.Element e) {
        switch (e.type) {
            case RESOURCE_ID:           return root.findById(e.value) != null;
            case TEXT:                  return root.findByText(e.value) != null;
            case TEXT_PRESENT_ANYWHERE: return root.findByTextContaining(e.value) != null;
            default:                    return false;
        }
    }
}