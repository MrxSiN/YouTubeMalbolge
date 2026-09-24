package io.github.mrxsin.ytmalbolge.bindinglab;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.ClassDataList;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;

import java.lang.reflect.Modifier;
import java.util.List;

/** Build-only binding laboratory. Never included in the Xposed module. */
public final class BindingLabActivity extends Activity {
    private static final String TAG = "YtmBindingLab";

    static {
        System.loadLibrary("dexkit");
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String apk = getIntent().getStringExtra("apk");
        String className = getIntent().getStringExtra("class");
        String methodName = getIntent().getStringExtra("method");
        String search = getIntent().getStringExtra("search");
        if (apk == null || (search == null && (className == null || methodName == null))) {
            Log.e(TAG, "missing search input");
            finish();
            return;
        }
        new Thread(() -> {
            try (DexKitBridge bridge = DexKitBridge.create(apk)) {
                if ("settings-root".equals(search)) {
                    searchSettingsRoot(bridge);
                    return;
                }
                if ("sponsorblock-player".equals(search)) {
                    searchSponsorBlockPlayer(bridge);
                    return;
                }
                MethodMatcher matcher;
                if ("load-video-ads".equals(search)) {
                    matcher = MethodMatcher.create().usingStrings(
                            "TriggerBundle doesn't have the required metadata specified by the trigger ",
                            "Ping migration no associated ping bindings for activated trigger: ");
                } else if ("player-ad-layout".equals(search)) {
                    matcher = MethodMatcher.create().usingStrings(
                            "Bootstrapped layout construction resulted in non PlayerBytesLayout. PlayerAds count: ");
                } else if ("ad-attribution".equals(search)) {
                    matcher = MethodMatcher.create().usingNumbers(0x7f0b00bf);
                } else if ("litho-component".equals(search)) {
                    matcher = MethodMatcher.create().usingStrings(
                            "Element missing correct type extension", "Element missing type");
                } else {
                    matcher = MethodMatcher.create()
                            .declaredClass(className)
                            .name(methodName)
                            .paramTypes("int", "int")
                            .returnType("void")
                            .modifiers(Modifier.PROTECTED | Modifier.FINAL);
                }
                MethodDataList results = bridge.findMethod(FindMethod.create().matcher(matcher));
                Log.i(TAG, "count=" + results.size() + " dex=" + bridge.getDexNum());
                for (MethodData method : results) {
                    List<String> opcodes = method.getOpNames();
                    Log.i(TAG, "candidate=" + method.getDescriptor()
                            + " modifiers=" + method.getModifiers()
                            + " superclass=" + method.getDeclaredClass().getSuperClass().getDescriptor()
                            + " opcodeCount=" + opcodes.size()
                            + " tail=" + opcodes.subList(Math.max(0, opcodes.size() - 12), opcodes.size()));
                }
            } catch (Throwable error) {
                Log.e(TAG, "binding search failed", error);
            } finally {
                runOnUiThread(this::finish);
            }
        }, "binding-lab").start();
    }

    /** Morphe PlayerInit, Seek and PlayerControllerSetTimeReference fingerprints. */
    private static void searchSponsorBlockPlayer(DexKitBridge bridge) {
        MethodDataList init = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .usingStrings("playVideo called on player response with no videoStreamingData.")));
        Log.i(TAG, "player-init count=" + init.size());
        for (MethodData method : init) {
            String owner = method.getDeclaredClass().getDescriptor();
            MethodDataList constructors = bridge.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create().declaredClass(method.getDeclaredClass().getName()).name("<init>")));
            Log.i(TAG, "player-controller class=" + owner + " constructors=" + constructors.size());
            report("player-controller", constructors);
            report("player-seek", bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .declaredClass(method.getDeclaredClass().getName())
                    .usingStrings("currentPositionMs."))));
        }
        // Morphe VideoIdBackgroundPlayFingerprint: synchronized (L)V storing the response video ID.
        MethodDataList stage = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .addInvoke("Lakgh;->M()Ljava/lang/String;")
                .paramCount(1)
                .returnType("void")
                .modifiers(Modifier.PUBLIC | Modifier.FINAL | Modifier.SYNCHRONIZED)));
        report("video-stage", stage);
        MethodDataList progress = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .usingStrings("Media progress reported outside media playback: %s")));
        Log.i(TAG, "progress-reporter count=" + progress.size());
        for (MethodData method : progress) {
            for (MethodData invoked : method.getInvokes()) {
                if (invoked.isConstructor() && invoked.getParamTypeNames().size() == 9) {
                    Log.i(TAG, "playback-progress reporter=" + method.getDescriptor());
                    report("playback-progress", bridge.findMethod(FindMethod.create().matcher(
                            MethodMatcher.create().descriptor(invoked.getDescriptor()))));
                }
            }
        }
    }

    /** Root settings fragment (uses "yt_android_settings") and its PreferenceScreen adapter factory. */
    private static void searchSettingsRoot(DexKitBridge bridge) {
        ClassDataList roots = bridge.findClass(FindClass.create().matcher(ClassMatcher.create()
                .usingStrings("yt_android_settings")));
        Log.i(TAG, "settings-root classes=" + roots.size());
        for (ClassData root : roots) {
            report("settings-root", bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .declaredClass(root.getName())
                    .paramTypes("androidx.preference.PreferenceScreen"))));
        }
        // The screen builder clears the PreferenceScreen and re-inflates it after the adapter exists.
        report("settings-builder", bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .name("run")
                .paramCount(0)
                .addInvoke("Landroidx/preference/PreferenceGroup;->af()V")
                .addUsingNumber(0x7f180027))));
        String[][] members = {
                {"androidx.preference.Preference", "Q", "java.lang.CharSequence"},
                {"androidx.preference.Preference", "n", "java.lang.CharSequence"},
                {"androidx.preference.Preference", "M", "int"},
                {"androidx.preference.Preference", "L", "java.lang.String"},
                {"androidx.preference.PreferenceGroup", "ai", "androidx.preference.Preference"},
                {"androidx.preference.PreferenceGroup", "l", "java.lang.CharSequence"},
        };
        for (String[] member : members) {
            report("preference-api", bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .declaredClass(member[0]).name(member[1]).paramTypes(member[2]))));
        }
    }

    private static void report(String label, MethodDataList results) {
        Log.i(TAG, label + " count=" + results.size());
        for (MethodData method : results) {
            List<String> opcodes = method.getOpNames();
            Log.i(TAG, label + " candidate=" + method.getDescriptor()
                    + " modifiers=" + method.getModifiers()
                    + " superclass=" + method.getDeclaredClass().getSuperClass().getDescriptor()
                    + " opcodeCount=" + opcodes.size());
        }
    }
}
