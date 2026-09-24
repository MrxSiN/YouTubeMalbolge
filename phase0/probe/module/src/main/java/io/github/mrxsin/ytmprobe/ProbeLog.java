package io.github.mrxsin.ytmprobe;

import android.util.Log;

/** One logcat tag for every probe observation, prefixed with the generation. */
final class ProbeLog {

    static final String TAG = "YTMProbe";

    private ProbeLog() {
    }

    static void i(String message) {
        Log.i(TAG, "g" + BuildConfig.PROBE_GENERATION + " " + message);
    }

    static void e(String message, Throwable error) {
        Log.e(TAG, "g" + BuildConfig.PROBE_GENERATION + " " + message, error);
    }
}
