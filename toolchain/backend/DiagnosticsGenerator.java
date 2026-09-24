package toolchain.backend;

import static toolchain.backend.GenerateModule.P;
import static toolchain.backend.GenerateModule.save;
import static toolchain.backend.GenerateModule.writer;

import java.io.IOException;
import java.nio.file.Path;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Emits the small, UID-checked bridge from the hooked process to manager diagnostics. */
final class DiagnosticsGenerator implements Opcodes {
    static final String STATUS = P + "DiagnosticStatus";
    private static final String RECEIVER = P + "DiagnosticReceiver";
    private static final String RETRY = P + "DiagnosticRetry";
    private static final String PREFS = "android/content/SharedPreferences";
    private static final String EDITOR = PREFS + "$Editor";
    private static final String BUNDLE = "android/os/Bundle";

    private DiagnosticsGenerator() {}

    static void generate(Path root, GenerateModule.Diagnostic diagnostic) throws IOException {
        ClassWriter w = writer(STATUS, "java/lang/Object");
        w.visitField(ACC_STATIC | ACC_VOLATILE, "pendingStatus", "Ljava/lang/String;", null, null).visitEnd();
        w.visitField(ACC_STATIC | ACC_VOLATILE, "pendingDetail", "Ljava/lang/String;", null, null).visitEnd();
        w.visitField(ACC_STATIC, "retries", "I", null, null).visitEnd();
        GenerateModule.ctor(w, "java/lang/Object");
        MethodVisitor m;

        // Hook-side publishing never interferes with YouTube startup if IPC is unavailable.
        m = w.visitMethod(ACC_PUBLIC | ACC_STATIC, "report", "(Ljava/lang/String;Ljava/lang/String;)V", null, null);
        m.visitCode(); Label start = new Label(), end = new Label(), fail = new Label(), noApp = new Label();
        m.visitTryCatchBlock(start, end, fail, "java/lang/Throwable");
        m.visitLabel(start);
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(PUTSTATIC, STATUS, "pendingStatus", "Ljava/lang/String;");
        m.visitVarInsn(ALOAD, 1); m.visitFieldInsn(PUTSTATIC, STATUS, "pendingDetail", "Ljava/lang/String;");
        m.visitMethodInsn(INVOKESTATIC, "android/app/ActivityThread", "currentApplication", "()Landroid/app/Application;", false);
        m.visitVarInsn(ASTORE, 2);
        m.visitVarInsn(ALOAD, 2); m.visitJumpInsn(IFNULL, noApp);
        m.visitTypeInsn(NEW, BUNDLE); m.visitInsn(DUP);
        m.visitMethodInsn(INVOKESPECIAL, BUNDLE, "<init>", "()V", false);
        m.visitVarInsn(ASTORE, 3);
        m.visitVarInsn(ALOAD, 3); m.visitLdcInsn("status"); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKEVIRTUAL, BUNDLE, "putString", "(Ljava/lang/String;Ljava/lang/String;)V", false);
        m.visitVarInsn(ALOAD, 3); m.visitLdcInsn("detail"); m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKEVIRTUAL, BUNDLE, "putString", "(Ljava/lang/String;Ljava/lang/String;)V", false);
        m.visitVarInsn(ALOAD, 2);
        m.visitTypeInsn(NEW, "android/content/Intent"); m.visitInsn(DUP);
        m.visitMethodInsn(INVOKESPECIAL, "android/content/Intent", "<init>", "()V", false);
        m.visitLdcInsn("io.github.mrxsin.ytmalbolge"); m.visitLdcInsn(RECEIVER.replace('/', '.'));
        m.visitMethodInsn(INVOKEVIRTUAL, "android/content/Intent", "setClassName",
                "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;", false);
        m.visitVarInsn(ALOAD, 3);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/content/Intent", "putExtras",
                "(L" + BUNDLE + ";)Landroid/content/Intent;", false);
        m.visitInsn(ACONST_NULL);
        m.visitMethodInsn(INVOKESTATIC, "android/app/BroadcastOptions", "makeBasic",
                "()Landroid/app/BroadcastOptions;", false);
        m.visitInsn(ICONST_1);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/app/BroadcastOptions", "setShareIdentityEnabled",
                "(Z)Landroid/app/BroadcastOptions;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/app/BroadcastOptions", "toBundle",
                "()L" + BUNDLE + ";", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/app/Application", "sendBroadcast",
                "(Landroid/content/Intent;Ljava/lang/String;L" + BUNDLE + ";)V", false);
        m.visitInsn(ACONST_NULL); m.visitFieldInsn(PUTSTATIC, STATUS, "pendingStatus", "Ljava/lang/String;");
        m.visitInsn(ICONST_0); m.visitFieldInsn(PUTSTATIC, STATUS, "retries", "I");
        m.visitLabel(end); m.visitInsn(RETURN);
        m.visitLabel(noApp);
        m.visitFieldInsn(GETSTATIC, STATUS, "retries", "I");
        m.visitIntInsn(BIPUSH, 8);
        Label noRetry = new Label(); m.visitJumpInsn(IF_ICMPGE, noRetry);
        m.visitFieldInsn(GETSTATIC, STATUS, "retries", "I"); m.visitInsn(ICONST_1); m.visitInsn(IADD);
        m.visitFieldInsn(PUTSTATIC, STATUS, "retries", "I");
        m.visitTypeInsn(NEW, "android/os/Handler"); m.visitInsn(DUP);
        m.visitMethodInsn(INVOKESTATIC, "android/os/Looper", "getMainLooper", "()Landroid/os/Looper;", false);
        m.visitMethodInsn(INVOKESPECIAL, "android/os/Handler", "<init>", "(Landroid/os/Looper;)V", false);
        m.visitTypeInsn(NEW, RETRY); m.visitInsn(DUP);
        m.visitMethodInsn(INVOKESPECIAL, RETRY, "<init>", "()V", false);
        m.visitLdcInsn(1000L);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/os/Handler", "postDelayed", "(Ljava/lang/Runnable;J)Z", false);
        m.visitInsn(POP);
        m.visitLabel(noRetry); m.visitInsn(RETURN);
        m.visitLabel(fail); m.visitVarInsn(ASTORE, 4);
        m.visitLdcInsn(GenerateModule.LOG_TAG); m.visitLdcInsn("diagnostic report failed"); m.visitVarInsn(ALOAD, 4);
        m.visitMethodInsn(INVOKESTATIC, "android/util/Log", "w", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I", false);
        m.visitInsn(POP); m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();

        // Manager-side snapshot, owned by the module app and never writable by UI code.
        m = w.visitMethod(ACC_PUBLIC | ACC_STATIC, "read", "(Landroid/content/Context;)L" + BUNDLE + ";", null, null);
        m.visitCode();
        m.visitVarInsn(ALOAD, 0); m.visitLdcInsn("hook_diagnostics"); m.visitInsn(ICONST_0);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/content/Context", "getSharedPreferences", "(Ljava/lang/String;I)L" + PREFS + ";", false);
        m.visitVarInsn(ASTORE, 1);
        m.visitTypeInsn(NEW, BUNDLE); m.visitInsn(DUP);
        m.visitMethodInsn(INVOKESPECIAL, BUNDLE, "<init>", "()V", false);
        m.visitVarInsn(ASTORE, 2);
        for (String key : new String[] {"status", "detail"}) {
            m.visitVarInsn(ALOAD, 2); m.visitLdcInsn(key); m.visitVarInsn(ALOAD, 1); m.visitLdcInsn(key);
            m.visitLdcInsn("");
            m.visitMethodInsn(INVOKEINTERFACE, PREFS, "getString", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", true);
            m.visitMethodInsn(INVOKEVIRTUAL, BUNDLE, "putString", "(Ljava/lang/String;Ljava/lang/String;)V", false);
        }
        m.visitVarInsn(ALOAD, 2); m.visitLdcInsn("time"); m.visitVarInsn(ALOAD, 1); m.visitLdcInsn("time"); m.visitInsn(LCONST_0);
        m.visitMethodInsn(INVOKEINTERFACE, PREFS, "getLong", "(Ljava/lang/String;J)J", true);
        m.visitMethodInsn(INVOKEVIRTUAL, BUNDLE, "putLong", "(Ljava/lang/String;J)V", false);
        m.visitVarInsn(ALOAD, 2); m.visitInsn(ARETURN); m.visitMaxs(0, 0); m.visitEnd();

        save(root, STATUS, w);
        retry(root);
        receiver(root, diagnostic.targetPackage());
    }

    private static void receiver(Path root, String targetPackage) throws IOException {
        ClassWriter w = writer(RECEIVER, "android/content/BroadcastReceiver");
        GenerateModule.ctor(w, "android/content/BroadcastReceiver");
        MethodVisitor m = w.visitMethod(ACC_PUBLIC, "onReceive",
                "(Landroid/content/Context;Landroid/content/Intent;)V", null, null);
        m.visitCode(); Label done = new Label();
        m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/content/Context", "getPackageManager",
                "()Landroid/content/pm/PackageManager;", false);
        m.visitLdcInsn(targetPackage); m.visitInsn(ICONST_0);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/content/pm/PackageManager", "getPackageUid",
                "(Ljava/lang/String;I)I", false);
        m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKEVIRTUAL, RECEIVER, "getSentFromUid", "()I", false);
        m.visitJumpInsn(IF_ICMPNE, done);
        m.visitVarInsn(ALOAD, 1); m.visitLdcInsn("hook_diagnostics"); m.visitInsn(ICONST_0);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/content/Context", "getSharedPreferences",
                "(Ljava/lang/String;I)L" + PREFS + ";", false);
        m.visitMethodInsn(INVOKEINTERFACE, PREFS, "edit", "()L" + EDITOR + ";", true);
        m.visitVarInsn(ASTORE, 3);
        for (String key : new String[] {"status", "detail"}) {
            m.visitVarInsn(ALOAD, 3); m.visitLdcInsn(key);
            m.visitVarInsn(ALOAD, 2); m.visitLdcInsn(key);
            m.visitMethodInsn(INVOKEVIRTUAL, "android/content/Intent", "getStringExtra",
                    "(Ljava/lang/String;)Ljava/lang/String;", false);
            m.visitMethodInsn(INVOKEINTERFACE, EDITOR, "putString",
                    "(Ljava/lang/String;Ljava/lang/String;)L" + EDITOR + ";", true);
            m.visitInsn(POP);
        }
        m.visitVarInsn(ALOAD, 3); m.visitLdcInsn("time");
        m.visitMethodInsn(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false);
        m.visitMethodInsn(INVOKEINTERFACE, EDITOR, "putLong", "(Ljava/lang/String;J)L" + EDITOR + ";", true);
        m.visitInsn(POP);
        m.visitVarInsn(ALOAD, 3);
        m.visitMethodInsn(INVOKEINTERFACE, EDITOR, "commit", "()Z", true); m.visitInsn(POP);
        m.visitLabel(done); m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();
        save(root, RECEIVER, w);
    }

    private static void retry(Path root) throws IOException {
        ClassWriter w = writer(RETRY, "java/lang/Object", "java/lang/Runnable");
        GenerateModule.ctor(w, "java/lang/Object");
        MethodVisitor m = w.visitMethod(ACC_PUBLIC, "run", "()V", null, null);
        m.visitCode(); Label done = new Label();
        m.visitFieldInsn(GETSTATIC, STATUS, "pendingStatus", "Ljava/lang/String;");
        m.visitJumpInsn(IFNULL, done);
        m.visitFieldInsn(GETSTATIC, STATUS, "pendingStatus", "Ljava/lang/String;");
        m.visitFieldInsn(GETSTATIC, STATUS, "pendingDetail", "Ljava/lang/String;");
        m.visitMethodInsn(INVOKESTATIC, STATUS, "report", "(Ljava/lang/String;Ljava/lang/String;)V", false);
        m.visitLabel(done); m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();
        save(root, RETRY, w);
    }

}
