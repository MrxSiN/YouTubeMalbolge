package io.github.mrxsin.ytmalbolge.bindinglab;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;

/** Generic interactive DexKit probe. Fingerprints are supplied as intent data. */
public final class BindingLabActivity extends Activity {
    private static final String TAG = "YtmBindingLab";

    static { System.loadLibrary("dexkit"); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String apk = getIntent().getStringExtra("apk");
        String owner = getIntent().getStringExtra("owner");
        String name = getIntent().getStringExtra("name");
        String result = getIntent().getStringExtra("return");
        String[] parameters = getIntent().getStringArrayExtra("parameters");
        String[] strings = getIntent().getStringArrayExtra("strings");
        if (apk == null) { Log.e(TAG, "missing apk"); finish(); return; }
        new Thread(() -> {
            try (DexKitBridge bridge = DexKitBridge.create(apk)) {
                MethodMatcher matcher = MethodMatcher.create();
                if (owner != null) matcher.declaredClass(owner);
                if (name != null) matcher.name(name);
                if (result != null) matcher.returnType(result);
                if (parameters != null) matcher.paramTypes(parameters);
                if (strings != null && strings.length != 0) matcher.usingStrings(strings);
                MethodDataList found = bridge.findMethod(FindMethod.create().matcher(matcher));
                Log.i(TAG, "count=" + found.size() + " dex=" + bridge.getDexNum());
                for (MethodData method : found) {
                    Log.i(TAG, "candidate=" + method.getDescriptor() + " modifiers="
                            + method.getModifiers() + " opcodes=" + method.getOpCodes().size());
                }
            } catch (Throwable error) {
                Log.e(TAG, "query failed", error);
            } finally {
                runOnUiThread(this::finish);
            }
        }, "binding-lab").start();
    }
}
