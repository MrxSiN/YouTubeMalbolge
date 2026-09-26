package toolchain.backend;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
    private static final String DIAG = StatusTransportGenerator.STATUS;
    static final String SEEK = P + "SeekPort";
    static final String STORE = P + "SegmentStore";
    static final String STATE = P + "StateSlots";
    private static final String FETCH = P + "SegmentFetch";
    private static final String PROGRAM_MEMBERS = ProgramMembersGenerator.NAME;
    private static final String XPOSED = "io/github/libxposed/api/XposedModule";
    private static final String PARAM = "io/github/libxposed/api/XposedModuleInterface$PackageReadyParam";
    private static final String HOT_RELOADING = "io/github/libxposed/api/XposedModuleInterface$HotReloadingParam";
    private static final String HOT_RELOADED = "io/github/libxposed/api/XposedModuleInterface$HotReloadedParam";
    static final String CHAIN = "io/github/libxposed/api/XposedInterface$Chain";
    private static final String HOOKER = "io/github/libxposed/api/XposedInterface$Hooker";
    private static final String BUILDER = "io/github/libxposed/api/XposedInterface$HookBuilder";
    private static final String HANDLE = "io/github/libxposed/api/XposedInterface$HookHandle";
    private static final String MODE = "io/github/libxposed/api/XposedInterface$ExceptionMode";
    private static final String PREFS = "android/content/SharedPreferences";
    private static final String PREF_LISTENER = PREFS + "$OnSharedPreferenceChangeListener";
    static final String LOG_TAG = "YtmMalbolge";
    static final String RESOLVER = "io/github/mrxsin/ytmalbolge/RuntimeResolver";
    private static final String CLASS_FOR_NAME = "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;";
    /** Seek targets closer than this to a segment end are left to normal playback. */

    record Config(String group, String key, boolean fallback) {}
    record Member(String kind, String owner, String name, String descriptor, int access,
                          String superclass, int opcodeCount) {
        boolean constructor() { return kind.equals("CONSTRUCTOR"); }
    }
    record Hook(Member member, int configIndex, String hookId, String group, int startup,
                List<String> arguments) {}
    private record Seek(Member member, String constant) {}
    private record Category(String name, int configIndex) {}
    private record Segments(String origin, String path, int prefixLength, int connectMs, int readMs,
                            int maxBytes, int successStatus, String digest, String query,
                            String videoKey, String listKey, String actionKey, String categoryKey,
                            String boundsKey, String actionValue, int scale, boolean useCaches,
                            int retries, List<Category> categories) {}
    record Diagnostic(String id, String transport, String failureMode, String targetPackage,
                       String screenTitle, String copyLabel, String checkingState, String checkingText,
                       String readyState, String readyText, String degradedState, String degradedText,
                       String failedState, String waitingText, String staleState, String staleText,
                       String readyDisplayText, String unavailableOpenText, String unavailableDisabledText,
                       String readFailedText, String reportTitle, String unavailablePrefix) {}
    private record Plan(String hash, List<Config> configs, List<Hook> hooks, Seek seek,
                        Segments segments, UiModelGenerator.Settings settings, Diagnostic diagnostic,
                        List<Member> programMembers, int[] resolverWeights) {}

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
        Label resolved = new Label();
        m.visitVarInsn(ALOAD, loaderLocal); m.visitLdcInsn(b.kind()); m.visitLdcInsn(b.owner());
        m.visitLdcInsn(b.name()); m.visitLdcInsn(b.descriptor()); integer(m, b.access());
        m.visitLdcInsn(b.superclass()); integer(m, b.opcodeCount());
        m.visitMethodInsn(INVOKESTATIC, RESOLVER, "executable",
                "(Ljava/lang/ClassLoader;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;I)Ljava/lang/reflect/Executable;",
                false);
        m.visitInsn(DUP); m.visitJumpInsn(IFNONNULL, resolved); m.visitInsn(POP); throwState(m, failure);
        m.visitLabel(resolved); m.visitVarInsn(ASTORE, targetLocal);
        m.visitVarInsn(ALOAD, targetLocal);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Executable", "getDeclaringClass", "()Ljava/lang/Class;", false);
        m.visitVarInsn(ASTORE, classLocal);
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

    /** Cold binding of one program-declared field into a generic state slot. */
    private static void bindDeclaredField(MethodVisitor m, Hook hook, int classLocal, int[] binding) {
        m.visitVarInsn(ALOAD, classLocal); m.visitLdcInsn(AotHookCompiler.constant(hook, binding[0]));
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredField",
                "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false);
        m.visitInsn(DUP);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "getType", "()Ljava/lang/Class;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
        m.visitLdcInsn(AotHookCompiler.constant(hook, binding[1]));
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label typed = new Label();
        m.visitJumpInsn(IFNE, typed);
        throwState(m, "binding shape mismatch: " + hook.hookId());
        m.visitLabel(typed);
        m.visitInsn(DUP); m.visitInsn(ICONST_1);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "setAccessible", "(Z)V", false);
        integer(m, binding[2]); m.visitInsn(SWAP);
        m.visitMethodInsn(INVOKESTATIC, STATE, "set", "(ILjava/lang/Object;)V", false);
    }
    private static void hooker(Path root, int index, Hook hook) throws IOException {
        String name = P + "ProgramHooker" + index;
        ClassWriter w = writer(name, "java/lang/Object", HOOKER);
        ctor(w, "java/lang/Object");
        MethodVisitor m = w.visitMethod(ACC_PUBLIC, "intercept", "(L" + CHAIN + ";)Ljava/lang/Object;",
                null, new String[] {"java/lang/Throwable"});
        m.visitCode(); m.visitInsn(ACONST_NULL); m.visitVarInsn(ASTORE, 30);
        m.visitInsn(ICONST_0); m.visitVarInsn(ISTORE, 31); Label proceed = new Label();
        boolean failOpen = AotHookCompiler.failOpen(hook);
        Label programStart = new Label(), programEnd = new Label(), programFail = new Label();
        if (failOpen) m.visitTryCatchBlock(programStart, programEnd, programFail, "java/lang/Throwable");
        if (hook.configIndex() >= 0) {
            integer(m, hook.configIndex());
            m.visitMethodInsn(INVOKESTATIC, SNAP, "enabled", "(I)Z", false);
            m.visitJumpInsn(IFEQ, proceed);
        }
        if (failOpen) m.visitLabel(programStart);
        AotHookCompiler.emit(m, hook);
        if (failOpen) {
            m.visitLabel(programEnd); m.visitJumpInsn(GOTO, proceed);
            Label notProceeded = new Label(), completed = new Label();
            m.visitLabel(programFail); m.visitVarInsn(ASTORE, 29); m.visitVarInsn(ILOAD, 31);
            m.visitJumpInsn(IFEQ, notProceeded); m.visitVarInsn(ILOAD, 31); m.visitInsn(ICONST_2);
            m.visitJumpInsn(IF_ICMPEQ, completed); m.visitVarInsn(ALOAD, 29); m.visitInsn(ATHROW);
            m.visitLabel(completed); m.visitVarInsn(ALOAD, 30); m.visitInsn(ARETURN);
            m.visitLabel(notProceeded); m.visitJumpInsn(GOTO, proceed);
        }
        m.visitLabel(proceed); m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKEINTERFACE, CHAIN, "proceed", "()Ljava/lang/Object;", true);
        m.visitInsn(ARETURN); m.visitMaxs(0, 0); m.visitEnd();
        save(root, name, w);
    }

    // endregion

    // region Segment skipping runtime

    /** Small generic volatile object store used by AOT programs and platform bridges. */
    private static void stateSlots(Path root, int count) throws IOException {
        ClassWriter w = writer(STATE, "java/lang/Object");
        for (int index = 0; index < count; index++) {
            w.visitField(ACC_PRIVATE | ACC_STATIC | ACC_VOLATILE, "s" + index,
                    "Ljava/lang/Object;", null, null).visitEnd();
        }
        MethodVisitor m = w.visitMethod(ACC_STATIC, "get", "(I)Ljava/lang/Object;", null, null);
        m.visitCode();
        for (int index = 0; index < count; index++) {
            Label next = new Label(); m.visitVarInsn(ILOAD, 0); integer(m, index);
            m.visitJumpInsn(IF_ICMPNE, next); m.visitFieldInsn(GETSTATIC, STATE, "s" + index, "Ljava/lang/Object;");
            m.visitInsn(ARETURN); m.visitLabel(next);
        }
        throwState(m, "state slot index"); m.visitMaxs(0, 0); m.visitEnd();
        m = w.visitMethod(ACC_STATIC, "set", "(ILjava/lang/Object;)V", null, null);
        m.visitCode();
        for (int index = 0; index < count; index++) {
            Label next = new Label(); m.visitVarInsn(ILOAD, 0); integer(m, index);
            m.visitJumpInsn(IF_ICMPNE, next); m.visitVarInsn(ALOAD, 1);
            m.visitFieldInsn(PUTSTATIC, STATE, "s" + index, "Ljava/lang/Object;");
            m.visitInsn(RETURN); m.visitLabel(next);
        }
        throwState(m, "state slot index"); m.visitMaxs(0, 0); m.visitEnd();
        save(root, STATE, w);
    }

    /** Holds the bound player receiver and seek member; invoked only on the player's thread. */
    private static void seekPort(Path root, Seek seek) throws IOException {
        Member b = seek.member();
        Type[] params = Type.getArgumentTypes(b.descriptor());
        if (b.constructor() || params.length != 2 || params[0].getSort() != Type.LONG
                || params[1].getSort() != Type.OBJECT || !b.descriptor().endsWith(")Z")) {
            throw new IllegalArgumentException("unsupported seek shape");
        }
        ClassWriter w = writer(SEEK, "java/lang/Object");
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
        integer(m, 0); m.visitMethodInsn(INVOKESTATIC, STATE, "get", "(I)Ljava/lang/Object;", false);
        m.visitVarInsn(ASTORE, 2);
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
        MethodVisitor m = w.visitMethod(ACC_STATIC | ACC_SYNCHRONIZED, "refresh", "(Ljava/lang/String;)V", null, null);
        m.visitCode();
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
        m.visitVarInsn(ALOAD, 0); integer(m, 3);
        m.visitMethodInsn(INVOKESTATIC, STATE, "get", "(I)Ljava/lang/Object;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        m.visitJumpInsn(IFEQ, stale);
        integer(m, 1); m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKESTATIC, STATE, "set", "(ILjava/lang/Object;)V", false);
        m.visitLabel(stale); m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();
        save(root, STORE, w);
    }

    /** One background request for one video; publishes only if that video is still current. */
    private static void segmentFetch(Path root, Segments s) throws IOException {
        if (s.prefixLength() < 4 || s.prefixLength() > 32 || s.prefixLength() % 2 != 0) {
            throw new IllegalArgumentException("hash prefix length");
        }
        if (s.maxBytes() < 1 || s.connectMs() < 1 || s.readMs() < 1 || s.scale() < 1
                || s.retries() != 0) throw new IllegalArgumentException("unsupported load policy");
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
        m.visitInsn(ACONST_NULL); m.visitVarInsn(ASTORE, 6);
        m.visitInsn(ACONST_NULL); m.visitVarInsn(ASTORE, 7);
        m.visitLabel(start);
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, FETCH, "id", "Ljava/lang/String;");
        m.visitVarInsn(ASTORE, 1);
        m.visitLdcInsn(s.digest());
        m.visitMethodInsn(INVOKESTATIC, "java/security/MessageDigest", "getInstance",
                "(Ljava/lang/String;)Ljava/security/MessageDigest;", false);
        m.visitVarInsn(ALOAD, 1);
        m.visitFieldInsn(GETSTATIC, "java/nio/charset/StandardCharsets", "UTF_8", "Ljava/nio/charset/Charset;");
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "getBytes", "(Ljava/nio/charset/Charset;)[B", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/security/MessageDigest", "digest", "([B)[B", false);
        m.visitVarInsn(ASTORE, 2);
        m.visitTypeInsn(NEW, "java/lang/StringBuilder"); m.visitInsn(DUP);
        m.visitLdcInsn(s.origin() + s.path());
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
        m.visitVarInsn(ALOAD, 6); m.visitInsn(s.useCaches() ? ICONST_1 : ICONST_0);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/net/HttpURLConnection", "setUseCaches", "(Z)V", false);
        Label ok = new Label();
        m.visitVarInsn(ALOAD, 6);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/net/HttpURLConnection", "getResponseCode", "()I", false);
        integer(m, s.successStatus()); m.visitJumpInsn(IF_ICMPEQ, ok);
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
        m.visitLdcInsn(s.maxBytes()); m.visitJumpInsn(IF_ICMPLE, readLoop);
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
        m.visitVarInsn(ALOAD, 1); m.visitVarInsn(ALOAD, 13); m.visitLdcInsn(s.videoKey());
        m.visitMethodInsn(INVOKEVIRTUAL, "org/json/JSONObject", "optString", "(Ljava/lang/String;)Ljava/lang/String;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        m.visitJumpInsn(IFEQ, videoNext);
        m.visitVarInsn(ALOAD, 13); m.visitLdcInsn(s.listKey());
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
        m.visitLdcInsn(s.actionValue()); m.visitVarInsn(ALOAD, 18); m.visitLdcInsn(s.actionKey());
        m.visitMethodInsn(INVOKEVIRTUAL, "org/json/JSONObject", "optString", "(Ljava/lang/String;)Ljava/lang/String;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        m.visitJumpInsn(IFEQ, segmentNext);
        m.visitVarInsn(ALOAD, 18); m.visitLdcInsn(s.categoryKey());
        m.visitMethodInsn(INVOKEVIRTUAL, "org/json/JSONObject", "optString", "(Ljava/lang/String;)Ljava/lang/String;", false);
        m.visitMethodInsn(INVOKESTATIC, FETCH, "category", "(Ljava/lang/String;)I", false);
        m.visitVarInsn(ISTORE, 19); m.visitVarInsn(ILOAD, 19); m.visitJumpInsn(IFLT, segmentNext);
        m.visitVarInsn(ALOAD, 18); m.visitLdcInsn(s.boundsKey());
        m.visitMethodInsn(INVOKEVIRTUAL, "org/json/JSONObject", "getJSONArray", "(Ljava/lang/String;)Lorg/json/JSONArray;", false);
        m.visitVarInsn(ASTORE, 20);
        m.visitVarInsn(ALOAD, 20);
        m.visitMethodInsn(INVOKEVIRTUAL, "org/json/JSONArray", "length", "()I", false);
        m.visitInsn(ICONST_2); m.visitJumpInsn(IF_ICMPNE, segmentNext);
        for (int bound = 0; bound < 2; bound++) {
            m.visitVarInsn(ALOAD, 20); integer(m, bound);
            m.visitMethodInsn(INVOKEVIRTUAL, "org/json/JSONArray", "getDouble", "(I)D", false);
            m.visitVarInsn(DSTORE, 22 + bound * 2);
            m.visitVarInsn(DLOAD, 22 + bound * 2);
            m.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "isFinite", "(D)Z", false);
            m.visitJumpInsn(IFEQ, segmentNext);
        }
        m.visitVarInsn(DLOAD, 22); m.visitInsn(DCONST_0); m.visitInsn(DCMPL);
        m.visitJumpInsn(IFLT, segmentNext);
        m.visitVarInsn(DLOAD, 24); m.visitVarInsn(DLOAD, 22); m.visitInsn(DCMPL);
        m.visitJumpInsn(IFLE, segmentNext);
        for (int bound = 0; bound < 2; bound++) {
            m.visitVarInsn(ALOAD, 15); m.visitVarInsn(ILOAD, 16); integer(m, bound); m.visitInsn(IADD);
            m.visitVarInsn(DLOAD, 22 + bound * 2);
            m.visitLdcInsn((double) s.scale()); m.visitInsn(DMUL); m.visitInsn(D2L); m.visitInsn(LASTORE);
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
        Label noConnection = new Label();
        m.visitVarInsn(ALOAD, 6); m.visitJumpInsn(IFNULL, noConnection);
        m.visitVarInsn(ALOAD, 6);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/net/HttpURLConnection", "disconnect", "()V", false);
        m.visitLabel(noConnection);
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

    // region Hook ownership and entry
    private static void controller(Path root, List<Hook> hooks, boolean seek, boolean members,
                                   boolean programMembers) throws IOException {
        ClassWriter w = writer(CTRL, "java/lang/Object");
        w.visitField(ACC_PRIVATE | ACC_STATIC, "handles", "[L" + HANDLE + ";", null, null).visitEnd();
        w.visitField(ACC_PUBLIC | ACC_STATIC | ACC_VOLATILE, "lastHook", "Ljava/lang/String;", null, null).visitEnd();
        w.visitField(ACC_PUBLIC | ACC_STATIC | ACC_VOLATILE, "failures", "I", null, null).visitEnd();
        w.visitField(ACC_PUBLIC | ACC_STATIC | ACC_VOLATILE, "failedGroups", "Ljava/lang/String;", null, null).visitEnd();
        MethodVisitor m = w.visitMethod(ACC_PUBLIC | ACC_STATIC | ACC_SYNCHRONIZED, "install",
                "(L" + ENTRY + ";Ljava/lang/ClassLoader;)V", null, new String[] {"java/lang/Throwable"});
        m.visitCode(); Label start = new Label();
        m.visitFieldInsn(GETSTATIC, CTRL, "handles", "[L" + HANDLE + ";");
        m.visitJumpInsn(IFNULL, start); m.visitInsn(RETURN); m.visitLabel(start);
        m.visitLdcInsn(""); m.visitFieldInsn(PUTSTATIC, CTRL, "failedGroups", "Ljava/lang/String;");
        // Locals: 0 entry, 1 loader, 2 class, 3.. executables, then handles and error.
        int handlesLocal = 3 + hooks.size();
        int errorLocal = handlesLocal + 1;
        integer(m, hooks.size()); m.visitTypeInsn(ANEWARRAY, HANDLE);
        m.visitVarInsn(ASTORE, handlesLocal);
        m.visitVarInsn(ALOAD, handlesLocal);
        m.visitFieldInsn(PUTSTATIC, CTRL, "handles", "[L" + HANDLE + ";");
        Set<String> groups = new LinkedHashSet<>();
        for (int phase = 0; phase <= 2; phase++) {
            for (Hook hook : hooks) if (hook.startup() == phase) groups.add(hook.group());
        }
        for (String group : groups) {
            Label groupStart = new Label(), groupEnd = new Label(), rollback = new Label(), done = new Label();
            m.visitTryCatchBlock(groupStart, groupEnd, rollback, "java/lang/Throwable");
            m.visitLabel(groupStart);
            Set<Integer> requiredMembers = new LinkedHashSet<>();
            boolean needsSeek = false, needsUi = false;
            for (int i = 0; i < hooks.size(); i++) {
                Hook h = hooks.get(i);
                if (!h.group().equals(group)) continue;
                m.visitLdcInsn(h.hookId()); m.visitFieldInsn(PUTSTATIC, CTRL, "lastHook", "Ljava/lang/String;");
                resolveMember(m, h.member(), 1, 2, 3 + i, "binding shape mismatch: " + h.hookId());
                for (int[] binding : AotHookCompiler.fieldBindings(h)) bindDeclaredField(m, h, 2, binding);
                if (AotHookCompiler.uses(h, 38)) needsSeek = true;
                if (AotHookCompiler.uses(h, 21)) needsUi = true;
                for (int index : AotHookCompiler.members(h)) requiredMembers.add(index);
            }
            if (seek && needsSeek) {
                m.visitLdcInsn("range-service seek binding"); m.visitFieldInsn(PUTSTATIC, CTRL, "lastHook", "Ljava/lang/String;");
                m.visitVarInsn(ALOAD, 1);
                m.visitMethodInsn(INVOKESTATIC, SEEK, "bind", "(Ljava/lang/ClassLoader;)V", false);
            }
            if (members && needsUi) {
                m.visitLdcInsn("ui member bindings"); m.visitFieldInsn(PUTSTATIC, CTRL, "lastHook", "Ljava/lang/String;");
                m.visitVarInsn(ALOAD, 1);
                m.visitMethodInsn(INVOKESTATIC, UiModelGenerator.MEMBERS, "bind", "(Ljava/lang/ClassLoader;)V", false);
            }
            if (programMembers) for (int index : requiredMembers) {
                m.visitLdcInsn("program member binding"); m.visitFieldInsn(PUTSTATIC, CTRL, "lastHook", "Ljava/lang/String;");
                m.visitVarInsn(ALOAD, 1); integer(m, index);
                m.visitMethodInsn(INVOKESTATIC, PROGRAM_MEMBERS, "bind", "(Ljava/lang/ClassLoader;I)V", false);
            }
            for (int i = 0; i < hooks.size(); i++) {
                Hook h = hooks.get(i);
                if (!h.group().equals(group)) continue;
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
                String hooker = P + "ProgramHooker" + i;
                m.visitTypeInsn(NEW, hooker); m.visitInsn(DUP);
                m.visitMethodInsn(INVOKESPECIAL, hooker, "<init>", "()V", false);
                m.visitMethodInsn(INVOKEINTERFACE, BUILDER, "intercept",
                        "(L" + HOOKER + ";)L" + HANDLE + ";", true);
                m.visitInsn(AASTORE);
            }
            m.visitLabel(groupEnd); m.visitJumpInsn(GOTO, done);
            m.visitLabel(rollback); m.visitVarInsn(ASTORE, errorLocal);
            m.visitFieldInsn(GETSTATIC, CTRL, "failures", "I"); m.visitInsn(ICONST_1); m.visitInsn(IADD);
            m.visitFieldInsn(PUTSTATIC, CTRL, "failures", "I");
            m.visitTypeInsn(NEW, "java/lang/StringBuilder"); m.visitInsn(DUP);
            m.visitFieldInsn(GETSTATIC, CTRL, "failedGroups", "Ljava/lang/String;");
            m.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false);
            m.visitLdcInsn("|" + group + "|");
            m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
            m.visitFieldInsn(PUTSTATIC, CTRL, "failedGroups", "Ljava/lang/String;");
            for (int i = 0; i < hooks.size(); i++) {
                if (!hooks.get(i).group().equals(group)) continue;
                Label unhookStart = new Label(), unhookEnd = new Label(), unhookFail = new Label(), skip = new Label();
                m.visitTryCatchBlock(unhookStart, unhookEnd, unhookFail, "java/lang/Throwable");
                m.visitLabel(unhookStart);
                m.visitVarInsn(ALOAD, handlesLocal); integer(m, i); m.visitInsn(AALOAD);
                m.visitJumpInsn(IFNULL, unhookEnd);
                m.visitVarInsn(ALOAD, handlesLocal); integer(m, i); m.visitInsn(AALOAD);
                m.visitMethodInsn(INVOKEINTERFACE, HANDLE, "unhook", "()V", true);
                m.visitVarInsn(ALOAD, handlesLocal); integer(m, i); m.visitInsn(ACONST_NULL); m.visitInsn(AASTORE);
                m.visitLabel(unhookEnd); m.visitJumpInsn(GOTO, skip);
                m.visitLabel(unhookFail); m.visitInsn(POP); m.visitLabel(skip);
            }
            m.visitLdcInsn(LOG_TAG); m.visitLdcInsn("hook group " + group + " disabled");
            m.visitVarInsn(ALOAD, errorLocal);
            m.visitMethodInsn(INVOKESTATIC, "android/util/Log", "e",
                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I", false);
            m.visitInsn(POP); m.visitLabel(done);
        }
        m.visitInsn(ACONST_NULL); m.visitFieldInsn(PUTSTATIC, CTRL, "lastHook", "Ljava/lang/String;");
        m.visitInsn(RETURN);
        m.visitMaxs(0, 0); m.visitEnd();

        m = w.visitMethod(ACC_PUBLIC | ACC_STATIC | ACC_SYNCHRONIZED, "uninstall", "()V", null, null);
        m.visitCode();
        for (int i = 0; i < hooks.size(); i++) {
            Label begin = new Label(), end = new Label(), failed = new Label(), next = new Label();
            m.visitTryCatchBlock(begin, end, failed, "java/lang/Throwable");
            m.visitLabel(begin); m.visitFieldInsn(GETSTATIC, CTRL, "handles", "[L" + HANDLE + ";");
            m.visitJumpInsn(IFNULL, end); m.visitFieldInsn(GETSTATIC, CTRL, "handles", "[L" + HANDLE + ";");
            integer(m, i); m.visitInsn(AALOAD); m.visitJumpInsn(IFNULL, end);
            m.visitFieldInsn(GETSTATIC, CTRL, "handles", "[L" + HANDLE + ";"); integer(m, i);
            m.visitInsn(AALOAD); m.visitMethodInsn(INVOKEINTERFACE, HANDLE, "unhook", "()V", true);
            m.visitLabel(end); m.visitJumpInsn(GOTO, next);
            m.visitLabel(failed); m.visitInsn(POP); m.visitLabel(next);
        }
        m.visitInsn(ACONST_NULL); m.visitFieldInsn(PUTSTATIC, CTRL, "handles", "[L" + HANDLE + ";");
        m.visitInsn(ICONST_0); m.visitFieldInsn(PUTSTATIC, CTRL, "failures", "I");
        m.visitLdcInsn(""); m.visitFieldInsn(PUTSTATIC, CTRL, "failedGroups", "Ljava/lang/String;");
        m.visitInsn(ACONST_NULL); m.visitFieldInsn(PUTSTATIC, CTRL, "lastHook", "Ljava/lang/String;");
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();

        m = w.visitMethod(ACC_PUBLIC | ACC_STATIC, "unhookOld", "(Ljava/util/List;)V", null, null);
        m.visitCode(); m.visitInsn(ICONST_0); m.visitVarInsn(ISTORE, 1);
        Label loop = new Label(), done = new Label(); m.visitLabel(loop);
        m.visitVarInsn(ILOAD, 1); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "size", "()I", true);
        m.visitJumpInsn(IF_ICMPGE, done);
        Label begin = new Label(), end = new Label(), failed = new Label(), next = new Label();
        m.visitTryCatchBlock(begin, end, failed, "java/lang/Throwable");
        m.visitLabel(begin); m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ILOAD, 1);
        m.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
        m.visitTypeInsn(CHECKCAST, HANDLE); m.visitMethodInsn(INVOKEINTERFACE, HANDLE, "unhook", "()V", true);
        m.visitLabel(end); m.visitJumpInsn(GOTO, next);
        m.visitLabel(failed); m.visitInsn(POP); m.visitLabel(next);
        m.visitIincInsn(1, 1); m.visitJumpInsn(GOTO, loop); m.visitLabel(done);
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();
        save(root, CTRL, w);
    }
    private static void entry(Path root, List<Config> configs, String authorityDigest,
                              int[] resolverWeights, Diagnostic diagnostic) throws IOException {
        ClassWriter w = writer(ENTRY, XPOSED);
        w.visitField(ACC_PRIVATE, "listener", "L" + LISTENER + ";", null, null).visitEnd();
        w.visitField(ACC_PRIVATE, "preferences", "[L" + PREFS + ";", null, null).visitEnd();
        w.visitField(ACC_PRIVATE, "application", "Landroid/content/pm/ApplicationInfo;", null, null).visitEnd();
        w.visitField(ACC_PRIVATE, "loader", "Ljava/lang/ClassLoader;", null, null).visitEnd();
        ctor(w, XPOSED);
        MethodVisitor m = w.visitMethod(ACC_PRIVATE, "start",
                "(Landroid/content/pm/ApplicationInfo;Ljava/lang/ClassLoader;)V", null, null);
        m.visitCode();
        Set<String> groups = new LinkedHashSet<>();
        for (Config c : configs) groups.add(c.group());
        Label start = new Label(), end = new Label(), fail = new Label(), done = new Label();
        m.visitTryCatchBlock(start, end, fail, "java/lang/Throwable");
        m.visitLabel(start);
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 1);
        m.visitFieldInsn(PUTFIELD, ENTRY, "application", "Landroid/content/pm/ApplicationInfo;");
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 2);
        m.visitFieldInsn(PUTFIELD, ENTRY, "loader", "Ljava/lang/ClassLoader;");
        for (int weight : resolverWeights) integer(m, weight);
        m.visitMethodInsn(INVOKESTATIC, RESOLVER, "configure", "(IIIIIII)V", false);
        m.visitVarInsn(ALOAD, 1);
        m.visitLdcInsn(authorityDigest);
        m.visitMethodInsn(INVOKESTATIC, RESOLVER, "initialize",
                "(Landroid/content/pm/ApplicationInfo;Ljava/lang/String;)V", false);
        m.visitLdcInsn(diagnostic.checkingState()); m.visitLdcInsn(diagnostic.checkingText()); m.visitLdcInsn("");
        m.visitMethodInsn(INVOKESTATIC, DIAG, "report",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false);
        m.visitVarInsn(ALOAD, 0); m.visitTypeInsn(NEW, LISTENER); m.visitInsn(DUP);
        m.visitMethodInsn(INVOKESPECIAL, LISTENER, "<init>", "()V", false);
        m.visitFieldInsn(PUTFIELD, ENTRY, "listener", "L" + LISTENER + ";");
        m.visitVarInsn(ALOAD, 0); integer(m, groups.size()); m.visitTypeInsn(ANEWARRAY, PREFS);
        m.visitFieldInsn(PUTFIELD, ENTRY, "preferences", "[L" + PREFS + ";");
        int groupIndex = 0;
        for (String group : groups) {
            m.visitVarInsn(ALOAD, 0); m.visitLdcInsn(group);
            m.visitMethodInsn(INVOKEVIRTUAL, ENTRY, "getRemotePreferences",
                    "(Ljava/lang/String;)L" + PREFS + ";", false);
            m.visitVarInsn(ASTORE, 3);
            m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, ENTRY, "preferences", "[L" + PREFS + ";");
            integer(m, groupIndex++); m.visitVarInsn(ALOAD, 3); m.visitInsn(AASTORE);
            for (int i = 0; i < configs.size(); i++) {
                Config c = configs.get(i);
                if (!c.group().equals(group)) continue;
                integer(m, i); m.visitVarInsn(ALOAD, 3); m.visitLdcInsn(c.key());
                m.visitInsn(c.fallback() ? ICONST_1 : ICONST_0);
                m.visitMethodInsn(INVOKEINTERFACE, PREFS, "getBoolean", "(Ljava/lang/String;Z)Z", true);
                m.visitMethodInsn(INVOKESTATIC, SNAP, "publish", "(IZ)V", false);
            }
            m.visitVarInsn(ALOAD, 3); m.visitVarInsn(ALOAD, 0);
            m.visitFieldInsn(GETFIELD, ENTRY, "listener", "L" + LISTENER + ";");
            m.visitMethodInsn(INVOKEINTERFACE, PREFS, "registerOnSharedPreferenceChangeListener",
                    "(L" + PREF_LISTENER + ";)V", true);
        }
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 2);
        m.visitMethodInsn(INVOKESTATIC, CTRL, "install",
                "(L" + ENTRY + ";Ljava/lang/ClassLoader;)V", false);
        m.visitMethodInsn(INVOKESTATIC, RESOLVER, "finish", "()V", false);
        Label allReady = new Label(), statusDone = new Label();
        m.visitFieldInsn(GETSTATIC, CTRL, "failures", "I"); m.visitJumpInsn(IFEQ, allReady);
        m.visitLdcInsn(diagnostic.degradedState()); m.visitLdcInsn(diagnostic.degradedText());
        m.visitFieldInsn(GETSTATIC, CTRL, "failedGroups", "Ljava/lang/String;");
        m.visitMethodInsn(INVOKESTATIC, DIAG, "report",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false);
        log(m, "w", "module loaded with disabled hook groups");
        m.visitJumpInsn(GOTO, statusDone);
        m.visitLabel(allReady);
        m.visitLdcInsn(diagnostic.readyState()); m.visitLdcInsn(diagnostic.readyText()); m.visitLdcInsn("");
        m.visitMethodInsn(INVOKESTATIC, DIAG, "report",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false);
        log(m, "i", "module hooks installed");
        m.visitLabel(statusDone);
        m.visitLabel(end); m.visitLabel(done); m.visitInsn(RETURN);
        m.visitLabel(fail); m.visitVarInsn(ASTORE, 4);
        m.visitMethodInsn(INVOKESTATIC, RESOLVER, "finish", "()V", false);
        m.visitVarInsn(ALOAD, 0); m.visitMethodInsn(INVOKESPECIAL, ENTRY, "detach", "()V", false);
        m.visitMethodInsn(INVOKESTATIC, CTRL, "uninstall", "()V", false);
        m.visitMethodInsn(INVOKESTATIC, SNAP, "disableAll", "()V", false);
        m.visitLdcInsn(diagnostic.failedState());
        m.visitTypeInsn(NEW, "java/lang/StringBuilder"); m.visitInsn(DUP);
        m.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        m.visitFieldInsn(GETSTATIC, CTRL, "lastHook", "Ljava/lang/String;");
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        m.visitLdcInsn(": ");
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        m.visitVarInsn(ALOAD, 4);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
        m.visitLdcInsn("");
        m.visitMethodInsn(INVOKESTATIC, DIAG, "report",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false);
        m.visitLdcInsn(LOG_TAG); m.visitLdcInsn("hook installation failed");
        m.visitVarInsn(ALOAD, 4);
        m.visitMethodInsn(INVOKESTATIC, "android/util/Log", "e",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I", false);
        m.visitInsn(POP); m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();

        m = w.visitMethod(ACC_PRIVATE, "detach", "()V", null, null); m.visitCode();
        groupIndex = 0;
        for (String ignored : groups) {
            Label begin = new Label(), cleanupEnd = new Label(), failed = new Label(), next = new Label();
            m.visitTryCatchBlock(begin, cleanupEnd, failed, "java/lang/Throwable");
            m.visitLabel(begin); m.visitVarInsn(ALOAD, 0);
            m.visitFieldInsn(GETFIELD, ENTRY, "preferences", "[L" + PREFS + ";");
            m.visitJumpInsn(IFNULL, cleanupEnd); m.visitVarInsn(ALOAD, 0);
            m.visitFieldInsn(GETFIELD, ENTRY, "preferences", "[L" + PREFS + ";");
            integer(m, groupIndex++); m.visitInsn(AALOAD); m.visitJumpInsn(IFNULL, cleanupEnd);
            m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, ENTRY, "preferences", "[L" + PREFS + ";");
            integer(m, groupIndex - 1); m.visitInsn(AALOAD); m.visitVarInsn(ALOAD, 0);
            m.visitFieldInsn(GETFIELD, ENTRY, "listener", "L" + LISTENER + ";");
            m.visitMethodInsn(INVOKEINTERFACE, PREFS, "unregisterOnSharedPreferenceChangeListener",
                    "(L" + PREF_LISTENER + ";)V", true);
            m.visitLabel(cleanupEnd); m.visitJumpInsn(GOTO, next);
            m.visitLabel(failed); m.visitInsn(POP); m.visitLabel(next);
        }
        m.visitVarInsn(ALOAD, 0); m.visitInsn(ACONST_NULL);
        m.visitFieldInsn(PUTFIELD, ENTRY, "preferences", "[L" + PREFS + ";");
        m.visitVarInsn(ALOAD, 0); m.visitInsn(ACONST_NULL);
        m.visitFieldInsn(PUTFIELD, ENTRY, "listener", "L" + LISTENER + ";");
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();

        m = w.visitMethod(ACC_PUBLIC, "onPackageReady", "(L" + PARAM + ";)V", null, null); m.visitCode();
        Label packageDone = new Label(); m.visitLdcInsn(diagnostic.targetPackage()); m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKEINTERFACE, PARAM, "getPackageName", "()Ljava/lang/String;", true);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        m.visitJumpInsn(IFEQ, packageDone); m.visitLdcInsn(diagnostic.targetPackage());
        m.visitMethodInsn(INVOKESTATIC, "android/app/Application", "getProcessName", "()Ljava/lang/String;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        m.visitJumpInsn(IFEQ, packageDone); m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKEINTERFACE, PARAM, "getApplicationInfo", "()Landroid/content/pm/ApplicationInfo;", true);
        m.visitVarInsn(ALOAD, 1);
        // getDefaultClassLoader is the target loader; PackageReadyParam#getClassLoader is the component loader.
        m.visitMethodInsn(INVOKEINTERFACE, PARAM, "getDefaultClassLoader", "()Ljava/lang/ClassLoader;", true);
        m.visitMethodInsn(INVOKESPECIAL, ENTRY, "start",
                "(Landroid/content/pm/ApplicationInfo;Ljava/lang/ClassLoader;)V", false);
        m.visitLabel(packageDone); m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();

        m = w.visitMethod(ACC_PUBLIC, "onHotReloading", "(L" + HOT_RELOADING + ";)Z", null, null); m.visitCode();
        Label inactive = new Label(), saveStart = new Label(), saveEnd = new Label();
        Label stateFailed = new Label(), stateReady = new Label();
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, ENTRY, "application", "Landroid/content/pm/ApplicationInfo;");
        m.visitJumpInsn(IFNULL, inactive); m.visitTryCatchBlock(saveStart, saveEnd, stateFailed, "java/lang/Throwable");
        m.visitLabel(saveStart); m.visitVarInsn(ALOAD, 1); m.visitInsn(ICONST_2);
        m.visitTypeInsn(ANEWARRAY, "java/lang/Object"); m.visitInsn(DUP); m.visitInsn(ICONST_0);
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, ENTRY, "application", "Landroid/content/pm/ApplicationInfo;");
        m.visitInsn(AASTORE); m.visitInsn(DUP); m.visitInsn(ICONST_1); m.visitVarInsn(ALOAD, 0);
        m.visitFieldInsn(GETFIELD, ENTRY, "loader", "Ljava/lang/ClassLoader;"); m.visitInsn(AASTORE);
        m.visitMethodInsn(INVOKEINTERFACE, HOT_RELOADING, "setSavedInstanceState", "(Ljava/lang/Object;)V", true);
        m.visitLabel(saveEnd); m.visitJumpInsn(GOTO, stateReady);
        m.visitLabel(stateFailed); m.visitInsn(POP); m.visitInsn(ICONST_0); m.visitInsn(IRETURN);
        m.visitLabel(stateReady);
        m.visitVarInsn(ALOAD, 0); m.visitMethodInsn(INVOKESPECIAL, ENTRY, "detach", "()V", false);
        m.visitMethodInsn(INVOKESTATIC, CTRL, "uninstall", "()V", false);
        m.visitMethodInsn(INVOKESTATIC, RESOLVER, "finish", "()V", false);
        m.visitMethodInsn(INVOKESTATIC, SNAP, "disableAll", "()V", false);
        m.visitLabel(inactive); m.visitInsn(ICONST_1); m.visitInsn(IRETURN);
        m.visitMaxs(0, 0); m.visitEnd();

        m = w.visitMethod(ACC_PUBLIC, "onHotReloaded", "(L" + HOT_RELOADED + ";)V", null, null); m.visitCode();
        m.visitVarInsn(ALOAD, 1); m.visitMethodInsn(INVOKEINTERFACE, HOT_RELOADED, "getOldHookHandles",
                "()Ljava/util/List;", true); m.visitMethodInsn(INVOKESTATIC, CTRL, "unhookOld", "(Ljava/util/List;)V", false);
        m.visitVarInsn(ALOAD, 1); m.visitMethodInsn(INVOKEINTERFACE, HOT_RELOADED, "getSavedInstanceState",
                "()Ljava/lang/Object;", true); m.visitVarInsn(ASTORE, 2);
        Label invalid = new Label(); m.visitVarInsn(ALOAD, 2); m.visitTypeInsn(INSTANCEOF, "[Ljava/lang/Object;");
        m.visitJumpInsn(IFEQ, invalid); m.visitVarInsn(ALOAD, 2); m.visitTypeInsn(CHECKCAST, "[Ljava/lang/Object;");
        m.visitVarInsn(ASTORE, 3); m.visitVarInsn(ALOAD, 3); m.visitInsn(ARRAYLENGTH); m.visitInsn(ICONST_2);
        m.visitJumpInsn(IF_ICMPNE, invalid); m.visitVarInsn(ALOAD, 3); m.visitInsn(ICONST_0); m.visitInsn(AALOAD);
        m.visitTypeInsn(INSTANCEOF, "android/content/pm/ApplicationInfo"); m.visitJumpInsn(IFEQ, invalid);
        m.visitVarInsn(ALOAD, 3); m.visitInsn(ICONST_1); m.visitInsn(AALOAD);
        m.visitTypeInsn(INSTANCEOF, "java/lang/ClassLoader"); m.visitJumpInsn(IFEQ, invalid);
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 3); m.visitInsn(ICONST_0); m.visitInsn(AALOAD);
        m.visitTypeInsn(CHECKCAST, "android/content/pm/ApplicationInfo"); m.visitVarInsn(ALOAD, 3);
        m.visitInsn(ICONST_1); m.visitInsn(AALOAD); m.visitTypeInsn(CHECKCAST, "java/lang/ClassLoader");
        m.visitMethodInsn(INVOKESPECIAL, ENTRY, "start",
                "(Landroid/content/pm/ApplicationInfo;Ljava/lang/ClassLoader;)V", false);
        m.visitInsn(RETURN); m.visitLabel(invalid); m.visitMethodInsn(INVOKESTATIC, SNAP, "disableAll", "()V", false);
        m.visitLdcInsn(diagnostic.failedState()); m.visitLdcInsn("hot reload state unavailable"); m.visitLdcInsn("");
        m.visitMethodInsn(INVOKESTATIC, DIAG, "report", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false);
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();
        save(root, ENTRY, w);
    }

    // endregion

    // region Plan

    private static Member member(String[] f, int at) {
        return new Member(f[at], f[at + 1], f[at + 2], f[at + 3], Integer.parseInt(f[at + 4]),
                f[at + 5], Integer.parseInt(f[at + 6]));
    }
    /**
     * Reads the tab-separated plan written by {@code build_development.py}:
     * {@code target}, {@code config}, {@code hook}, {@code seek},
     * {@code segments} and {@code category} lines.
     */
    private static Plan readPlan(Path file) throws IOException {
        String hash = null; Seek seek = null; String[] segmentLine = null;
        int[] resolverWeights = null;
        Diagnostic diagnostic = null;
        List<Config> configs = new ArrayList<>();
        List<Hook> hooks = new ArrayList<>();
        List<Category> categories = new ArrayList<>();
        List<Member> programMembers = new ArrayList<>();
        UiModelGenerator.Builder settings = new UiModelGenerator.Builder();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isEmpty()) continue;
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "target" -> hash = f[1];
                case "resolver" -> {
                    if (f.length != 8) throw new IllegalArgumentException("resolver policy width");
                    resolverWeights = new int[7];
                    for (int i = 0; i < 7; i++) resolverWeights[i] = Integer.parseInt(f[i + 1]);
                }
                case "diagnostic" -> diagnostic = new Diagnostic(f[1], f[2], f[3], f[4], f[5], f[6],
                        f[7], f[8], f[9], f[10], f[11], f[12], f[13], f[14], f[15], f[16], f[17],
                        f[18], f[19], f[20], f[21], f[22]);
                case "config" -> configs.add(new Config(f[1], f[2], Boolean.parseBoolean(f[3])));
                case "hook" -> hooks.add(new Hook(member(f, 1), Integer.parseInt(f[8]), f[9], f[10],
                        Integer.parseInt(f[12]), List.of(f).subList(11, f.length)));
                case "seek" -> {
                    if (seek != null) throw new IllegalArgumentException("duplicate seek");
                    seek = new Seek(member(f, 1), f[8]);
                }
                case "segments" -> segmentLine = f;
                case "category" -> categories.add(new Category(f[1], Integer.parseInt(f[2])));
                case "pmember" -> programMembers.add(member(f, 2));
                case "member", "role", "page", "layouts", "section", "item" -> settings.accept(f);
                default -> throw new IllegalArgumentException("unknown plan line " + f[0]);
            }
        }
        for (Hook hook : hooks) {
            if (hook.group().isEmpty() || hook.startup() < 0 || hook.startup() > 2) {
                throw new IllegalArgumentException("invalid hook group/startup policy");
            }
            if (hooks.stream().anyMatch(other -> other.group().equals(hook.group())
                    && other.startup() != hook.startup())) {
                throw new IllegalArgumentException("hook group spans startup phases: " + hook.group());
            }
        }
        if (hash == null || resolverWeights == null || configs.isEmpty() || hooks.isEmpty() || diagnostic == null
                || !diagnostic.id().equals("hook_compatibility")
                || !diagnostic.transport().equals("EXPLICIT_BROADCAST")
                || !diagnostic.failureMode().equals("GROUP_ROLLBACK_FAIL_OPEN")
                || diagnostic.targetPackage().isEmpty()) {
            throw new IllegalArgumentException("incomplete plan");
        }
        Segments segments = null;
        if (segmentLine != null) {
            segments = new Segments(segmentLine[1], segmentLine[2], Integer.parseInt(segmentLine[3]),
                    Integer.parseInt(segmentLine[4]), Integer.parseInt(segmentLine[5]),
                    Integer.parseInt(segmentLine[6]), Integer.parseInt(segmentLine[7]), segmentLine[8],
                    segmentLine[9], segmentLine[10], segmentLine[11], segmentLine[12], segmentLine[13],
                    segmentLine[14], segmentLine[15], Integer.parseInt(segmentLine[16]),
                    Boolean.parseBoolean(segmentLine[17]), Integer.parseInt(segmentLine[18]),
                    List.copyOf(categories));
        }
        boolean range = hooks.stream().anyMatch(h -> AotHookCompiler.uses(h, 44));
        if (range != (segments != null) || range != (seek != null)
                || (segments != null && categories.isEmpty())) {
            throw new IllegalArgumentException("segment skipping plan is incomplete");
        }
        UiModelGenerator.Settings built = settings.build();
        boolean injects = hooks.stream().anyMatch(h -> AotHookCompiler.uses(h, 21));
        if (injects != (built != null)) throw new IllegalArgumentException("settings plan is incomplete");
        return new Plan(hash, configs, hooks, seek, segments, built, diagnostic,
                List.copyOf(programMembers), resolverWeights);
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: GenerateModule <out> <plan>");
        Path root = Path.of(args[0]);
        Plan plan = readPlan(Path.of(args[1]));
        snapshot(root, plan.configs().size()); listener(root, plan.configs());
        for (int i = 0; i < plan.hooks().size(); i++) hooker(root, i, plan.hooks().get(i));
        if (plan.seek() != null) {
            stateSlots(root, 4); seekPort(root, plan.seek()); segmentStore(root); segmentFetch(root, plan.segments());
        }
        if (plan.settings() != null) UiModelGenerator.generate(root, plan.settings(), plan.configs(),
                plan.hooks(), plan.diagnostic());
        if (!plan.programMembers().isEmpty()) ProgramMembersGenerator.generate(root, plan.programMembers());
        controller(root, plan.hooks(), plan.seek() != null,
                plan.settings() != null, !plan.programMembers().isEmpty());
        StatusTransportGenerator.generate(root, plan.diagnostic());
        entry(root, plan.configs(), plan.hash(), plan.resolverWeights(), plan.diagnostic());
    }

    // endregion
}
