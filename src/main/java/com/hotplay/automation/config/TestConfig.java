package com.hotplay.automation.config;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class TestConfig {
    private TestConfig() {}

    // ---- Device ----
    // Override at runtime via HOT_DEVICE_UDID env var, e.g.
    //   HOT_DEVICE_UDID=192.168.1.88:5555 mvn exec:java ...
    public static final String DEVICE_UDID =
            System.getenv().getOrDefault(
                    "HOT_DEVICE_UDID", "192.168.1.88:5555");

    // ---- App ----
    public static final String HOT_PACKAGE  = "com.applicaster.il.hotvod";
    public static final String HOT_ACTIVITY =
            "il.net.hot.sharedvod.ui.activities.SplashActivity";

    // ---- Test data ----
    public static final String ID_NUMBER    = "999286578";
    public static final String PHONE_NUMBER = "0543501323";
    public static final String OTP_CODE     = "123456";

    // ---- Paths ----
    // PROJECT_ROOT resolves against the JVM working directory so the framework
    // works from any clone location. Override with HOT_PROJECT_ROOT if needed.
    public static final String PROJECT_ROOT =
            System.getenv().getOrDefault(
                    "HOT_PROJECT_ROOT", System.getProperty("user.dir"));

    public static final String SCREENS_DIR  = PROJECT_ROOT + "/Screens";
    public static final String EXPECTED_DIR = SCREENS_DIR + "/Expected";
    public static final String RUNS_DIR     = SCREENS_DIR + "/Runs";
    public static final String FAIL_DIR     = SCREENS_DIR + "/Fail";
    public static final String LOGS_DIR     = PROJECT_ROOT + "/logs";
    public static final String XML_DIR      = PROJECT_ROOT + "/xml";

    // ---- Per-run output folder ----
    //
    // Computed once per JVM, so every screenshot and dump from a single test
    // run lands in the same folder. Format:
    //   Screens/Runs/2026-09-16_07-54-12_FullLoginTest/
    //
    // Override via HOT_RUN_TAG if you want a stable folder name (useful in
    // CI where every run should overwrite the previous).
    public static final String RUN_TAG =
            System.getenv().getOrDefault(
                    "HOT_RUN_TAG", buildRunTag());

    public static final String RUN_DIR =
            System.getenv().getOrDefault(
                    "HOT_RUN_DIR", RUNS_DIR + "/" + RUN_TAG);

    private static String buildRunTag() {
        String ts = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String testName = System.getenv().getOrDefault(
                "HOT_RUN_NAME", "FullLoginTest");
        return ts + "_" + testName;
    }

    // ---- Timeouts (ms) ----
    public static final long APP_LOAD_TIMEOUT    = 30_000;
    public static final long SCREEN_WAIT_TIMEOUT = 15_000;
    public static final long POLL_INTERVAL       = 500;

    // ---- Image comparison ----
    public static final double SIMILARITY_THRESHOLD = 95.0;
    public static final int    PIXEL_TOLERANCE      = 10;
}