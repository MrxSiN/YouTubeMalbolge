package io.github.mrxsin.ytmprobe;

import android.app.Activity;
import android.os.Bundle;

import java.util.List;

import io.github.libxposed.service.HookedTarget;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * Manager-side probe controller, driven over adb:
 *
 * <pre>
 * am start -n io.github.mrxsin.ytmprobe/.ProbeControlActivity --es action status
 * am start -n io.github.mrxsin.ytmprobe/.ProbeControlActivity --es action write --ei value 5
 * am start -n io.github.mrxsin.ytmprobe/.ProbeControlActivity --es action reload
 * </pre>
 */
public final class ProbeControlActivity extends Activity {

    // XposedServiceHelper delivers a bound service once per process; keep it.
    private static volatile XposedService service;
    private static boolean registered;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String action = getIntent().getStringExtra("action");
        int value = getIntent().getIntExtra("value", 0);
        Runnable command = () -> {
            run(service, action == null ? "status" : action, value);
            finish();
        };
        if (service != null) {
            command.run();
            return;
        }
        synchronized (ProbeControlActivity.class) {
            if (registered) {
                ProbeLog.i("CTRL service not yet bound");
                finish();
                return;
            }
            registered = true;
        }
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService bound) {
                service = bound;
                runOnUiThread(command);
            }

            @Override
            public void onServiceDied(XposedService dead) {
                service = null;
                ProbeLog.i("CTRL service died");
            }
        });
    }

    private static void run(XposedService service, String action, int value) {
        try {
            switch (action) {
                case "write":
                    boolean ok = service.getRemotePreferences(ProbeModule.PREFS_GROUP)
                            .edit().putInt("value", value).commit();
                    ProbeLog.i("CTRL write value=" + value + " commit=" + ok);
                    break;
                case "reload":
                    for (HookedTarget target : targets(service)) {
                        service.hotReloadModule(target, new Bundle(), (t, result) ->
                                ProbeLog.i("CTRL reload result pid=" + t.getPid() + " status="
                                        + result.status() + " message=" + result.message()));
                    }
                    break;
                default:
                    ProbeLog.i("CTRL status api=" + service.getApiVersion() + " framework="
                            + service.getFrameworkName() + " " + service.getFrameworkVersion()
                            + " scope=" + service.getScope());
                    targets(service);
            }
        } catch (Throwable error) {
            ProbeLog.e("CTRL " + action + " failed", error);
        }
    }

    private static List<HookedTarget> targets(XposedService service) {
        List<HookedTarget> targets = service.getRunningTargets();
        for (HookedTarget target : targets) {
            ProbeLog.i("CTRL target process=" + target.getProcessName() + " pid=" + target.getPid()
                    + " state=" + target.getState() + " loadedVersion=" + target.getLoadedVersionCode());
        }
        return targets;
    }
}
