package io.github.mrxsin.ytmprobe;

import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.HookHandle;

/**
 * Owns the probe's physical hooks and reconciles them across generations by stable ID
 * (HR-03 replace, HR-05 add/remove, HR-07 partial failure).
 */
final class ProbeHooks {

    static final String TARGET_CLASS = "io.github.mrxsin.ytmprobe.target.ProbeTarget";
    private static final String ID_PREFIX = "ytmprobe.";

    private final XposedInterface xposed;
    private final int generation = BuildConfig.PROBE_GENERATION;
    private final List<String> wanted = Arrays.asList(BuildConfig.PROBE_HOOKS.split(","));

    ProbeHooks(XposedInterface xposed) {
        this.xposed = xposed;
    }

    /** First generation in a fresh process. */
    void installAll(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> target = Class.forName(TARGET_CLASS, false, loader);
        for (String name : wanted) {
            install(target, name);
        }
    }

    /** New generation after hot reload: replace same IDs, add new, unhook removed. */
    void reconcile(List<HookHandle> oldHandles) throws ReflectiveOperationException {
        Map<String, HookHandle> byId = new HashMap<>();
        Class<?> target = null;
        for (HookHandle handle : oldHandles) {
            byId.put(handle.getId(), handle);
            target = handle.getExecutable().getDeclaringClass();
            ProbeLog.i("OLD_HANDLE id=" + handle.getId() + " exec=" + handle.getExecutable().getName());
        }
        for (String name : wanted) {
            HookHandle old = byId.remove(ID_PREFIX + name);
            if (name.equals(BuildConfig.PROBE_FAIL)) {
                ProbeLog.i("INJECTED_FAILURE before " + name);
                throw new IllegalStateException("probe injected replacement failure: " + name);
            }
            if (old != null) {
                HookHandle replaced = old.replaceHook(hooker(name));
                ProbeLog.i("REPLACED id=" + replaced.getId() + " same=" + (replaced == old));
            } else if (target != null) {
                install(target, name);
            } else {
                ProbeLog.i("ADD_SKIPPED " + name + " (no old handle to locate target class)");
            }
        }
        for (HookHandle removed : byId.values()) {
            removed.unhook();
            ProbeLog.i("UNHOOKED id=" + removed.getId());
        }
    }

    private void install(Class<?> target, String name) throws NoSuchMethodException {
        Executable method = target.getDeclaredMethod(name.toLowerCase(), int.class);
        HookHandle handle = xposed.hook(method)
                .setId(ID_PREFIX + name)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(hooker(name));
        ProbeLog.i("INSTALLED id=" + handle.getId());
    }

    /** H1 adds G*1000, H2 adds G*1000+500; see ProbeTarget. */
    private XposedInterface.Hooker hooker(String name) {
        int delta = generation * 1000 + ("H2".equals(name) ? 500 : 0);
        return chain -> (Integer) chain.proceed() + delta;
    }
}
