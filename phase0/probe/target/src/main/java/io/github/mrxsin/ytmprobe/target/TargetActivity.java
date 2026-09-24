package io.github.mrxsin.ytmprobe.target;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calls the hook targets from several threads and reports, once per second, which
 * generation values were observed (HR-04 concurrency, HR-05 add/remove).
 */
public final class TargetActivity extends Activity {

    static final String TAG = "YTMProbeTarget";
    private static final int THREADS = 4;
    private static final int INPUT = 7;

    private final ProbeTarget target = new ProbeTarget();
    private final Set<String> h1Seen = ConcurrentHashMap.newKeySet();
    private final Set<String> h2Seen = ConcurrentHashMap.newKeySet();
    private volatile boolean running;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        TextView view = new TextView(this);
        view.setText("YTM Phase-0 probe target. Keep this screen open during probes.");
        setContentView(view);
    }

    @Override
    protected void onStart() {
        super.onStart();
        running = true;
        for (int i = 0; i < THREADS; i++) {
            new Thread(this::callLoop, "probe-caller-" + i).start();
        }
        new Thread(this::reportLoop, "probe-reporter").start();
    }

    @Override
    protected void onStop() {
        running = false;
        super.onStop();
    }

    private void callLoop() {
        while (running) {
            h1Seen.add(classify(target.h1(INPUT), 0));
            h2Seen.add(classify(target.h2(INPUT), 500));
            sleep(2);
        }
    }

    private void reportLoop() {
        while (running) {
            sleep(1000);
            Log.i(TAG, "OBS h1=" + drain(h1Seen) + " h2=" + drain(h2Seen));
        }
    }

    /** "orig", "gN" for a valid generation value, or "INVALID:v". */
    private static String classify(int value, int hookOffset) {
        if (value == INPUT) {
            return "orig";
        }
        int delta = value - INPUT - hookOffset;
        if (delta > 0 && delta % 1000 == 0) {
            return "g" + delta / 1000;
        }
        return "INVALID:" + value;
    }

    private static Set<String> drain(Set<String> seen) {
        Set<String> copy = new TreeSet<>(seen);
        seen.removeAll(copy);
        return copy;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
