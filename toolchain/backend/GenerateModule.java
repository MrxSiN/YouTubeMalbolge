package toolchain.backend;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Compiles a validated Logical Module Plan to one Xposed entry and cold hook ownership. */
public final class GenerateModule implements Opcodes {
    static final String P = "io/github/mrxsin/ytmalbolge/generated/";
    private static final String ENTRY = P + "Entry";
    static final String SNAP = P + "ConfigSnapshot";
    private static final String LISTENER = P + "ConfigListener";
    private static final String CTRL = P + "HookController";
    private static final String DIAG = DiagnosticsGenerator.STATUS;
    private static final String IDENTITY = P + "TargetIdentity";
    private static final String SEEK = P + "SeekPort";
    private static final String STORE = P + "SegmentStore";
    private static final String FETCH = P + "SegmentFetch";
    private static final String LITHO = P + "LithoAdPort";
    private static final String SHORTS = ShortsFeedGenerator.NAME;
    private static final String XPOSED = "io/github/libxposed/api/XposedModule";
    private static final String PARAM = "io/github/libxposed/api/XposedModuleInterface$PackageReadyParam";
    static final String CHAIN = "io/github/libxposed/api/XposedInterface$Chain";
    private static final String HOOKER = "io/github/libxposed/api/XposedInterface$Hooker";
    private static final String BUILDER = "io/github/libxposed/api/XposedInterface$HookBuilder";
    private static final String HANDLE = "io/github/libxposed/api/XposedInterface$HookHandle";
    private static final String MODE = "io/github/libxposed/api/XposedInterface$ExceptionMode";
    private static final String PREFS = "android/content/SharedPreferences";
    private static final String PREF_LISTENER = PREFS + "$OnSharedPreferenceChangeListener";
    static final String LOG_TAG = "YtmMalbolge";
    private static final String CLASS_FOR_NAME = "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;";
    /** Seek targets closer than this to a segment end are left to normal playback. */
    private static final int SEGMENT_END_MARGIN_MS = 250;
    private static final int MAX_SEGMENT_RESPONSE_BYTES = 1 << 20;

    record Config(String group, String key, boolean fallback) {}
    record Member(String kind, String owner, String name, String descriptor, int access,
                          String superclass) {
        boolean constructor() { return kind.equals("CONSTRUCTOR"); }
    }
    record Hook(Member member, String handler, int configIndex, String hookId, List<String> arguments) {}
    private record Seek(Member member, String constant) {}
    private record Category(String name, int configIndex) {}
    private record Segments(String origin, int prefixLength, int connectMs, int readMs, String query,
                            String actionType, List<Category> categories) {}
    record Diagnostic(String id, String transport, String failureMode, String targetPackage,
                      String screenTitle, String copyLabel) {}
    private record Plan(String hash, boolean active, List<Config> configs, List<Hook> hooks, Seek seek,
                        Segments segments, SettingsGenerator.Settings settings, Diagnostic diagnostic) {}

    /** One generated Effect handler body; control falls through to {@code chain.proceed()}. */
    interface Handler { void emit(MethodVisitor m, Hook hook); }

    private static final Map<String, Handler> HANDLERS = Map.of(
            "hide_view", GenerateModule::hideView,
            "filter_litho_ads", GenerateModule::filterLithoAds,
            "skip_void", GenerateModule::skipVoid,
            "capture_receiver", GenerateModule::captureReceiver,
            "observe_video_id", GenerateModule::observeVideoId,
            "filter_shorts_ads", GenerateModule::filterShortsAds,
            "inject_settings_entry", SettingsGenerator::injectEntry,
            "skip_segments", GenerateModule::skipSegments);

    private GenerateModule() {}

    static ClassWriter writer(String name, String parent, String... interfaces) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String a, String b) { return "java/lang/Object"; }
        };
        w.visit(V1_8, ACC_PUBLIC | ACC_FINAL, name, null, parent, interfaces);
        return w;
    }
    static void save(Path root, String name, ClassWriter w) throws IOException {
        w.visitEnd();
        Path file = root.resolve(name + ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, w.toByteArray());
    }
    static void ctor(ClassWriter w, String parent) {
        MethodVisitor m = w.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        m.visitCode(); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKESPECIAL, parent, "<init>", "()V", false);
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();
    }
    static void integer(MethodVisitor m, int n) {
        if (n >= -1 && n <= 5) m.visitInsn(ICONST_0 + n);
        else if (n >= Byte.MIN_VALUE && n <= Byte.MAX_VALUE) m.visitIntInsn(BIPUSH, n);
        else if (n >= Short.MIN_VALUE && n <= Short.MAX_VALUE) m.visitIntInsn(SIPUSH, n);
        else m.visitLdcInsn(n);
    }
    static void log(MethodVisitor m, String level, String message) {
        m.visitLdcInsn(LOG_TAG); m.visitLdcInsn(message);
        m.visitMethodInsn(INVOKESTATIC, "android/util/Log", level, "(Ljava/lang/String;Ljava/lang/String;)I", false);
        m.visitInsn(POP);
    }
    static void throwState(MethodVisitor m, String message) {
        m.visitTypeInsn(NEW, "java/lang/IllegalStateException"); m.visitInsn(DUP);
        m.visitLdcInsn(message);
        m.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false);
        m.visitInsn(ATHROW);
    }
    /** Pushes {@code Class.forName(name, false, loader)} with the loader in {@code loaderLocal}. */
    static void loadClass(MethodVisitor m, String name, int loaderLocal) {
        m.visitLdcInsn(name); m.visitInsn(ICONST_0); m.visitVarInsn(ALOAD, loaderLocal);
        m.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", CLASS_FOR_NAME, false);
    }
    /** Pushes the {@code Class} of one descriptor type, loading target classes from the loader. */
    static void typeClass(MethodVisitor m, Type type, int loaderLocal) {
        if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
            loadClass(m, type.getSort() == Type.ARRAY
                    ? type.getDescriptor().replace('/', '.') : type.getClassName(), loaderLocal);
            return;
        }
        String wrapper = switch (type.getSort()) {
            case Type.VOID -> "java/lang/Void";
            case Type.INT -> "java/lang/Integer";
            case Type.BOOLEAN -> "java/lang/Boolean";
            case Type.LONG -> "java/lang/Long";
            default -> throw new IllegalArgumentException("unsupported member type");
        };
        m.visitFieldInsn(GETSTATIC, wrapper, "TYPE", "Ljava/lang/Class;");
    }
    /** Pushes the {@code Class[]} of a descriptor's parameter types. */
    static void parameterClasses(MethodVisitor m, String descriptor, int loaderLocal) {
        Type[] params = Type.getArgumentTypes(descriptor);
        integer(m, params.length); m.visitTypeInsn(ANEWARRAY, "java/lang/Class");
        for (int p = 0; p < params.length; p++) {
            m.visitInsn(DUP); integer(m, p);
            typeClass(m, params[p], loaderLocal);
            m.visitInsn(AASTORE);
        }
    }
    /**
     * Resolves an exact bound member into {@code targetLocal} (class in {@code classLocal})
     * and throws unless declaring class, superclass, signature and access match.
     */
    static void resolveMember(MethodVisitor m, Member b, int loaderLocal,
                                      int classLocal, int targetLocal, String failure) {
        Label shapeFail = new Label(), next = new Label();
        loadClass(m, b.owner(), loaderLocal); m.visitVarInsn(ASTORE, classLocal);
        m.visitVarInsn(ALOAD, classLocal);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getSuperclass", "()Ljava/lang/Class;", false);
        // Do not emit a superclass class literal here. The generated module
        // loader cannot resolve target-only obfuscated classes; compare the
        // runtime name using the target class loader instead.
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
        m.visitLdcInsn(b.superclass().replace('/', '.'));
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        m.visitJumpInsn(IFEQ, shapeFail);
        m.visitVarInsn(ALOAD, classLocal);
        if (b.constructor()) {
            parameterClasses(m, b.descriptor(), loaderLocal);
            m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredConstructor",
                    "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;", false);
            m.visitVarInsn(ASTORE, targetLocal);
            m.visitVarInsn(ALOAD, targetLocal);
            m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Constructor", "getModifiers", "()I", false);
        } else {
            m.visitLdcInsn(b.name());
            parameterClasses(m, b.descriptor(), loaderLocal);
            m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredMethod",
                    "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
            m.visitVarInsn(ASTORE, targetLocal);
            m.visitVarInsn(ALOAD, targetLocal); m.visitTypeInsn(CHECKCAST, "java/lang/reflect/Method");
            m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "getReturnType", "()Ljava/lang/Class;", false);
            typeClass(m, Type.getReturnType(b.descriptor()), loaderLocal);
            m.visitJumpInsn(IF_ACMPNE, shapeFail);
            m.visitVarInsn(ALOAD, targetLocal); m.visitTypeInsn(CHECKCAST, "java/lang/reflect/Method");
            m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "getModifiers", "()I", false);
        }
        integer(m, 29); m.visitInsn(IAND); integer(m, b.access());
        m.visitJumpInsn(IF_ICMPEQ, next);
        m.visitLabel(shapeFail); throwState(m, failure);
        m.visitLabel(next);
    }

    // region Configuration

    private static void snapshot(Path root, int count) throws IOException {
        ClassWriter w = writer(SNAP, "java/lang/Object");
        w.visitField(ACC_PRIVATE | ACC_STATIC | ACC_VOLATILE, "values", "[Z", null, null).visitEnd();
        MethodVisitor m = w.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        m.visitCode(); integer(m, count); m.visitIntInsn(NEWARRAY, T_BOOLEAN);
        m.visitFieldInsn(PUTSTATIC, SNAP, "values", "[Z");
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();
        m = w.visitMethod(ACC_PUBLIC | ACC_STATIC, "enabled", "(I)Z", null, null);
        m.visitCode(); m.visitFieldInsn(GETSTATIC, SNAP, "values", "[Z");
        m.visitVarInsn(ILOAD, 0); m.visitInsn(BALOAD); m.visitInsn(IRETURN);
        m.visitMaxs(0, 0); m.visitEnd();
        m = w.visitMethod(ACC_PUBLIC | ACC_STATIC | ACC_SYNCHRONIZED, "publish", "(IZ)V", null, null);
        m.visitCode(); m.visitFieldInsn(GETSTATIC, SNAP, "values", "[Z");
        m.visitFieldInsn(GETSTATIC, SNAP, "values", "[Z"); m.visitInsn(ARRAYLENGTH);
        m.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "copyOf", "([ZI)[Z", false);
        m.visitVarInsn(ASTORE, 2); m.visitVarInsn(ALOAD, 2);
        m.visitVarInsn(ILOAD, 0); m.visitVarInsn(ILOAD, 1); m.visitInsn(BASTORE);
        m.visitVarInsn(ALOAD, 2); m.visitFieldInsn(PUTSTATIC, SNAP, "values", "[Z");
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();
        m = w.visitMethod(ACC_PUBLIC | ACC_STATIC | ACC_SYNCHRONIZED, "disableAll", "()V", null, null);
        m.visitCode(); integer(m, count); m.visitIntInsn(NEWARRAY, T_BOOLEAN);
        m.visitFieldInsn(PUTSTATIC, SNAP, "values", "[Z");
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();
        save(root, SNAP, w);
    }
    private static void listener(Path root, List<Config> configs) throws IOException {
        ClassWriter w = writer(LISTENER, "java/lang/Object", PREF_LISTENER);
        ctor(w, "java/lang/Object");
        MethodVisitor m = w.visitMethod(ACC_PUBLIC, "onSharedPreferenceChanged",
                "(L" + PREFS + ";Ljava/lang/String;)V", null, null);
        m.visitCode();
        for (int i = 0; i < configs.size(); i++) {
            Config c = configs.get(i); Label next = new Label();
            m.visitLdcInsn(c.key()); m.visitVarInsn(ALOAD, 2);
            m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
            m.visitJumpInsn(IFEQ, next);
            integer(m, i); m.visitVarInsn(ALOAD, 1); m.visitLdcInsn(c.key());
            m.visitInsn(c.fallback() ? ICONST_1 : ICONST_0);
            m.visitMethodInsn(INVOKEINTERFACE, PREFS, "getBoolean", "(Ljava/lang/String;Z)Z", true);
            m.visitMethodInsn(INVOKESTATIC, SNAP, "publish", "(IZ)V", false);
            m.visitInsn(RETURN); m.visitLabel(next);
        }
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();
        save(root, LISTENER, w);
    }

    // endregion

    // region Effect handlers

    private static void hideView(MethodVisitor m, Hook hook) {
        m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKEINTERFACE, CHAIN, "getThisObject", "()Ljava/lang/Object;", true);
        m.visitTypeInsn(CHECKCAST, "android/view/View");
        integer(m, 8);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/view/View", "setVisibility", "(I)V", false);
    }
    private static void filterLithoAds(MethodVisitor m, Hook hook) {
        Label noMatch = new Label();
        m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKESTATIC, LITHO, "filter", "(L" + CHAIN + ";)Ljava/lang/Object;", false);
        m.visitInsn(DUP);
        m.visitJumpInsn(IFNULL, noMatch);
        m.visitInsn(ARETURN);
        m.visitLabel(noMatch);
        m.visitInsn(POP);
    }
    private static void skipVoid(MethodVisitor m, Hook hook) {
        m.visitInsn(ACONST_NULL); m.visitInsn(ARETURN);
    }

    private static void filterShortsAds(MethodVisitor m, Hook hook) {
        m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKESTATIC, SHORTS, "filter", "(L" + CHAIN + ";)Ljava/lang/Object;", false);
        m.visitInsn(ARETURN);
    }
    private static void captureReceiver(MethodVisitor m, Hook hook) {
        m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKEINTERFACE, CHAIN, "proceed", "()Ljava/lang/Object;", true);
        m.visitVarInsn(ASTORE, 2);
        m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKEINTERFACE, CHAIN, "getThisObject", "()Ljava/lang/Object;", true);
        m.visitFieldInsn(PUTSTATIC, SEEK, "receiver", "Ljava/lang/Object;");
        m.visitVarInsn(ALOAD, 2); m.visitInsn(ARETURN);
    }
    private static void observeVideoId(MethodVisitor m, Hook hook) {
        m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKEINTERFACE, CHAIN, "proceed", "()Ljava/lang/Object;", true);
        m.visitVarInsn(ASTORE, 2);
        m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKEINTERFACE, CHAIN, "getThisObject", "()Ljava/lang/Object;", true);
        m.visitMethodInsn(INVOKESTATIC, STORE, "onStage", "(Ljava/lang/Object;)V", false);
        m.visitVarInsn(ALOAD, 2); m.visitInsn(ARETURN);
    }
    /** Cold binding of the private field in which the observed member stores the video ID. */
    private static void bindVideoField(MethodVisitor m, Hook hook, int classLocal) {
        m.visitVarInsn(ALOAD, classLocal); m.visitLdcInsn(hook.arguments().get(0));
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredField",
                "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false);
        m.visitInsn(DUP);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "getType", "()Ljava/lang/Class;", false);
        m.visitLdcInsn(Type.getType("Ljava/lang/String;"));
        Label typed = new Label();
        m.visitJumpInsn(IF_ACMPEQ, typed);
        throwState(m, "binding shape mismatch: " + hook.hookId());
        m.visitLabel(typed);
        m.visitInsn(DUP); m.visitInsn(ICONST_1);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "setAccessible", "(Z)V", false);
        m.visitFieldInsn(PUTSTATIC, STORE, "videoField", "Ljava/lang/reflect/Field;");
    }
    private static void skipSegments(MethodVisitor m, Hook hook) {
        m.visitVarInsn(ALOAD, 1); integer(m, Integer.parseInt(hook.arguments().get(0)));
        m.visitMethodInsn(INVOKEINTERFACE, CHAIN, "getArg", "(I)Ljava/lang/Object;", true);
        m.visitTypeInsn(CHECKCAST, "java/lang/Long");
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
        m.visitMethodInsn(INVOKESTATIC, STORE, "onProgress", "(J)V", false);
    }
    private static void hooker(Path root, int index, Hook hook) throws IOException {
        Handler handler = HANDLERS.get(hook.handler());
        if (handler == null) throw new IllegalArgumentException("unsupported handler " + hook.handler());
        String name = P + "EffectHooker" + index;
        ClassWriter w = writer(name, "java/lang/Object", HOOKER);
        ctor(w, "java/lang/Object");
        MethodVisitor m = w.visitMethod(ACC_PUBLIC, "intercept", "(L" + CHAIN + ";)Ljava/lang/Object;",
                null, new String[] {"java/lang/Throwable"});
        m.visitCode(); Label proceed = new Label();
        if (hook.configIndex() >= 0) {
            integer(m, hook.configIndex());
            m.visitMethodInsn(INVOKESTATIC, SNAP, "enabled", "(I)Z", false);
            m.visitJumpInsn(IFEQ, proceed);
        }
        handler.emit(m, hook);
        m.visitLabel(proceed); m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKEINTERFACE, CHAIN, "proceed", "()Ljava/lang/Object;", true);
        m.visitInsn(ARETURN); m.visitMaxs(0, 0); m.visitEnd();
        save(root, name, w);
    }

    // endregion

    // region Segment skipping runtime

    /** Holds the bound player receiver and seek member; invoked only on the player's thread. */
    private static void seekPort(Path root, Seek seek) throws IOException {
        Member b = seek.member();
        Type[] params = Type.getArgumentTypes(b.descriptor());
        if (b.constructor() || params.length != 2 || params[0].getSort() != Type.LONG
                || params[1].getSort() != Type.OBJECT || !b.descriptor().endsWith(")Z")) {
            throw new IllegalArgumentException("unsupported seek shape");
        }
        ClassWriter w = writer(SEEK, "java/lang/Object");
        w.visitField(ACC_STATIC | ACC_VOLATILE, "receiver", "Ljava/lang/Object;", null, null).visitEnd();
        w.visitField(ACC_PRIVATE | ACC_STATIC | ACC_VOLATILE, "method", "Ljava/lang/reflect/Method;", null, null).visitEnd();
        w.visitField(ACC_PRIVATE | ACC_STATIC | ACC_VOLATILE, "source", "Ljava/lang/Object;", null, null).visitEnd();

        MethodVisitor m = w.visitMethod(ACC_STATIC, "bind", "(Ljava/lang/ClassLoader;)V", null,
                new String[] {"java/lang/Throwable"});
        m.visitCode();
        resolveMember(m, b, 0, 1, 2, "binding shape mismatch: seek");
        loadClass(m, params[1].getClassName(), 0); m.visitLdcInsn(seek.constant());
        m.visitMethodInsn(INVOKESTATIC, "java/lang/Enum", "valueOf",
                "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", false);
        m.visitFieldInsn(PUTSTATIC, SEEK, "source", "Ljava/lang/Object;");
        m.visitVarInsn(ALOAD, 2); m.visitTypeInsn(CHECKCAST, "java/lang/reflect/Method");
        m.visitFieldInsn(PUTSTATIC, SEEK, "method", "Ljava/lang/reflect/Method;");
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();

        m = w.visitMethod(ACC_STATIC, "seek", "(J)Z", null, null);
        m.visitCode();
        Label start = new Label(), end = new Label(), fail = new Label(), missing = new Label();
        m.visitTryCatchBlock(start, end, fail, "java/lang/Throwable");
        m.visitFieldInsn(GETSTATIC, SEEK, "receiver", "Ljava/lang/Object;"); m.visitVarInsn(ASTORE, 2);
        m.visitVarInsn(ALOAD, 2); m.visitJumpInsn(IFNULL, missing);
        m.visitLabel(start);
        m.visitFieldInsn(GETSTATIC, SEEK, "method", "Ljava/lang/reflect/Method;");
        m.visitVarInsn(ALOAD, 2);
        integer(m, 2); m.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        m.visitInsn(DUP); integer(m, 0); m.visitVarInsn(LLOAD, 0);
        m.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        m.visitInsn(AASTORE);
        m.visitInsn(DUP); integer(m, 1); m.visitFieldInsn(GETSTATIC, SEEK, "source", "Ljava/lang/Object;");
        m.visitInsn(AASTORE);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "invoke",
                "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
        m.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
        m.visitLabel(end); m.visitInsn(IRETURN);
        m.visitLabel(fail); m.visitVarInsn(ASTORE, 3);
        m.visitLdcInsn(LOG_TAG); m.visitLdcInsn("segment seek failed"); m.visitVarInsn(ALOAD, 3);
        m.visitMethodInsn(INVOKESTATIC, "android/util/Log", "w",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I", false);
        m.visitInsn(POP);
        m.visitLabel(missing); m.visitInsn(ICONST_0); m.visitInsn(IRETURN);
        m.visitMaxs(0, 0); m.visitEnd();
        save(root, SEEK, w);
    }

    /**
     * Owns the current video's immutable segment table: {@code [start, end, configIndex, skipped]}
     * per segment. Only the player thread reads it and marks segments skipped.
     */
    private static void segmentStore(Path root) throws IOException {
        ClassWriter w = writer(STORE, "java/lang/Object");
        w.visitField(ACC_PRIVATE | ACC_STATIC | ACC_VOLATILE, "videoId", "Ljava/lang/String;", null, null).visitEnd();
        w.visitField(ACC_PRIVATE | ACC_STATIC | ACC_VOLATILE, "segments", "[J", null, null).visitEnd();
        MethodVisitor m = w.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        m.visitCode(); m.visitInsn(ICONST_0); m.visitIntInsn(NEWARRAY, T_LONG);
        m.visitFieldInsn(PUTSTATIC, STORE, "segments", "[J");
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();

        w.visitField(ACC_STATIC | ACC_VOLATILE, "videoField", "Ljava/lang/reflect/Field;", null, null).visitEnd();

        // onStage(Object owner): reads the bound video ID field after the observed member ran.
        m = w.visitMethod(ACC_STATIC, "onStage", "(Ljava/lang/Object;)V", null, new String[] {"java/lang/Throwable"});
        m.visitCode();
        Label same = new Label();
        m.visitFieldInsn(GETSTATIC, STORE, "videoField", "Ljava/lang/reflect/Field;"); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
        m.visitTypeInsn(CHECKCAST, "java/lang/String"); m.visitVarInsn(ASTORE, 1);
        m.visitVarInsn(ALOAD, 1); m.visitJumpInsn(IFNULL, same);
        m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
        integer(m, 11); m.visitJumpInsn(IF_ICMPNE, same);
        m.visitVarInsn(ALOAD, 1); m.visitFieldInsn(GETSTATIC, STORE, "videoId", "Ljava/lang/String;");
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        m.visitJumpInsn(IFNE, same);
        m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKESTATIC, STORE, "request", "(Ljava/lang/String;)V", false);
        m.visitLabel(same); m.visitInsn(RETURN);
        m.visitMaxs(0, 0); m.visitEnd();

        // onProgress(long position): locals 0-1 position, 2 table, 3 index. No allocation.
        m = w.visitMethod(ACC_STATIC, "onProgress", "(J)V", null, null);
        m.visitCode();
        Label loop = new Label(), next = new Label(), done = new Label();
        m.visitFieldInsn(GETSTATIC, STORE, "segments", "[J"); m.visitVarInsn(ASTORE, 2);
        m.visitInsn(ICONST_0); m.visitVarInsn(ISTORE, 3);
        m.visitLabel(loop);
        m.visitVarInsn(ILOAD, 3); m.visitVarInsn(ALOAD, 2); m.visitInsn(ARRAYLENGTH);
        m.visitJumpInsn(IF_ICMPGE, done);
        // skipped flag
        m.visitVarInsn(ALOAD, 2); m.visitVarInsn(ILOAD, 3); m.visitInsn(ICONST_3); m.visitInsn(IADD);
        m.visitInsn(LALOAD); m.visitInsn(LCONST_0); m.visitInsn(LCMP); m.visitJumpInsn(IFNE, next);
        // position < start
        m.visitVarInsn(LLOAD, 0); m.visitVarInsn(ALOAD, 2); m.visitVarInsn(ILOAD, 3); m.visitInsn(LALOAD);
        m.visitInsn(LCMP); m.visitJumpInsn(IFLT, next);
        // position + margin >= end
        m.visitVarInsn(LLOAD, 0); m.visitLdcInsn((long) SEGMENT_END_MARGIN_MS); m.visitInsn(LADD);
        m.visitVarInsn(ALOAD, 2); m.visitVarInsn(ILOAD, 3); m.visitInsn(ICONST_1); m.visitInsn(IADD);
        m.visitInsn(LALOAD); m.visitInsn(LCMP); m.visitJumpInsn(IFGE, next);
        // category enabled
        m.visitVarInsn(ALOAD, 2); m.visitVarInsn(ILOAD, 3); m.visitInsn(ICONST_2); m.visitInsn(IADD);
        m.visitInsn(LALOAD); m.visitInsn(L2I);
        m.visitMethodInsn(INVOKESTATIC, SNAP, "enabled", "(I)Z", false);
        m.visitJumpInsn(IFEQ, next);
        m.visitVarInsn(ALOAD, 2); m.visitVarInsn(ILOAD, 3); m.visitInsn(ICONST_3); m.visitInsn(IADD);
        m.visitInsn(LCONST_1); m.visitInsn(LASTORE);
        m.visitVarInsn(ALOAD, 2); m.visitVarInsn(ILOAD, 3); m.visitInsn(ICONST_1); m.visitInsn(IADD);
        m.visitInsn(LALOAD);
        m.visitMethodInsn(INVOKESTATIC, SEEK, "seek", "(J)Z", false);
        m.visitJumpInsn(IFEQ, done);
        log(m, "i", "segment skipped");
        m.visitInsn(RETURN);
        m.visitLabel(next); m.visitIincInsn(3, 4); m.visitJumpInsn(GOTO, loop);
        m.visitLabel(done); m.visitInsn(RETURN);
        m.visitMaxs(0, 0); m.visitEnd();

        m = w.visitMethod(ACC_PRIVATE | ACC_STATIC | ACC_SYNCHRONIZED, "request", "(Ljava/lang/String;)V", null, null);
        m.visitCode();
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(PUTSTATIC, STORE, "videoId", "Ljava/lang/String;");
        m.visitInsn(ICONST_0); m.visitIntInsn(NEWARRAY, T_LONG);
        m.visitFieldInsn(PUTSTATIC, STORE, "segments", "[J");
        m.visitTypeInsn(NEW, "java/lang/Thread"); m.visitInsn(DUP);
        m.visitTypeInsn(NEW, FETCH); m.visitInsn(DUP); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKESPECIAL, FETCH, "<init>", "(Ljava/lang/String;)V", false);
        m.visitLdcInsn("ytm-segments");
        m.visitMethodInsn(INVOKESPECIAL, "java/lang/Thread", "<init>",
                "(Ljava/lang/Runnable;Ljava/lang/String;)V", false);
        m.visitInsn(DUP); m.visitInsn(ICONST_1);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Thread", "setDaemon", "(Z)V", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Thread", "start", "()V", false);
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();

        m = w.visitMethod(ACC_STATIC | ACC_SYNCHRONIZED, "publish", "(Ljava/lang/String;[J)V", null, null);
        m.visitCode(); Label stale = new Label();
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETSTATIC, STORE, "videoId", "Ljava/lang/String;");
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        m.visitJumpInsn(IFEQ, stale);
        m.visitVarInsn(ALOAD, 1); m.visitFieldInsn(PUTSTATIC, STORE, "segments", "[J");
        m.visitLabel(stale); m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();
        save(root, STORE, w);
    }

    /** One background request for one video; publishes only if that video is still current. */
    private static void segmentFetch(Path root, Segments s) throws IOException {
        if (s.prefixLength() < 4 || s.prefixLength() > 32 || s.prefixLength() % 2 != 0) {
            throw new IllegalArgumentException("hash prefix length");
        }
        ClassWriter w = writer(FETCH, "java/lang/Object", "java/lang/Runnable");
        w.visitField(ACC_PRIVATE | ACC_FINAL, "id", "Ljava/lang/String;", null, null).visitEnd();
        MethodVisitor m = w.visitMethod(0, "<init>", "(Ljava/lang/String;)V", null, null);
        m.visitCode(); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 1);
        m.visitFieldInsn(PUTFIELD, FETCH, "id", "Ljava/lang/String;");
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();

        m = w.visitMethod(ACC_PRIVATE | ACC_STATIC, "category", "(Ljava/lang/String;)I", null, null);
        m.visitCode();
        for (Category c : s.categories()) {
            Label next = new Label();
            m.visitLdcInsn(c.name()); m.visitVarInsn(ALOAD, 0);
            m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
            m.visitJumpInsn(IFEQ, next); integer(m, c.configIndex()); m.visitInsn(IRETURN);
            m.visitLabel(next);
        }
        m.visitInsn(ICONST_M1); m.visitInsn(IRETURN); m.visitMaxs(0, 0); m.visitEnd();

        // Locals: 0 this, 1 id, 2 hash, 3 url, 4 i, 5 byte, 6 connection, 7 in, 8 out, 9 buffer,
        // 10 read, 11 videos, 12 v, 13 video, 14 list, 15 table, 16 k, 17 j, 18 segment,
        // 19 configIndex, 20 bounds, 21 error.
        m = w.visitMethod(ACC_PUBLIC, "run", "()V", null, null);
        m.visitCode();
        Label start = new Label(), end = new Label(), fail = new Label(), exit = new Label();
        m.visitTryCatchBlock(start, end, fail, "java/lang/Throwable");
        m.visitLabel(start);
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, FETCH, "id", "Ljava/lang/String;");
        m.visitVarInsn(ASTORE, 1);
        m.visitLdcInsn("SHA-256");
        m.visitMethodInsn(INVOKESTATIC, "java/security/MessageDigest", "getInstance",
                "(Ljava/lang/String;)Ljava/security/MessageDigest;", false);
        m.visitVarInsn(ALOAD, 1);
        m.visitFieldInsn(GETSTATIC, "java/nio/charset/StandardCharsets", "UTF_8", "Ljava/nio/charset/Charset;");
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "getBytes", "(Ljava/nio/charset/Charset;)[B", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/security/MessageDigest", "digest", "([B)[B", false);
        m.visitVarInsn(ASTORE, 2);
        m.visitTypeInsn(NEW, "java/lang/StringBuilder"); m.visitInsn(DUP);
        m.visitLdcInsn(s.origin() + "/api/skipSegments/");
        m.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false);
        m.visitVarInsn(ASTORE, 3);
        Label hexLoop = new Label(), hexDone = new Label();
        m.visitInsn(ICONST_0); m.visitVarInsn(ISTORE, 4);
        m.visitLabel(hexLoop);
        m.visitVarInsn(ILOAD, 4); integer(m, s.prefixLength() / 2); m.visitJumpInsn(IF_ICMPGE, hexDone);
        m.visitVarInsn(ALOAD, 2); m.visitVarInsn(ILOAD, 4); m.visitInsn(BALOAD);
        integer(m, 255); m.visitInsn(IAND); m.visitVarInsn(ISTORE, 5);
        for (boolean high : new boolean[] {true, false}) {
            m.visitVarInsn(ALOAD, 3); m.visitVarInsn(ILOAD, 5);
            if (high) { m.visitInsn(ICONST_4); m.visitInsn(ISHR); } else { integer(m, 15); m.visitInsn(IAND); }
            integer(m, 16);
            m.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "forDigit", "(II)C", false);
            m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                    "(C)Ljava/lang/StringBuilder;", false);
            m.visitInsn(POP);
        }
        m.visitIincInsn(4, 1); m.visitJumpInsn(GOTO, hexLoop);
        m.visitLabel(hexDone);
        m.visitVarInsn(ALOAD, 3); m.visitLdcInsn(s.query());
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        m.visitInsn(POP);
        m.visitTypeInsn(NEW, "java/net/URL"); m.visitInsn(DUP); m.visitVarInsn(ALOAD, 3);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
        m.visitMethodInsn(INVOKESPECIAL, "java/net/URL", "<init>", "(Ljava/lang/String;)V", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/net/URL", "openConnection", "()Ljava/net/URLConnection;", false);
        m.visitTypeInsn(CHECKCAST, "java/net/HttpURLConnection"); m.visitVarInsn(ASTORE, 6);
        m.visitVarInsn(ALOAD, 6); integer(m, s.connectMs());
        m.visitMethodInsn(INVOKEVIRTUAL, "java/net/HttpURLConnection", "setConnectTimeout", "(I)V", false);
        m.visitVarInsn(ALOAD, 6); integer(m, s.readMs());
        m.visitMethodInsn(INVOKEVIRTUAL, "java/net/HttpURLConnection", "setReadTimeout", "(I)V", false);
        m.visitVarInsn(ALOAD, 6); m.visitInsn(ICONST_0);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/net/HttpURLConnection", "setUseCaches", "(Z)V", false);
        Label ok = new Label();
        m.visitVarInsn(ALOAD, 6);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/net/HttpURLConnection", "getResponseCode", "()I", false);
        integer(m, 200); m.visitJumpInsn(IF_ICMPEQ, ok);
        m.visitVarInsn(ALOAD, 6);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/net/HttpURLConnection", "disconnect", "()V", false);
        m.visitJumpInsn(GOTO, exit);
        m.visitLabel(ok);
        m.visitVarInsn(ALOAD, 6);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/net/HttpURLConnection", "getInputStream", "()Ljava/io/InputStream;", false);
        m.visitVarInsn(ASTORE, 7);
        m.visitTypeInsn(NEW, "java/io/ByteArrayOutputStream"); m.visitInsn(DUP);
        m.visitMethodInsn(INVOKESPECIAL, "java/io/ByteArrayOutputStream", "<init>", "()V", false);
        m.visitVarInsn(ASTORE, 8);
        integer(m, 8192); m.visitIntInsn(NEWARRAY, T_BYTE); m.visitVarInsn(ASTORE, 9);
        Label readLoop = new Label(), readDone = new Label();
        m.visitLabel(readLoop);
        m.visitVarInsn(ALOAD, 7); m.visitVarInsn(ALOAD, 9);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/io/InputStream", "read", "([B)I", false);
        m.visitVarInsn(ISTORE, 10); m.visitVarInsn(ILOAD, 10); m.visitJumpInsn(IFLT, readDone);
        m.visitVarInsn(ALOAD, 8); m.visitVarInsn(ALOAD, 9); m.visitInsn(ICONST_0); m.visitVarInsn(ILOAD, 10);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/io/ByteArrayOutputStream", "write", "([BII)V", false);
        m.visitVarInsn(ALOAD, 8);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/io/ByteArrayOutputStream", "size", "()I", false);
        m.visitLdcInsn(MAX_SEGMENT_RESPONSE_BYTES); m.visitJumpInsn(IF_ICMPLE, readLoop);
        throwState(m, "segment response too large");
        m.visitLabel(readDone);
        m.visitVarInsn(ALOAD, 7); m.visitMethodInsn(INVOKEVIRTUAL, "java/io/InputStream", "close", "()V", false);
        m.visitVarInsn(ALOAD, 6);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/net/HttpURLConnection", "disconnect", "()V", false);
        m.visitTypeInsn(NEW, "org/json/JSONArray"); m.visitInsn(DUP);
        m.visitVarInsn(ALOAD, 8); m.visitLdcInsn("UTF-8");
        m.visitMethodInsn(INVOKEVIRTUAL, "java/io/ByteArrayOutputStream", "toString",
                "(Ljava/lang/String;)Ljava/lang/String;", false);
        m.visitMethodInsn(INVOKESPECIAL, "org/json/JSONArray", "<init>", "(Ljava/lang/String;)V", false);
        m.visitVarInsn(ASTORE, 11);
        Label videoLoop = new Label(), videoNext = new Label();
        m.visitInsn(ICONST_0); m.visitVarInsn(ISTORE, 12);
        m.visitLabel(videoLoop);
        m.visitVarInsn(ILOAD, 12); m.visitVarInsn(ALOAD, 11);
        m.visitMethodInsn(INVOKEVIRTUAL, "org/json/JSONArray", "length", "()I", false);
        m.visitJumpInsn(IF_ICMPGE, exit);
        m.visitVarInsn(ALOAD, 11); m.visitVarInsn(ILOAD, 12);
        m.visitMethodInsn(INVOKEVIRTUAL, "org/json/JSONArray", "getJSONObject", "(I)Lorg/json/JSONObject;", false);
        m.visitVarInsn(ASTORE, 13);
        m.visitVarInsn(ALOAD, 1); m.visitVarInsn(ALOAD, 13); m.visitLdcInsn("videoID");
        m.visitMethodInsn(INVOKEVIRTUAL, "org/json/JSONObject", "optString", "(Ljava/lang/String;)Ljava/lang/String;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        m.visitJumpInsn(IFEQ, videoNext);
        m.visitVarInsn(ALOAD, 13); m.visitLdcInsn("segments");
        m.visitMethodInsn(INVOKEVIRTUAL, "org/json/JSONObject", "getJSONArray", "(Ljava/lang/String;)Lorg/json/JSONArray;", false);
        m.visitVarInsn(ASTORE, 14);
        m.visitVarInsn(ALOAD, 14);
        m.visitMethodInsn(INVOKEVIRTUAL, "org/json/JSONArray", "length", "()I", false);
        m.visitInsn(ICONST_4); m.visitInsn(IMUL); m.visitIntInsn(NEWARRAY, T_LONG); m.visitVarInsn(ASTORE, 15);
        m.visitInsn(ICONST_0); m.visitVarInsn(ISTORE, 16);
        Label segmentLoop = new Label(), segmentNext = new Label(), segmentDone = new Label();
        m.visitInsn(ICONST_0); m.visitVarInsn(ISTORE, 17);
        m.visitLabel(segmentLoop);
        m.visitVarInsn(ILOAD, 17); m.visitVarInsn(ALOAD, 14);
        m.visitMethodInsn(INVOKEVIRTUAL, "org/json/JSONArray", "length", "()I", false);
        m.visitJumpInsn(IF_ICMPGE, segmentDone);
        m.visitVarInsn(ALOAD, 14); m.visitVarInsn(ILOAD, 17);
        m.visitMethodInsn(INVOKEVIRTUAL, "org/json/JSONArray", "getJSONObject", "(I)Lorg/json/JSONObject;", false);
        m.visitVarInsn(ASTORE, 18);
        m.visitLdcInsn(s.actionType()); m.visitVarInsn(ALOAD, 18); m.visitLdcInsn("actionType");
        m.visitMethodInsn(INVOKEVIRTUAL, "org/json/JSONObject", "optString", "(Ljava/lang/String;)Ljava/lang/String;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        m.visitJumpInsn(IFEQ, segmentNext);
        m.visitVarInsn(ALOAD, 18); m.visitLdcInsn("category");
        m.visitMethodInsn(INVOKEVIRTUAL, "org/json/JSONObject", "optString", "(Ljava/lang/String;)Ljava/lang/String;", false);
        m.visitMethodInsn(INVOKESTATIC, FETCH, "category", "(Ljava/lang/String;)I", false);
        m.visitVarInsn(ISTORE, 19); m.visitVarInsn(ILOAD, 19); m.visitJumpInsn(IFLT, segmentNext);
        m.visitVarInsn(ALOAD, 18); m.visitLdcInsn("segment");
        m.visitMethodInsn(INVOKEVIRTUAL, "org/json/JSONObject", "getJSONArray", "(Ljava/lang/String;)Lorg/json/JSONArray;", false);
        m.visitVarInsn(ASTORE, 20);
        for (int bound = 0; bound < 2; bound++) {
            m.visitVarInsn(ALOAD, 15); m.visitVarInsn(ILOAD, 16); integer(m, bound); m.visitInsn(IADD);
            m.visitVarInsn(ALOAD, 20); integer(m, bound);
            m.visitMethodInsn(INVOKEVIRTUAL, "org/json/JSONArray", "getDouble", "(I)D", false);
            m.visitLdcInsn(1000.0); m.visitInsn(DMUL); m.visitInsn(D2L); m.visitInsn(LASTORE);
        }
        m.visitVarInsn(ALOAD, 15); m.visitVarInsn(ILOAD, 16); m.visitInsn(ICONST_2); m.visitInsn(IADD);
        m.visitVarInsn(ILOAD, 19); m.visitInsn(I2L); m.visitInsn(LASTORE);
        m.visitIincInsn(16, 4);
        m.visitLabel(segmentNext); m.visitIincInsn(17, 1); m.visitJumpInsn(GOTO, segmentLoop);
        m.visitLabel(segmentDone);
        m.visitVarInsn(ALOAD, 1); m.visitVarInsn(ALOAD, 15); m.visitVarInsn(ILOAD, 16);
        m.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "copyOf", "([JI)[J", false);
        m.visitMethodInsn(INVOKESTATIC, STORE, "publish", "(Ljava/lang/String;[J)V", false);
        m.visitLdcInsn(LOG_TAG);
        m.visitTypeInsn(NEW, "java/lang/StringBuilder"); m.visitInsn(DUP);
        m.visitLdcInsn("segments loaded: ");
        m.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false);
        m.visitVarInsn(ILOAD, 16); m.visitInsn(ICONST_4); m.visitInsn(IDIV);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
        m.visitMethodInsn(INVOKESTATIC, "android/util/Log", "i", "(Ljava/lang/String;Ljava/lang/String;)I", false);
        m.visitInsn(POP);
        m.visitJumpInsn(GOTO, exit);
        m.visitLabel(videoNext); m.visitIincInsn(12, 1); m.visitJumpInsn(GOTO, videoLoop);
        m.visitLabel(end);
        m.visitLabel(exit); m.visitInsn(RETURN);
        m.visitLabel(fail); m.visitVarInsn(ASTORE, 21);
        m.visitLdcInsn(LOG_TAG);
        m.visitTypeInsn(NEW, "java/lang/StringBuilder"); m.visitInsn(DUP);
        m.visitLdcInsn("segment request failed: ");
        m.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false);
        m.visitVarInsn(ALOAD, 21);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
        m.visitMethodInsn(INVOKESTATIC, "android/util/Log", "w", "(Ljava/lang/String;Ljava/lang/String;)I", false);
        m.visitInsn(POP); m.visitInsn(RETURN);
        m.visitMaxs(0, 0); m.visitEnd();
        save(root, FETCH, w);
    }

    // endregion

    // region Target identity, hook ownership and entry

    private static void identity(Path root, String hash) throws IOException {
        if (!hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("target hash");
        ClassWriter w = writer(IDENTITY, "java/lang/Object");
        MethodVisitor m = w.visitMethod(ACC_PUBLIC | ACC_STATIC, "matches",
                "(Landroid/content/pm/ApplicationInfo;)Z", null, null);
        m.visitCode(); Label start = new Label(), end = new Label(), fail = new Label();
        Label loop = new Label(), done = new Label();
        m.visitTryCatchBlock(start, end, fail, "java/lang/Throwable");
        m.visitInsn(ACONST_NULL); m.visitVarInsn(ASTORE, 2);
        m.visitLabel(start); m.visitLdcInsn("SHA-256");
        m.visitMethodInsn(INVOKESTATIC, "java/security/MessageDigest", "getInstance",
                "(Ljava/lang/String;)Ljava/security/MessageDigest;", false);
        m.visitVarInsn(ASTORE, 1);
        m.visitTypeInsn(NEW, "java/io/FileInputStream"); m.visitInsn(DUP);
        m.visitVarInsn(ALOAD, 0);
        m.visitFieldInsn(GETFIELD, "android/content/pm/ApplicationInfo", "sourceDir", "Ljava/lang/String;");
        m.visitMethodInsn(INVOKESPECIAL, "java/io/FileInputStream", "<init>", "(Ljava/lang/String;)V", false);
        m.visitVarInsn(ASTORE, 2);
        integer(m, 8192); m.visitIntInsn(NEWARRAY, T_BYTE); m.visitVarInsn(ASTORE, 3);
        m.visitLabel(loop); m.visitVarInsn(ALOAD, 2); m.visitVarInsn(ALOAD, 3);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/io/FileInputStream", "read", "([B)I", false);
        m.visitVarInsn(ISTORE, 4); m.visitVarInsn(ILOAD, 4); m.visitJumpInsn(IFLT, done);
        m.visitVarInsn(ALOAD, 1); m.visitVarInsn(ALOAD, 3); m.visitInsn(ICONST_0);
        m.visitVarInsn(ILOAD, 4);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/security/MessageDigest", "update", "([BII)V", false);
        m.visitJumpInsn(GOTO, loop);
        m.visitLabel(done); m.visitVarInsn(ALOAD, 2);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/io/FileInputStream", "close", "()V", false);
        m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/security/MessageDigest", "digest", "()[B", false);
        integer(m, 32); m.visitIntInsn(NEWARRAY, T_BYTE);
        for (int i = 0; i < 32; i++) {
            m.visitInsn(DUP); integer(m, i);
            integer(m, Integer.parseInt(hash.substring(i * 2, i * 2 + 2), 16));
            m.visitInsn(BASTORE);
        }
        m.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "equals", "([B[B)Z", false);
        m.visitLabel(end); m.visitInsn(IRETURN);
        m.visitLabel(fail); m.visitVarInsn(ASTORE, 5);
        Label closeStart = new Label(), closeEnd = new Label();
        Label closeFail = new Label(), noStream = new Label();
        m.visitVarInsn(ALOAD, 2); m.visitJumpInsn(IFNULL, noStream);
        m.visitTryCatchBlock(closeStart, closeEnd, closeFail, "java/lang/Throwable");
        m.visitLabel(closeStart); m.visitVarInsn(ALOAD, 2);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/io/FileInputStream", "close", "()V", false);
        m.visitLabel(closeEnd); m.visitJumpInsn(GOTO, noStream);
        m.visitLabel(closeFail); m.visitInsn(POP);
        m.visitLabel(noStream); m.visitInsn(ICONST_0); m.visitInsn(IRETURN);
        m.visitMaxs(0, 0); m.visitEnd(); save(root, IDENTITY, w);
    }
    private static void controller(Path root, List<Hook> hooks, boolean seek, boolean members,
                                   boolean litho, boolean shorts) throws IOException {
        ClassWriter w = writer(CTRL, "java/lang/Object");
        w.visitField(ACC_PRIVATE | ACC_STATIC, "handles", "[L" + HANDLE + ";", null, null).visitEnd();
        w.visitField(ACC_PUBLIC | ACC_STATIC | ACC_VOLATILE, "lastHook", "Ljava/lang/String;", null, null).visitEnd();
        MethodVisitor m = w.visitMethod(ACC_PUBLIC | ACC_STATIC | ACC_SYNCHRONIZED, "install",
                "(L" + ENTRY + ";Ljava/lang/ClassLoader;)V", null, new String[] {"java/lang/Throwable"});
        m.visitCode(); Label start = new Label();
        m.visitFieldInsn(GETSTATIC, CTRL, "handles", "[L" + HANDLE + ";");
        m.visitJumpInsn(IFNULL, start); m.visitInsn(RETURN); m.visitLabel(start);
        // Locals: 0 entry, 1 loader, 2 class, 3.. executables, then handles and error.
        int handlesLocal = 3 + hooks.size();
        int errorLocal = handlesLocal + 1;
        for (int i = 0; i < hooks.size(); i++) {
            Hook h = hooks.get(i);
            m.visitLdcInsn(h.hookId()); m.visitFieldInsn(PUTSTATIC, CTRL, "lastHook", "Ljava/lang/String;");
            resolveMember(m, h.member(), 1, 2, 3 + i, "binding shape mismatch: " + h.hookId());
            if (h.handler().equals("observe_video_id")) bindVideoField(m, h, 2);
        }
        if (seek) {
            m.visitLdcInsn("SponsorBlock seek binding"); m.visitFieldInsn(PUTSTATIC, CTRL, "lastHook", "Ljava/lang/String;");
            m.visitVarInsn(ALOAD, 1);
            m.visitMethodInsn(INVOKESTATIC, SEEK, "bind", "(Ljava/lang/ClassLoader;)V", false);
        }
        if (members) {
            m.visitLdcInsn("settings entry bindings"); m.visitFieldInsn(PUTSTATIC, CTRL, "lastHook", "Ljava/lang/String;");
            m.visitVarInsn(ALOAD, 1);
            m.visitMethodInsn(INVOKESTATIC, SettingsGenerator.MEMBERS, "bind", "(Ljava/lang/ClassLoader;)V", false);
        }
        if (litho) {
            m.visitLdcInsn("sponsored feed bindings"); m.visitFieldInsn(PUTSTATIC, CTRL, "lastHook", "Ljava/lang/String;");
            m.visitVarInsn(ALOAD, 1);
            m.visitMethodInsn(INVOKESTATIC, LITHO, "bind", "(Ljava/lang/ClassLoader;)V", false);
        }
        if (shorts) {
            m.visitLdcInsn("Shorts feed bindings"); m.visitFieldInsn(PUTSTATIC, CTRL, "lastHook", "Ljava/lang/String;");
            m.visitVarInsn(ALOAD, 1);
            m.visitMethodInsn(INVOKESTATIC, SHORTS, "bind", "(Ljava/lang/ClassLoader;)V", false);
        }
        integer(m, hooks.size()); m.visitTypeInsn(ANEWARRAY, HANDLE);
        m.visitVarInsn(ASTORE, handlesLocal);
        Label hookStart = new Label(), hookEnd = new Label(), rollback = new Label();
        m.visitTryCatchBlock(hookStart, hookEnd, rollback, "java/lang/Throwable");
        m.visitLabel(hookStart);
        for (int i = 0; i < hooks.size(); i++) {
            Hook h = hooks.get(i);
            m.visitLdcInsn(h.hookId()); m.visitFieldInsn(PUTSTATIC, CTRL, "lastHook", "Ljava/lang/String;");
            m.visitVarInsn(ALOAD, handlesLocal); integer(m, i);
            m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 3 + i);
            m.visitTypeInsn(CHECKCAST, "java/lang/reflect/Executable");
            m.visitMethodInsn(INVOKEVIRTUAL, ENTRY, "hook",
                    "(Ljava/lang/reflect/Executable;)L" + BUILDER + ";", false);
            m.visitLdcInsn(h.hookId());
            m.visitMethodInsn(INVOKEINTERFACE, BUILDER, "setId",
                    "(Ljava/lang/String;)L" + BUILDER + ";", true);
            m.visitFieldInsn(GETSTATIC, MODE, "PROTECTIVE", "L" + MODE + ";");
            m.visitMethodInsn(INVOKEINTERFACE, BUILDER, "setExceptionMode",
                    "(L" + MODE + ";)L" + BUILDER + ";", true);
            String hooker = P + "EffectHooker" + i;
            m.visitTypeInsn(NEW, hooker); m.visitInsn(DUP);
            m.visitMethodInsn(INVOKESPECIAL, hooker, "<init>", "()V", false);
            m.visitMethodInsn(INVOKEINTERFACE, BUILDER, "intercept",
                    "(L" + HOOKER + ";)L" + HANDLE + ";", true);
            m.visitInsn(AASTORE);
        }
        m.visitLabel(hookEnd);
        m.visitVarInsn(ALOAD, handlesLocal);
        m.visitFieldInsn(PUTSTATIC, CTRL, "handles", "[L" + HANDLE + ";");
        m.visitInsn(ACONST_NULL); m.visitFieldInsn(PUTSTATIC, CTRL, "lastHook", "Ljava/lang/String;");
        m.visitInsn(RETURN);
        m.visitLabel(rollback); m.visitVarInsn(ASTORE, errorLocal);
        m.visitMethodInsn(INVOKESTATIC, SNAP, "disableAll", "()V", false);
        for (int i = 0; i < hooks.size(); i++) {
            Label unhookStart = new Label(), unhookEnd = new Label();
            Label unhookFail = new Label(), skip = new Label();
            m.visitTryCatchBlock(unhookStart, unhookEnd, unhookFail, "java/lang/Throwable");
            m.visitLabel(unhookStart);
            m.visitVarInsn(ALOAD, handlesLocal); integer(m, i); m.visitInsn(AALOAD);
            m.visitJumpInsn(IFNULL, unhookEnd);
            m.visitVarInsn(ALOAD, handlesLocal); integer(m, i); m.visitInsn(AALOAD);
            m.visitMethodInsn(INVOKEINTERFACE, HANDLE, "unhook", "()V", true);
            m.visitLabel(unhookEnd); m.visitJumpInsn(GOTO, skip);
            m.visitLabel(unhookFail); m.visitInsn(POP);
            m.visitLabel(skip);
        }
        m.visitVarInsn(ALOAD, errorLocal); m.visitInsn(ATHROW);
        m.visitMaxs(0, 0); m.visitEnd(); save(root, CTRL, w);
    }
    private static void entry(Path root, List<Config> configs, boolean active) throws IOException {
        ClassWriter w = writer(ENTRY, XPOSED);
        w.visitField(ACC_PRIVATE, "listener", "L" + LISTENER + ";", null, null).visitEnd();
        ctor(w, XPOSED);
        MethodVisitor m = w.visitMethod(ACC_PUBLIC, "onPackageReady", "(L" + PARAM + ";)V", null, null);
        m.visitCode();
        if (!active) {
            m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd(); save(root, ENTRY, w); return;
        }
        Set<String> groups = new LinkedHashSet<>();
        for (Config c : configs) groups.add(c.group());
        Label start = new Label(), end = new Label(), fail = new Label(), done = new Label(), identityOk = new Label();
        m.visitTryCatchBlock(start, end, fail, "java/lang/Throwable");
        m.visitLabel(start); m.visitLdcInsn("com.google.android.youtube");
        m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKEINTERFACE, PARAM, "getPackageName", "()Ljava/lang/String;", true);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        m.visitJumpInsn(IFEQ, done);
        m.visitLdcInsn("com.google.android.youtube");
        m.visitMethodInsn(INVOKESTATIC, "android/app/Application", "getProcessName", "()Ljava/lang/String;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        m.visitJumpInsn(IFEQ, done);
        m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKEINTERFACE, PARAM, "getApplicationInfo",
                "()Landroid/content/pm/ApplicationInfo;", true);
        m.visitMethodInsn(INVOKESTATIC, IDENTITY, "matches", "(Landroid/content/pm/ApplicationInfo;)Z", false);
        m.visitJumpInsn(IFNE, identityOk);
        m.visitLdcInsn("mismatch"); m.visitLdcInsn("YouTube APK does not match the verified target build");
        m.visitMethodInsn(INVOKESTATIC, DIAG, "report", "(Ljava/lang/String;Ljava/lang/String;)V", false);
        m.visitJumpInsn(GOTO, done);
        m.visitLabel(identityOk);
        m.visitLdcInsn("checking"); m.visitLdcInsn("Resolving and installing hooks");
        m.visitMethodInsn(INVOKESTATIC, DIAG, "report", "(Ljava/lang/String;Ljava/lang/String;)V", false);
        m.visitVarInsn(ALOAD, 0); m.visitTypeInsn(NEW, LISTENER); m.visitInsn(DUP);
        m.visitMethodInsn(INVOKESPECIAL, LISTENER, "<init>", "()V", false);
        m.visitFieldInsn(PUTFIELD, ENTRY, "listener", "L" + LISTENER + ";");
        for (String group : groups) {
            m.visitVarInsn(ALOAD, 0); m.visitLdcInsn(group);
            m.visitMethodInsn(INVOKEVIRTUAL, ENTRY, "getRemotePreferences",
                    "(Ljava/lang/String;)L" + PREFS + ";", false);
            m.visitVarInsn(ASTORE, 2);
            for (int i = 0; i < configs.size(); i++) {
                Config c = configs.get(i);
                if (!c.group().equals(group)) continue;
                integer(m, i); m.visitVarInsn(ALOAD, 2); m.visitLdcInsn(c.key());
                m.visitInsn(c.fallback() ? ICONST_1 : ICONST_0);
                m.visitMethodInsn(INVOKEINTERFACE, PREFS, "getBoolean", "(Ljava/lang/String;Z)Z", true);
                m.visitMethodInsn(INVOKESTATIC, SNAP, "publish", "(IZ)V", false);
            }
            m.visitVarInsn(ALOAD, 2); m.visitVarInsn(ALOAD, 0);
            m.visitFieldInsn(GETFIELD, ENTRY, "listener", "L" + LISTENER + ";");
            m.visitMethodInsn(INVOKEINTERFACE, PREFS, "registerOnSharedPreferenceChangeListener",
                    "(L" + PREF_LISTENER + ";)V", true);
        }
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 1);
        // API 102 exposes the target package loader through the inherited
        // PackageLoadedParam method. PackageReadyParam#getClassLoader is the
        // module/component loader and cannot resolve YouTube members.
        m.visitMethodInsn(INVOKEINTERFACE, PARAM, "getDefaultClassLoader", "()Ljava/lang/ClassLoader;", true);
        m.visitMethodInsn(INVOKESTATIC, CTRL, "install",
                "(L" + ENTRY + ";Ljava/lang/ClassLoader;)V", false);
        m.visitLdcInsn("ready"); m.visitLdcInsn("All required hooks installed");
        m.visitMethodInsn(INVOKESTATIC, DIAG, "report", "(Ljava/lang/String;Ljava/lang/String;)V", false);
        log(m, "i", "module hooks installed");
        m.visitLabel(end); m.visitLabel(done); m.visitInsn(RETURN);
        m.visitLabel(fail); m.visitVarInsn(ASTORE, 3);
        m.visitMethodInsn(INVOKESTATIC, SNAP, "disableAll", "()V", false);
        m.visitLdcInsn("failed");
        m.visitTypeInsn(NEW, "java/lang/StringBuilder"); m.visitInsn(DUP);
        m.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        m.visitFieldInsn(GETSTATIC, CTRL, "lastHook", "Ljava/lang/String;");
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        m.visitLdcInsn(": ");
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        m.visitVarInsn(ALOAD, 3);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
        m.visitMethodInsn(INVOKESTATIC, DIAG, "report", "(Ljava/lang/String;Ljava/lang/String;)V", false);
        m.visitLdcInsn(LOG_TAG); m.visitLdcInsn("hook installation failed");
        m.visitVarInsn(ALOAD, 3);
        m.visitMethodInsn(INVOKESTATIC, "android/util/Log", "e",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I", false);
        m.visitInsn(POP); m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();
        save(root, ENTRY, w);
    }

    // endregion

    // region Plan

    private static Member member(String[] f, int at) {
        return new Member(f[at], f[at + 1], f[at + 2], f[at + 3], Integer.parseInt(f[at + 4]), f[at + 5]);
    }
    /**
     * Reads the tab-separated plan written by {@code build_development.py}:
     * {@code target}, {@code active}, {@code config}, {@code hook}, {@code seek},
     * {@code segments} and {@code category} lines.
     */
    private static Plan readPlan(Path file) throws IOException {
        String hash = null; Boolean active = null; Seek seek = null; String[] segmentLine = null;
        Diagnostic diagnostic = null;
        List<Config> configs = new ArrayList<>();
        List<Hook> hooks = new ArrayList<>();
        List<Category> categories = new ArrayList<>();
        SettingsGenerator.Builder settings = new SettingsGenerator.Builder();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isEmpty()) continue;
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "target" -> hash = f[1];
                case "active" -> active = Boolean.parseBoolean(f[1]);
                case "diagnostic" -> diagnostic = new Diagnostic(f[1], f[2], f[3], f[4], f[5], f[6]);
                case "config" -> configs.add(new Config(f[1], f[2], Boolean.parseBoolean(f[3])));
                case "hook" -> hooks.add(new Hook(member(f, 1), f[7], Integer.parseInt(f[8]), f[9],
                        List.of(f).subList(10, f.length)));
                case "seek" -> {
                    if (seek != null) throw new IllegalArgumentException("duplicate seek");
                    seek = new Seek(member(f, 1), f[7]);
                }
                case "segments" -> segmentLine = f;
                case "category" -> categories.add(new Category(f[1], Integer.parseInt(f[2])));
                case "member", "role", "page", "layouts", "icon", "section", "item" -> settings.accept(f);
                default -> throw new IllegalArgumentException("unknown plan line " + f[0]);
            }
        }
        if (hash == null || active == null || configs.isEmpty() || hooks.isEmpty() || diagnostic == null
                || !diagnostic.id().equals("hook_compatibility")
                || !diagnostic.transport().equals("EXPLICIT_BROADCAST")
                || !diagnostic.failureMode().equals("ROLLBACK_ALL_DISABLE_TOGGLES")
                || !diagnostic.targetPackage().equals("com.google.android.youtube")) {
            throw new IllegalArgumentException("incomplete plan");
        }
        Segments segments = null;
        if (segmentLine != null) {
            segments = new Segments(segmentLine[1], Integer.parseInt(segmentLine[2]),
                    Integer.parseInt(segmentLine[3]), Integer.parseInt(segmentLine[4]),
                    segmentLine[5], segmentLine[6], List.copyOf(categories));
        }
        boolean skips = hooks.stream().anyMatch(h -> h.handler().equals("skip_segments"));
        boolean captures = hooks.stream().anyMatch(h -> h.handler().equals("capture_receiver"));
        boolean observes = hooks.stream().anyMatch(h -> h.handler().equals("observe_video_id"));
        if (skips != (segments != null) || skips != (seek != null) || skips != captures || skips != observes
                || (segments != null && categories.isEmpty())) {
            throw new IllegalArgumentException("segment skipping plan is incomplete");
        }
        SettingsGenerator.Settings built = settings.build();
        boolean injects = hooks.stream().anyMatch(h -> h.handler().equals("inject_settings_entry"));
        if (injects != (built != null)) throw new IllegalArgumentException("settings plan is incomplete");
        return new Plan(hash, active, configs, hooks, seek, segments, built, diagnostic);
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: GenerateModule <out> <plan>");
        Path root = Path.of(args[0]);
        Plan plan = readPlan(Path.of(args[1]));
        snapshot(root, plan.configs().size()); listener(root, plan.configs());
        for (int i = 0; i < plan.hooks().size(); i++) hooker(root, i, plan.hooks().get(i));
        if (plan.seek() != null) {
            seekPort(root, plan.seek()); segmentStore(root); segmentFetch(root, plan.segments());
        }
        if (plan.settings() != null) SettingsGenerator.generate(root, plan.settings(), plan.configs(),
                plan.hooks(), plan.active(), plan.diagnostic());
        Hook lithoHook = plan.hooks().stream().filter(h -> h.handler().equals("filter_litho_ads"))
                .findFirst().orElse(null);
        if (lithoHook != null) LithoAdGenerator.generate(root, lithoHook.arguments());
        Hook shortsHook = plan.hooks().stream().filter(h -> h.handler().equals("filter_shorts_ads"))
                .findFirst().orElse(null);
        if (shortsHook != null) ShortsFeedGenerator.generate(root, shortsHook.arguments());
        identity(root, plan.hash()); controller(root, plan.hooks(), plan.seek() != null,
                plan.settings() != null, lithoHook != null, shortsHook != null);
        DiagnosticsGenerator.generate(root, plan.diagnostic());
        entry(root, plan.configs(), plan.active());
    }

    // endregion
}
