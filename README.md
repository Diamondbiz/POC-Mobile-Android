End-to-end UI automation for the **HOT Play** Android app, driven directly
through **ADB** — no Appium, no Selenium. Each screen is validated by reading
the live UI hierarchy (`uiautomator dump`) and asserting expected elements.

**Maintainer:** [@Diamondbiz](https://github.com/Diamondbiz)

---

## What it does

Drives a physical Android device over the network through the HOT Play login
journey and asserts each screen along the way:

cold start (pm clear)
→ Login screen (ID + phone + terms checkbox)
→ OTP screen (6-digit code)
→ Site picker dialog (multi-site accounts only)
→ Legal notice dialog (first login)
→ Live Mosaic


Every assertion is data-driven. One generic `ScreenValidator` walks a
`ScreenProfile` and checks each expected element — the tests do not hard-code
element lookups.

---

## Requirements

- macOS (paths in `TestConfig` are macOS-specific)
- JDK 17
- Maven 3.9+
- ADB on `PATH` (Android Platform Tools)
- A physical Android device reachable over the network

Verify the environment:

```bash
adb devices
java -version
mvn -version


Project structure

src/main/java/com/hotplay/automation/
├── config/       TestConfig — device UDID, package, activity, paths, timeouts
├── core/         DeviceController, XmlParser, UiNode, KeyCodes
├── flows/        SitePickerFlow, CallbackDialogFlow  (optional post-OTP dialogs)
├── profiles/     ScreenProfile + one profile per screen
├── utils/        ImageComparator
└── validators/   ScreenValidator, ScreenAssertionResult

src/test/java/com/hotplay/automation/
└── smoke/        FullLoginTest — the end-to-end journey



How it works

The layers only depend downward:

Layer	                                                  Job

DeviceController	                          All ADB I/O. dumpUi() returns a UiNode tree; tap(UiNode) clicks a parsed node.

XmlParser	                                  Parses a uiautomator dump string into a UiNode tree.

UiNode	                                    Immutable wrapper for one <node> — resourceId, text, bounds(), enabled, plus tree navigation (findById, findByText).

ScreenProfile	                              Pure data: expected elements (and optional crop coordinates) for one screen.

ScreenValidator	                            One generic class that asserts any profile.

SitePickerFlow, CallbackDialogFlow	        Handle optional dialogs after OTP. handleIfPresent() returns false when the dialog doesn't appear.


Screens & resource IDs
All IDs are captured from live device dumps.

Login — LoginScreenProfile

Element                          	Resource-id

Close (X)	          com.applicaster.il.hotvod:id/close_dialog_btn

ID field	          com.applicaster.il.hotvod:id/account_edittext

Phone field	        com.applicaster.il.hotvod:id/pin_edittext

Terms checkbox	    com.applicaster.il.hotvod:id/approve_terms_checkbox

התחבר button	com.applicaster.il.hotvod:id/connect_btn

OTP — OtpScreenProfile
Element	                        Resource-id / text

Instruction	            אנא הזן את קוד האימות שנשלח ב-SMS

OTP field	              com.applicaster.il.hotvod:id/token_edittext

שלח button	com.applicaster.il.hotvod:id/confirm_button
שלח קוד שנית	com.applicaster.il.hotvod:id/resend_token_textview

Site picker dialog — SitePickerProfile

Element	                           Resource-id

Dialog list              	com.applicaster.il.hotvod:id/accounts_dialog_list_view

Title (בחר אתר).        	com.applicaster.il.hotvod:id/accounts_picker_title_tv

Dismiss (X)	              com.applicaster.il.hotvod:id/accounts_picker_dismiss_btn

All rows share android:id/text1, so rows are selected by text substring.

Legal notice — CallbackDialogProfile
Element	                            Resource-id
Scroll view	             com.applicaster.il.hotvod:id/callback_dialog_scroll_view

Message	                 com.applicaster.il.hotvod:id/callback_dialog_message_tv

אישור button	com.applicaster.il.hotvod:id/callback_dialog_action_button


Live Mosaic — LiveMosaicProfil

Element	                   Resource-id / text

Grid	               com.applicaster.il.hotvod:id/all_channels_fragment_recycler_view

Channel tile	       com.applicaster.il.hotvod:id/item_view_holder_channel_container

Tile title	         com.applicaster.il.hotvod:id/item_view_holder_channel_title_text_view

Top bar              title	הכל

Bottom nav          	com.applicaster.il.hotvod:id/next_tv_activity_bottom_navigation_recycler_view


Running the test
Compile once:

mvn clean test-compile

Run the end-to-end journey:
mvn exec:java \
  -Dexec.mainClass=com.hotplay.automation.smoke.FullLoginTest \
  -Dexec.classpathScope=test

Or as one command (recompile + run):

mvn clean test-compile exec:java \
  -Dexec.mainClass=com.hotplay.automation.smoke.FullLoginTest \
  -Dexec.classpathScope=test

Tests use public static void main(...), not TestNG/JUnit. TestNG migration
is planned.



Configuration
Everything configurable lives in config/TestConfig.java:

DEVICE_UDID — the target device address (default 192.168.1.167:5555)

HOT_PACKAGE — com.applicaster.il.hotvod

HOT_ACTIVITY — il.net.hot.sharedvod.ui.activities.SplashActivity

Test data — ID_NUMBER, PHONE_NUMBER, OTP_CODE

Folder paths — CURRENT_DIR, FAIL_DIR, XML_DIR, LOGS_DIR

Timeouts — APP_LOAD_TIMEOUT, SCREEN_WAIT_TIMEOUT, POLL_INTERVAL

Change device or credentials here; nothing else needs editing.


Adding a new screen
Three steps, no existing file changes.

1. Create a profile in profiles/XScreenProfile.java:

public final class XScreenProfile {
    private static final String PKG = "com.applicaster.il.hotvod";
    public static ScreenProfile get() {
        return new ScreenProfile.Builder(
                        "XScreen",
                        ScreenMarker.Type.RESOURCE_ID, PKG + ":id/marker")
                .element("Element A", ScreenMarker.Type.RESOURCE_ID, PKG + ":id/a")
                .element("Element B", ScreenMarker.Type.TEXT, "visible text")
                .build();
    }
}

Marker types: RESOURCE_ID, TEXT, TEXT_PRESENT_ANYWHERE.

2. Call it from the test: validator.validate(XScreenProfile.get());

3. Run. No changes to core/, validators/, or any other test.

Artifacts
Each run writes to timestamped folders, all ignored by .gitignore:


Folder	                         Contents	              Tracked?

Screens/Expected/	           Reference baselines.        	Yes
Screens/Current screen/	     Current run screenshots	     No
Screens/Fail/	               Failed screenshots	           No
logs/                        JSON test logs                No
xml/                       	 uiautomator dumps	           No
test-logs/	                 Plain text logger output	     No

Known limitations
Absolute paths are hardcoded to /Users/Johnny/IdeaProjects/POC-Mobile-Android. Other machines must edit TestConfig.PROJECT_ROOT.

Single device only — one DEVICE_UDID at a time.
Tests use main(), not TestNG/JUnit. Migration planned for parallel / multi-device runs.
Crop comparison is available via ImageComparator but not yet used in any profile.
