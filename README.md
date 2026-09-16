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
Everything configurable lives in `config/TestConfig.java`. Four values are
also overridable at runtime via environment variables:

| Setting | Default | Env override |
|---|---|---|
| `DEVICE_UDID` | `192.168.1.88:5555` | `HOT_DEVICE_UDID` |
| `PROJECT_ROOT` | JVM working directory | `HOT_PROJECT_ROOT` |
| Run folder suffix | `FullLoginTest` | `HOT_RUN_NAME` |
| Full run folder name | `<timestamp>_<testname>` | `HOT_RUN_TAG` |

Fixed values (edit the file to change):

- `HOT_PACKAGE` — `com.applicaster.il.hotvod`
- `HOT_ACTIVITY` — `il.net.hot.sharedvod.ui.activities.SplashActivity`
- Test data — `ID_NUMBER`, `PHONE_NUMBER`, `OTP_CODE`
- Timeouts — `APP_LOAD_TIMEOUT`, `SCREEN_WAIT_TIMEOUT`, `POLL_INTERVAL`
- Image comparison — `SIMILARITY_THRESHOLD`, `PIXEL_TOLERANCE`

Running against a different device, without editing source:

```bash
HOT_DEVICE_UDID=192.168.1.90:5555 mvn exec:java \
  -Dexec.mainClass=com.hotplay.automation.smoke.FullLoginTest \
  -Dexec.classpathScope=test
```

**Cold start sequence** (in `FullLoginTest`):

1. `pm clear` — wipes app data and revokes runtime permissions
2. `am force-stop`
3. `pm grant POST_NOTIFICATIONS` — restores the notification permission so
   the Android 13+ system dialog does not block the login screen
4. `am start` — launches `SplashActivity`

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

##Artifacts
Every run creates its own timestamped folder under `Screens/Runs/`, so
previous runs are preserved:

```
Screens/
├── Expected/                                       ← committed baselines (tracked)
└── Runs/
    ├── 2026-09-16_08-07-05_FullLoginTest/
    │   ├── 01_login.png
    │   ├── 02_otp.png
    │   ├── 03_mosaic.png
    │   ├── 04_site_picker.png
    │   └── 05_callback_dialog.png
    └── 2026-09-16_08-21-33_FullLoginTest/
        └── ...
```
Filenames are numbered so `ls` sorts them in journey order:
`01_login` → `02_otp` → `03_mosaic` → `04_site_picker` → `05_callback_dialog`.

Additional folders, all gitignored:

| Folder | Contents | Tracked? |
|---|---|---|
| `Screens/Expected/` | Reference baselines | Yes |
| `Screens/Runs/` | Per-run screenshots | No |
| `Screens/Fail/` | Failed-run screenshots (legacy) | No |
| `logs/` | Text log output | No |
| `xml/` | uiautomator dumps | No |

Nothing under `Screens/Runs/`, `xml/`, or `logs/` is tracked — each clone
starts clean and produces its own artifacts on first run.

##Known limitations
- **Single device only** — the framework targets one `DEVICE_UDID` at a time.
- **Tests use `main()`**, not TestNG/JUnit. Migration planned for parallel and
  multi-device runs.
- **Test data is hardcoded** in `TestConfig` (`ID_NUMBER`, `PHONE_NUMBER`,
  `OTP_CODE`). Parameterize before handing this to multiple testers.
- **OTP is a fixed value** (`123456`). If the backend stops accepting it, the
  test fails at `confirm_button enabled`. Fetching the real OTP from the
  backend or SMS is planned work.
- **Crop comparison is available** via `ImageComparator` but no screen uses
  it yet — no baselines exist under `Screens/Expected/`.
- **Android TV vs. mobile** — this repo targets **mobile**. The Android TV /
  Big Screen flow is a separate project.


## Recent changes. 16/9/26

- **Per-run artifact folders** — every run writes to
  `Screens/Runs/<timestamp>_<testname>/` so previous runs are preserved.
- **`screenshot()` uses `adb exec-out screencap -p`** — streams directly into
  the target file, prints saved path and byte count.
- **`DEVICE_UDID` is env-overridable** via `HOT_DEVICE_UDID`.
- **`PROJECT_ROOT` resolves at runtime** via `System.getProperty("user.dir")`
  — a fresh clone in `/tmp` writes to `/tmp`, not to a hardcoded path.
- **Cold start pre-grants `POST_NOTIFICATIONS`** via `pm grant`, bypassing the
  Android 13+ dialog on every run.
- **Two optional post-OTP dialogs handled** — site picker (`בחר אתר`) and
  legal notice (`לקוח יקר, ...`).




  
