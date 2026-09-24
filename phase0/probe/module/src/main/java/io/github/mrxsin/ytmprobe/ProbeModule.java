package io.github.mrxsin.ytmprobe;

import android.content.SharedPreferences;
import android.os.Bundle;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/**
 * Phase-0 API-102 characterization probe. Never shipped; hooks only the harmless probe
 * target app. Logs every lifecycle callback so HR-01..HR-10 can be read from logcat.
 */
public final class ProbeModule extends XposedModule {

    static final String PREFS_GROUP = "probe";
    private static final int GC_CHECKS = 15;

    private final AtomicBoolean installed = new AtomicBoolean(false);
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        ProbeLog.i("CB onModuleLoaded process=" + param.getProcessName()
                + " api=" + getApiVersion() + " framework=" + getFrameworkName()
                + " " + getFrameworkVersion() + " props=0x" + Long.toHexString(getFrameworkProperties()));
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        ProbeLog.i("CB onPackageLoaded package=" + param.getPackageName());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        ProbeLog.i("CB onPackageReady package=" + param.getPackageName());
        if (!"io.github.mrxsin.ytmprobe.target".equals(param.getPackageName()) || !installed.compareAndSet(false, true)) {
            return;
        }
        try {
            new ProbeHooks(this).installAll(param.getClassLoader());
        } catch (Throwable error) {
            ProbeLog.e("INSTALL_FAILED", error);
        }
        attachListener();
    }

    @Override
    public boolean onHotReloading(HotReloadingParam param) {
        ProbeLog.i("CB onHotReloading");
        if ("veto".equals(BuildConfig.PROBE_FAIL)) {
            ProbeLog.i("VETO reload");
            return false;
        }
        detachListener();
        Bundle extras = param.getExtras();
        extras.putInt("generation", BuildConfig.PROBE_GENERATION);
        param.setSavedInstanceState(new Object[] {
                BuildConfig.PROBE_GENERATION,
                new WeakReference<>(getClass().getClassLoader())
        });
        return true;
    }

    @Override
    public void onHotReloaded(HotReloadedParam param) {
        Object state = param.getSavedInstanceState();
        ProbeLog.i("CB onHotReloaded process=" + param.getProcessName()
                + " extrasGen=" + param.getExtras().getInt("generation", -1)
                + " oldHandles=" + param.getOldHookHandles().size());
        installed.set(true);
        try {
            new ProbeHooks(this).reconcile(param.getOldHookHandles());
        } catch (ReflectiveOperationException error) {
            ProbeLog.e("RECONCILE_FAILED", error);
        } finally {
            attachListener();
            if (state instanceof Object[] saved && saved.length == 2) {
                ProbeLog.i("SAVED_STATE fromGen=" + saved[0]);
                watchCollection((WeakReference<?>) saved[1]);
            } else {
                ProbeLog.i("SAVED_STATE unexpected=" + state);
            }
        }
    }

    private void attachListener() {
        try {
            prefs = getRemotePreferences(PREFS_GROUP);
            listener = (sp, key) -> ProbeLog.i("PREF key=" + key + " value=" + sp.getAll().get(key)
                    + " thread=" + Thread.currentThread().getName());
            prefs.registerOnSharedPreferenceChangeListener(listener);
            ProbeLog.i("LISTENER attached value=" + prefs.getAll().get("value"));
        } catch (Throwable error) {
            ProbeLog.e("LISTENER attach failed", error);
        }
    }

    private void detachListener() {
        if (prefs != null && listener != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(listener);
            ProbeLog.i("LISTENER detached");
        }
        listener = null;
    }

    /** HR-09: report whether the previous generation's ClassLoader becomes collectible. */
    private static void watchCollection(WeakReference<?> oldLoader) {
        Thread watcher = new Thread(() -> {
            for (int i = 1; i <= GC_CHECKS; i++) {
                Runtime.getRuntime().gc();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
                if (oldLoader.get() == null) {
                    ProbeLog.i("OLD_CLASSLOADER collected afterChecks=" + i);
                    return;
                }
            }
            ProbeLog.i("OLD_CLASSLOADER retained afterChecks=" + GC_CHECKS);
        }, "probe-gc-watch");
        watcher.setDaemon(true);
        watcher.start();
    }
}
