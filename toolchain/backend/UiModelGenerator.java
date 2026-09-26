package toolchain.backend;

import static toolchain.backend.GenerateModule.LOG_TAG;
import static toolchain.backend.GenerateModule.P;
import static toolchain.backend.GenerateModule.ctor;
import static toolchain.backend.GenerateModule.integer;
import static toolchain.backend.GenerateModule.loadClass;
import static toolchain.backend.GenerateModule.resolveMember;
import static toolchain.backend.GenerateModule.save;
import static toolchain.backend.GenerateModule.throwState;
import static toolchain.backend.GenerateModule.typeClass;
import static toolchain.backend.GenerateModule.writer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Compiles a validated {@code SettingsPage} into two surfaces: one entry injected
 * into YouTube's settings screen (target process, read-only) and the module's manager
 * Activity, the only writer of RemotePreferences (through libxposed service 102).
 */
final class UiModelGenerator implements Opcodes {
    static final String MEMBERS = P + "TargetMembers";
    static final String ENTRY_UI = P + "SettingsEntry";
    private static final String MANAGER = P + "ManagerActivity";
    private static final String SERVICE = P + "ManagerService";
    private static final String TOGGLE = P + "ManagerToggle";
    private static final String XSERVICE = "io/github/libxposed/service/XposedService";
    private static final String XHELPER = "io/github/libxposed/service/XposedServiceHelper";
    private static final String XLISTENER = XHELPER + "$OnServiceListener";
    private static final String PREFS = "android/content/SharedPreferences";
    private static final String EDITOR = PREFS + "$Editor";
    private static final String CHECK_LISTENER = "android/widget/CompoundButton$OnCheckedChangeListener";
    private static final String OBJECTS = "[Ljava/lang/Object;";
    private static final Set<String> ROLES = Set.of("owner", "screen", "context", "intent", "layout",
            "icon_space", "set_icon", "new_preference", "set_key", "set_title", "set_summary", "set_order",
            "group_add", "group_find");
    record TargetMember(String id, GenerateModule.Member member) {}
    record Page(String key, String title, int order, String packageName, String summary, String icon,
                String note, String clipboardLabel, String reportPrefix) {}
    record Section(String id, String title) {}
    record Item(String section, int configIndex, String title) {}
    record Settings(List<TargetMember> members, Map<String, Integer> roles, Page page, String layouts,
                    List<Section> sections, List<Item> items) {}

    /** Collects {@code member}, {@code role}, {@code page}, {@code section} and {@code item} plan lines. */
    static final class Builder {
        private final List<TargetMember> members = new ArrayList<>();
        private final Map<String, String> roles = new HashMap<>();
        private final List<Section> sections = new ArrayList<>();
        private final List<Item> items = new ArrayList<>();
        private Page page;
        private String layouts;

        void accept(String[] f) {
            switch (f[0]) {
                case "member" -> members.add(new TargetMember(f[1], new GenerateModule.Member(
                        f[2], f[3], f[4], f[5], Integer.parseInt(f[6]), f[7], Integer.parseInt(f[8]))));
                case "role" -> roles.put(f[1], f[2]);
                case "page" -> page = new Page(f[1], f[2], Integer.parseInt(f[3]), f[4], f[5], f[6],
                        f[7], f[8], f[9]);
                case "layouts" -> layouts = f[1];
                case "section" -> sections.add(new Section(f[1], f[2]));
                case "item" -> items.add(new Item(f[1], Integer.parseInt(f[2]), f[3]));
                default -> throw new IllegalArgumentException(f[0]);
            }
        }

        Settings build() {
            if (page == null && layouts == null && members.isEmpty() && roles.isEmpty() && sections.isEmpty()
                    && items.isEmpty()) {
                return null;
            }
            if (page == null || layouts == null || sections.isEmpty() || items.isEmpty() || !roles.keySet().equals(ROLES)) {
                throw new IllegalArgumentException("incomplete settings plan");
            }
            Map<String, Integer> index = new HashMap<>();
            for (int i = 0; i < members.size(); i++) index.put(members.get(i).id(), i);
            Map<String, Integer> resolved = new HashMap<>();
            roles.forEach((role, id) -> {
                Integer at = index.get(id);
                if (at == null) throw new IllegalArgumentException("unbound settings role " + role);
                resolved.put(role, at);
            });
            List<String> ids = sections.stream().map(Section::id).toList();
            if (items.stream().anyMatch(item -> !ids.contains(item.section()))) {
                throw new IllegalArgumentException("item outside settings sections");
            }
            if (!page.icon().equals("module")) {
                throw new IllegalArgumentException("unsupported settings entry icon");
            }
            return new Settings(List.copyOf(members), Map.copyOf(resolved), page, layouts,
                    List.copyOf(sections),
                    List.copyOf(items));
        }
    }

    private UiModelGenerator() {}

    static void generate(Path root, Settings s, List<GenerateModule.Config> configs,
                         List<GenerateModule.Hook> hooks,
                         GenerateModule.Diagnostic diagnostic) throws IOException {
        targetMembers(root, s.members());
        settingsEntry(root, s);
        managerActivity(root, s, configs, hooks, diagnostic);
        managerService(root);
        managerToggle(root);
    }

    // region Target process

    /** Cold-resolved table of target members used by the entry section. */
    private static void targetMembers(Path root, List<TargetMember> members) throws IOException {
        ClassWriter w = writer(MEMBERS, "java/lang/Object");
        w.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "members", OBJECTS, null, null).visitEnd();
        MethodVisitor m = w.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        m.visitCode(); integer(m, members.size()); m.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        m.visitFieldInsn(PUTSTATIC, MEMBERS, "members", OBJECTS);
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();

        // bind(ClassLoader): locals 0 loader, 1 class, 2 member.
        m = w.visitMethod(ACC_STATIC, "bind", "(Ljava/lang/ClassLoader;)V", null, new String[] {"java/lang/Throwable"});
        m.visitCode();
        for (int i = 0; i < members.size(); i++) {
            TargetMember t = members.get(i);
            String failure = "binding shape mismatch: " + t.id();
            if (t.member().kind().equals("FIELD")) resolveField(m, t.member(), failure);
            else resolveMember(m, t.member(), 0, 1, 2, failure);
            m.visitFieldInsn(GETSTATIC, MEMBERS, "members", OBJECTS);
            integer(m, i); m.visitVarInsn(ALOAD, 2); m.visitInsn(AASTORE);
        }
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();

        accessor(w, "invoke", "(ILjava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                "java/lang/reflect/Method", "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", 2);
        accessor(w, "create", "(I[Ljava/lang/Object;)Ljava/lang/Object;",
                "java/lang/reflect/Constructor", "newInstance", "([Ljava/lang/Object;)Ljava/lang/Object;", 1);
        accessor(w, "read", "(ILjava/lang/Object;)Ljava/lang/Object;",
                "java/lang/reflect/Field", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", 1);
        accessor(w, "write", "(ILjava/lang/Object;Ljava/lang/Object;)V",
                "java/lang/reflect/Field", "set", "(Ljava/lang/Object;Ljava/lang/Object;)V", 2);
        save(root, MEMBERS, w);
    }

    /** {@code static R name(int i, a1..aN) = ((Type) members[i]).call(a1..aN)}. */
    private static void accessor(ClassWriter w, String name, String descriptor, String type, String call,
                                 String callDescriptor, int arguments) {
        MethodVisitor m = w.visitMethod(ACC_STATIC, name, descriptor, null, new String[] {"java/lang/Throwable"});
        m.visitCode();
        m.visitFieldInsn(GETSTATIC, MEMBERS, "members", OBJECTS);
        m.visitVarInsn(ILOAD, 0); m.visitInsn(AALOAD); m.visitTypeInsn(CHECKCAST, type);
        for (int a = 1; a <= arguments; a++) m.visitVarInsn(ALOAD, a);
        m.visitMethodInsn(INVOKEVIRTUAL, type, call, callDescriptor, false);
        m.visitInsn(Type.getReturnType(descriptor).getSort() == Type.VOID ? RETURN : ARETURN);
        m.visitMaxs(0, 0); m.visitEnd();
    }

    /** Resolves an exact field into local 2 (class in 1, loader in 0) and makes it accessible. */
    private static void resolveField(MethodVisitor m, GenerateModule.Member b, String failure) {
        Label ready = new Label();
        m.visitVarInsn(ALOAD, 0); m.visitLdcInsn(b.owner()); m.visitLdcInsn(b.name());
        m.visitLdcInsn(b.descriptor()); integer(m, b.access()); m.visitLdcInsn(b.superclass());
        m.visitMethodInsn(INVOKESTATIC, GenerateModule.RESOLVER, "field",
                "(Ljava/lang/ClassLoader;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;)Ljava/lang/reflect/Field;",
                false);
        m.visitInsn(DUP); m.visitJumpInsn(IFNONNULL, ready); m.visitInsn(POP); throwState(m, failure);
        m.visitLabel(ready); m.visitVarInsn(ASTORE, 2);
    }

    private interface Push { void emit(MethodVisitor m); }

    private static void objects(MethodVisitor m, Push... values) {
        integer(m, values.length); m.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        for (int i = 0; i < values.length; i++) {
            m.visitInsn(DUP); integer(m, i); values[i].emit(m); m.visitInsn(AASTORE);
        }
    }
    private static Push text(String value) { return m -> m.visitLdcInsn(value); }
    private static Push local(int slot) { return m -> m.visitVarInsn(ALOAD, slot); }
    private static Push boxed(int value) {
        return m -> {
            integer(m, value);
            m.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        };
    }
    private static void invoke(MethodVisitor m, Settings s, String role, int receiver, Push... args) {
        integer(m, s.roles().get(role)); m.visitVarInsn(ALOAD, receiver); objects(m, args);
        m.visitMethodInsn(INVOKESTATIC, MEMBERS, "invoke",
                "(ILjava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
    }
    private static void create(MethodVisitor m, Settings s, String role, Push... args) {
        integer(m, s.roles().get(role)); objects(m, args);
        m.visitMethodInsn(INVOKESTATIC, MEMBERS, "create", "(I[Ljava/lang/Object;)Ljava/lang/Object;", false);
    }
    private static void describe(MethodVisitor m, Settings s, int preference, String key, String title, int order) {
        invoke(m, s, "set_key", preference, text(key)); m.visitInsn(POP);
        invoke(m, s, "set_title", preference, text(title)); m.visitInsn(POP);
        invoke(m, s, "set_order", preference, boxed(order)); m.visitInsn(POP);
    }

    /** Adds one YouTube-styled row above Account. Idempotent per rebuilt screen. */
    private static void settingsEntry(Path root, Settings s) throws IOException {
        ClassWriter w = writer(ENTRY_UI, "java/lang/Object");
        // inject(Object builder): locals 0 builder, 1 context, 3 row, 4 intent, 5 error,
        // 6 screen fragment, 7 screen, 9 row layout, 10 icon stream, 11 decode options, 12 bitmap.
        MethodVisitor m = w.visitMethod(ACC_STATIC, "inject", "(Ljava/lang/Object;)V", null, null);
        m.visitCode();
        Label start = new Label(), end = new Label(), fail = new Label(), done = new Label();
        m.visitTryCatchBlock(start, end, fail, "java/lang/Throwable");
        m.visitLabel(start);
        integer(m, s.roles().get("owner")); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKESTATIC, MEMBERS, "read", "(ILjava/lang/Object;)Ljava/lang/Object;", false);
        m.visitVarInsn(ASTORE, 6);
        m.visitVarInsn(ALOAD, 6); m.visitJumpInsn(IFNULL, done);
        invoke(m, s, "screen", 6);
        m.visitVarInsn(ASTORE, 7);
        m.visitVarInsn(ALOAD, 7); m.visitJumpInsn(IFNULL, done);
        invoke(m, s, "group_find", 7, text(s.page().key()));
        m.visitJumpInsn(IFNONNULL, done);
        integer(m, s.roles().get("context")); m.visitVarInsn(ALOAD, 7);
        m.visitMethodInsn(INVOKESTATIC, MEMBERS, "read", "(ILjava/lang/Object;)Ljava/lang/Object;", false);
        m.visitVarInsn(ASTORE, 1);
        layoutId(m, s.layouts(), 9);
        create(m, s, "new_preference", local(1));
        m.visitVarInsn(ASTORE, 3);
        describe(m, s, 3, s.page().key(), s.page().title(), s.page().order());
        invoke(m, s, "set_summary", 3, text(s.page().summary())); m.visitInsn(POP);
        useLayout(m, s, 3, 9);
        invoke(m, s, "icon_space", 3, mv -> mv.visitFieldInsn(GETSTATIC, "java/lang/Boolean", "FALSE",
                "Ljava/lang/Boolean;"));
        m.visitInsn(POP);
        useIcon(m, s, 3);
        m.visitTypeInsn(NEW, "android/content/Intent"); m.visitInsn(DUP);
        m.visitMethodInsn(INVOKESPECIAL, "android/content/Intent", "<init>", "()V", false);
        m.visitLdcInsn(s.page().packageName()); m.visitLdcInsn(MANAGER.replace('/', '.'));
        m.visitMethodInsn(INVOKEVIRTUAL, "android/content/Intent", "setClassName",
                "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;", false);
        m.visitVarInsn(ASTORE, 4);
        integer(m, s.roles().get("intent")); m.visitVarInsn(ALOAD, 3); m.visitVarInsn(ALOAD, 4);
        m.visitMethodInsn(INVOKESTATIC, MEMBERS, "write", "(ILjava/lang/Object;Ljava/lang/Object;)V", false);
        invoke(m, s, "group_add", 7, local(3)); m.visitInsn(POP);
        m.visitLabel(end);
        m.visitLabel(done); m.visitInsn(RETURN);
        m.visitLabel(fail); m.visitVarInsn(ASTORE, 5);
        m.visitLdcInsn(LOG_TAG); m.visitLdcInsn("settings entry failed"); m.visitVarInsn(ALOAD, 5);
        m.visitMethodInsn(INVOKESTATIC, "android/util/Log", "w",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I", false);
        m.visitInsn(POP); m.visitInsn(RETURN);
        m.visitMaxs(0, 0); m.visitEnd();
        save(root, ENTRY_UI, w);
    }

    /** Stores {@code context.getResources().getIdentifier(name, "layout", package)} in an int local. */
    private static void layoutId(MethodVisitor m, String name, int slot) {
        resourceId(m, name, "layout", slot);
    }

    private static void resourceId(MethodVisitor m, String name, String type, int slot) {
        m.visitVarInsn(ALOAD, 1); m.visitTypeInsn(CHECKCAST, "android/content/Context");
        m.visitMethodInsn(INVOKEVIRTUAL, "android/content/Context", "getResources", "()Landroid/content/res/Resources;", false);
        m.visitLdcInsn(name); m.visitLdcInsn(type);
        m.visitVarInsn(ALOAD, 1); m.visitTypeInsn(CHECKCAST, "android/content/Context");
        m.visitMethodInsn(INVOKEVIRTUAL, "android/content/Context", "getPackageName", "()Ljava/lang/String;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/content/res/Resources", "getIdentifier",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I", false);
        m.visitVarInsn(ISTORE, slot);
    }

    /** Uses the installed module's icon for its YouTube settings entry. */
    private static void useIcon(MethodVisitor m, Settings s, int preference) {
        m.visitLdcInsn(Type.getObjectType(ENTRY_UI));
        m.visitLdcInsn("/ytm_hellfire.png");
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getResourceAsStream",
                "(Ljava/lang/String;)Ljava/io/InputStream;", false);
        m.visitVarInsn(ASTORE, 10);
        Label missingIcon = new Label();
        m.visitVarInsn(ALOAD, 10); m.visitJumpInsn(IFNULL, missingIcon);
        m.visitTypeInsn(NEW, "android/graphics/BitmapFactory$Options"); m.visitInsn(DUP);
        m.visitMethodInsn(INVOKESPECIAL, "android/graphics/BitmapFactory$Options", "<init>", "()V", false);
        m.visitVarInsn(ASTORE, 11);
        m.visitVarInsn(ALOAD, 11); integer(m, 8);
        m.visitFieldInsn(PUTFIELD, "android/graphics/BitmapFactory$Options", "inSampleSize", "I");
        m.visitVarInsn(ALOAD, 10); m.visitInsn(ACONST_NULL); m.visitVarInsn(ALOAD, 11);
        m.visitMethodInsn(INVOKESTATIC, "android/graphics/BitmapFactory", "decodeStream",
                "(Ljava/io/InputStream;Landroid/graphics/Rect;Landroid/graphics/BitmapFactory$Options;)Landroid/graphics/Bitmap;", false);
        m.visitVarInsn(ASTORE, 12);
        m.visitVarInsn(ALOAD, 10);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/io/InputStream", "close", "()V", false);
        invoke(m, s, "set_icon", preference, mv -> {
            mv.visitTypeInsn(NEW, "android/graphics/drawable/BitmapDrawable"); mv.visitInsn(DUP);
            mv.visitVarInsn(ALOAD, 1); mv.visitTypeInsn(CHECKCAST, "android/content/Context");
            mv.visitMethodInsn(INVOKEVIRTUAL, "android/content/Context", "getResources",
                    "()Landroid/content/res/Resources;", false);
            mv.visitVarInsn(ALOAD, 12);
            mv.visitMethodInsn(INVOKESPECIAL, "android/graphics/drawable/BitmapDrawable", "<init>",
                    "(Landroid/content/res/Resources;Landroid/graphics/Bitmap;)V", false);
        });
        m.visitInsn(POP);
        m.visitLabel(missingIcon);
    }

    /** Applies a resolved layout to a preference; a missing resource keeps the default layout. */
    private static void useLayout(MethodVisitor m, Settings s, int preference, int slot) {
        Label skip = new Label();
        m.visitVarInsn(ILOAD, slot); m.visitJumpInsn(IFEQ, skip);
        integer(m, s.roles().get("layout")); m.visitVarInsn(ALOAD, preference); m.visitVarInsn(ILOAD, slot);
        m.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        m.visitMethodInsn(INVOKESTATIC, MEMBERS, "write", "(ILjava/lang/Object;Ljava/lang/Object;)V", false);
        m.visitLabel(skip);
    }

    // endregion

    // region Manager process

    private static void textColor(MethodVisitor m, String color) {
        m.visitVarInsn(ALOAD, 5); m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKEVIRTUAL, MANAGER, "getResources", "()Landroid/content/res/Resources;", false);
        m.visitLdcInsn(color); m.visitLdcInsn("color");
        m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKEVIRTUAL, MANAGER, "getPackageName", "()Ljava/lang/String;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/content/res/Resources", "getIdentifier",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I", false);
        m.visitMethodInsn(INVOKEVIRTUAL, MANAGER, "getColor", "(I)I", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/TextView", "setTextColor", "(I)V", false);
    }

    /** Adds a label using the compact type and spacing of YouTube settings. */
    private static void textView(MethodVisitor m, String text, float size, boolean bold,
                                 int layout, boolean footer) {
        m.visitTypeInsn(NEW, "android/widget/TextView"); m.visitInsn(DUP); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKESPECIAL, "android/widget/TextView", "<init>", "(Landroid/content/Context;)V", false);
        m.visitVarInsn(ASTORE, 5);
        m.visitVarInsn(ALOAD, 5); m.visitLdcInsn(text);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/TextView", "setText", "(Ljava/lang/CharSequence;)V", false);
        m.visitVarInsn(ALOAD, 5); m.visitLdcInsn(size);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/TextView", "setTextSize", "(F)V", false);
        textColor(m, footer ? "ytm_secondary" : "ytm_primary");
        if (bold) {
            m.visitVarInsn(ALOAD, 5);
            m.visitFieldInsn(GETSTATIC, "android/graphics/Typeface", "DEFAULT", "Landroid/graphics/Typeface;");
            m.visitInsn(ICONST_1);
            m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/TextView", "setTypeface",
                    "(Landroid/graphics/Typeface;I)V", false);
        }
        m.visitVarInsn(ALOAD, 5); m.visitInsn(ICONST_0); m.visitVarInsn(ILOAD, 4);
        m.visitInsn(ICONST_0); m.visitVarInsn(ILOAD, 4);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/TextView", "setPadding", "(IIII)V", false);
        m.visitVarInsn(ALOAD, layout); m.visitVarInsn(ALOAD, 5);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/LinearLayout", "addView", "(Landroid/view/View;)V", false);
    }

    /** Settings screen of the module app; switches stay disabled until the service is bound. */
    private static void managerActivity(Path root, Settings s, List<GenerateModule.Config> configs,
                                        List<GenerateModule.Hook> hooks,
                                        GenerateModule.Diagnostic diagnostic) throws IOException {
        ClassWriter w = writer(MANAGER, "android/app/Activity", "android/view/View$OnClickListener");
        w.visitField(ACC_STATIC | ACC_VOLATILE, "current", "L" + MANAGER + ";", null, null).visitEnd();
        w.visitField(ACC_PRIVATE, "switches", "[Landroid/widget/Switch;", null, null).visitEnd();
        w.visitField(ACC_PRIVATE, "available", "[Z", null, null).visitEnd();
        w.visitField(ACC_PRIVATE, "statusView", "Landroid/widget/TextView;", null, null).visitEnd();
        w.visitField(ACC_PRIVATE, "reportView", "Landroid/widget/TextView;", null, null).visitEnd();
        w.visitField(ACC_PRIVATE, "copyButton", "Landroid/widget/Button;", null, null).visitEnd();
        w.visitField(ACC_PRIVATE, "reportText", "Ljava/lang/String;", null, null).visitEnd();
        w.visitField(ACC_PRIVATE, "hooksReady", "Z", null, null).visitEnd();
        ctor(w, "android/app/Activity");

        // onCreate: locals 0 this, 1 bundle, 2 scroll, 3 content, 4 pad, 5 view,
        // 6 root, 7 toolbar.
        MethodVisitor m = w.visitMethod(ACC_PROTECTED, "onCreate", "(Landroid/os/Bundle;)V", null, null);
        m.visitCode();
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKESPECIAL, "android/app/Activity", "onCreate", "(Landroid/os/Bundle;)V", false);
        m.visitVarInsn(ALOAD, 0); m.visitLdcInsn(s.page().title());
        m.visitMethodInsn(INVOKEVIRTUAL, MANAGER, "setTitle", "(Ljava/lang/CharSequence;)V", false);
        m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKEVIRTUAL, MANAGER, "getResources", "()Landroid/content/res/Resources;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/content/res/Resources", "getDisplayMetrics",
                "()Landroid/util/DisplayMetrics;", false);
        m.visitFieldInsn(GETFIELD, "android/util/DisplayMetrics", "density", "F");
        m.visitLdcInsn(16f); m.visitInsn(FMUL); m.visitInsn(F2I); m.visitVarInsn(ISTORE, 4);
        m.visitTypeInsn(NEW, "android/widget/LinearLayout"); m.visitInsn(DUP); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKESPECIAL, "android/widget/LinearLayout", "<init>", "(Landroid/content/Context;)V", false);
        m.visitVarInsn(ASTORE, 6);
        m.visitVarInsn(ALOAD, 6); m.visitInsn(ICONST_1);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/LinearLayout", "setOrientation", "(I)V", false);
        m.visitVarInsn(ALOAD, 6); m.visitInsn(ICONST_1);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/LinearLayout", "setFitsSystemWindows", "(Z)V", false);
        m.visitTypeInsn(NEW, "android/widget/LinearLayout"); m.visitInsn(DUP); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKESPECIAL, "android/widget/LinearLayout", "<init>", "(Landroid/content/Context;)V", false);
        m.visitVarInsn(ASTORE, 7);
        m.visitVarInsn(ALOAD, 7); m.visitIntInsn(BIPUSH, 16);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/LinearLayout", "setGravity", "(I)V", false);
        m.visitVarInsn(ALOAD, 7); m.visitVarInsn(ILOAD, 4); integer(m, 7); m.visitInsn(IMUL); m.visitInsn(ICONST_2);
        m.visitInsn(IDIV);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/LinearLayout", "setMinimumHeight", "(I)V", false);
        m.visitTypeInsn(NEW, "android/widget/ImageButton"); m.visitInsn(DUP); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKESPECIAL, "android/widget/ImageButton", "<init>", "(Landroid/content/Context;)V", false);
        m.visitVarInsn(ASTORE, 5);
        m.visitVarInsn(ALOAD, 5); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKEVIRTUAL, MANAGER, "getResources", "()Landroid/content/res/Resources;", false);
        m.visitLdcInsn("ytm_arrow_back"); m.visitLdcInsn("drawable");
        m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKEVIRTUAL, MANAGER, "getPackageName", "()Ljava/lang/String;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/content/res/Resources", "getIdentifier",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/ImageButton", "setImageResource", "(I)V", false);
        m.visitVarInsn(ALOAD, 5); m.visitInsn(ICONST_0);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/ImageButton", "setBackgroundColor", "(I)V", false);
        m.visitVarInsn(ALOAD, 5); m.visitVarInsn(ILOAD, 4); integer(m, 3); m.visitInsn(IMUL);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/ImageButton", "setMinimumWidth", "(I)V", false);
        m.visitVarInsn(ALOAD, 5); m.visitVarInsn(ILOAD, 4); integer(m, 7); m.visitInsn(IMUL); m.visitInsn(ICONST_2);
        m.visitInsn(IDIV);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/ImageButton", "setMinimumHeight", "(I)V", false);
        m.visitVarInsn(ALOAD, 5); m.visitLdcInsn("Back");
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/ImageButton", "setContentDescription", "(Ljava/lang/CharSequence;)V", false);
        m.visitVarInsn(ALOAD, 5); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/ImageButton", "setOnClickListener",
                "(Landroid/view/View$OnClickListener;)V", false);
        m.visitVarInsn(ALOAD, 7); m.visitVarInsn(ALOAD, 5);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/LinearLayout", "addView", "(Landroid/view/View;)V", false);
        m.visitTypeInsn(NEW, "android/widget/TextView"); m.visitInsn(DUP); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKESPECIAL, "android/widget/TextView", "<init>", "(Landroid/content/Context;)V", false);
        m.visitVarInsn(ASTORE, 5);
        m.visitVarInsn(ALOAD, 5); m.visitLdcInsn(s.page().title());
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/TextView", "setText", "(Ljava/lang/CharSequence;)V", false);
        m.visitVarInsn(ALOAD, 5); m.visitLdcInsn(20f);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/TextView", "setTextSize", "(F)V", false);
        textColor(m, "ytm_primary");
        m.visitVarInsn(ALOAD, 5); m.visitFieldInsn(GETSTATIC, "android/graphics/Typeface", "DEFAULT", "Landroid/graphics/Typeface;");
        m.visitInsn(ICONST_1);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/TextView", "setTypeface", "(Landroid/graphics/Typeface;I)V", false);
        m.visitVarInsn(ALOAD, 5); m.visitVarInsn(ILOAD, 4); m.visitInsn(ICONST_0);
        m.visitInsn(ICONST_0); m.visitInsn(ICONST_0);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/TextView", "setPadding", "(IIII)V", false);
        m.visitVarInsn(ALOAD, 7); m.visitVarInsn(ALOAD, 5);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/LinearLayout", "addView", "(Landroid/view/View;)V", false);
        m.visitVarInsn(ALOAD, 6); m.visitVarInsn(ALOAD, 7);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/LinearLayout", "addView", "(Landroid/view/View;)V", false);
        m.visitTypeInsn(NEW, "android/widget/ScrollView"); m.visitInsn(DUP); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKESPECIAL, "android/widget/ScrollView", "<init>", "(Landroid/content/Context;)V", false);
        m.visitVarInsn(ASTORE, 2);
        m.visitTypeInsn(NEW, "android/widget/LinearLayout"); m.visitInsn(DUP); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKESPECIAL, "android/widget/LinearLayout", "<init>", "(Landroid/content/Context;)V", false);
        m.visitVarInsn(ASTORE, 3);
        m.visitVarInsn(ALOAD, 3); m.visitInsn(ICONST_1);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/LinearLayout", "setOrientation", "(I)V", false);
        m.visitVarInsn(ALOAD, 3); m.visitVarInsn(ILOAD, 4); m.visitInsn(ICONST_0);
        m.visitVarInsn(ILOAD, 4); m.visitVarInsn(ILOAD, 4);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/LinearLayout", "setPadding", "(IIII)V", false);
        m.visitVarInsn(ALOAD, 2); m.visitVarInsn(ALOAD, 3);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/ScrollView", "addView", "(Landroid/view/View;)V", false);
        m.visitVarInsn(ALOAD, 6); m.visitVarInsn(ALOAD, 2);
        m.visitTypeInsn(NEW, "android/widget/LinearLayout$LayoutParams"); m.visitInsn(DUP);
        m.visitInsn(ICONST_M1); m.visitInsn(ICONST_0); m.visitInsn(FCONST_1);
        m.visitMethodInsn(INVOKESPECIAL, "android/widget/LinearLayout$LayoutParams", "<init>", "(IIF)V", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/LinearLayout", "addView",
                "(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V", false);
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 6);
        m.visitMethodInsn(INVOKEVIRTUAL, MANAGER, "setContentView", "(Landroid/view/View;)V", false);
        m.visitVarInsn(ALOAD, 0); integer(m, s.items().size()); m.visitTypeInsn(ANEWARRAY, "android/widget/Switch");
        m.visitFieldInsn(PUTFIELD, MANAGER, "switches", "[Landroid/widget/Switch;");
        m.visitVarInsn(ALOAD, 0); integer(m, s.items().size()); m.visitIntInsn(NEWARRAY, T_BOOLEAN);
        m.visitFieldInsn(PUTFIELD, MANAGER, "available", "[Z");
        textView(m, diagnostic.screenTitle(), 18f, true, 3, false);
        textView(m, diagnostic.waitingText(), 14f, false, 3, true);
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 5);
        m.visitFieldInsn(PUTFIELD, MANAGER, "statusView", "Landroid/widget/TextView;");
        int slot = 0;
        for (Section section : s.sections()) {
            textView(m, section.title(), 18f, true, 3, false);
            for (Item item : s.items()) {
                if (!item.section().equals(section.id())) continue;
                m.visitTypeInsn(NEW, "android/widget/Switch"); m.visitInsn(DUP); m.visitVarInsn(ALOAD, 0);
                m.visitMethodInsn(INVOKESPECIAL, "android/widget/Switch", "<init>", "(Landroid/content/Context;)V", false);
                m.visitVarInsn(ASTORE, 5);
                m.visitVarInsn(ALOAD, 5); m.visitLdcInsn(item.title());
                m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/Switch", "setText", "(Ljava/lang/CharSequence;)V", false);
                m.visitVarInsn(ALOAD, 5); m.visitLdcInsn(16f);
                m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/Switch", "setTextSize", "(F)V", false);
                m.visitVarInsn(ALOAD, 5); m.visitIntInsn(BIPUSH, 16);
                m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/Switch", "setGravity", "(I)V", false);
                m.visitVarInsn(ALOAD, 5); m.visitVarInsn(ILOAD, 4); integer(m, 7); m.visitInsn(IMUL);
                m.visitInsn(ICONST_2); m.visitInsn(IDIV);
                m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/Switch", "setMinHeight", "(I)V", false);
                m.visitVarInsn(ALOAD, 5); m.visitInsn(ICONST_0);
                m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/Switch", "setEnabled", "(Z)V", false);
                m.visitVarInsn(ALOAD, 5); m.visitInsn(ICONST_0); m.visitVarInsn(ILOAD, 4); m.visitInsn(ICONST_2);
                m.visitInsn(IDIV); m.visitInsn(ICONST_0); m.visitVarInsn(ILOAD, 4); m.visitInsn(ICONST_2); m.visitInsn(IDIV);
                m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/Switch", "setPadding", "(IIII)V", false);
                m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, MANAGER, "switches", "[Landroid/widget/Switch;");
                integer(m, s.items().indexOf(item)); m.visitVarInsn(ALOAD, 5); m.visitInsn(AASTORE);
                m.visitVarInsn(ALOAD, 3); m.visitVarInsn(ALOAD, 5);
                m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/LinearLayout", "addView", "(Landroid/view/View;)V", false);
                slot++;
            }
        }
        if (slot != s.items().size()) throw new IllegalArgumentException("settings items");
        textView(m, diagnostic.reportTitle(), 18f, true, 3, false);
        textView(m, "", 12f, false, 3, true);
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 5);
        m.visitFieldInsn(PUTFIELD, MANAGER, "reportView", "Landroid/widget/TextView;");
        m.visitTypeInsn(NEW, "android/widget/Button"); m.visitInsn(DUP); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKESPECIAL, "android/widget/Button", "<init>", "(Landroid/content/Context;)V", false);
        m.visitVarInsn(ASTORE, 5);
        m.visitVarInsn(ALOAD, 5); m.visitLdcInsn(diagnostic.copyLabel());
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/Button", "setText", "(Ljava/lang/CharSequence;)V", false);
        m.visitVarInsn(ALOAD, 5); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/Button", "setOnClickListener", "(Landroid/view/View$OnClickListener;)V", false);
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 5);
        m.visitTypeInsn(CHECKCAST, "android/widget/Button");
        m.visitFieldInsn(PUTFIELD, MANAGER, "copyButton", "Landroid/widget/Button;");
        m.visitVarInsn(ALOAD, 3); m.visitVarInsn(ALOAD, 5);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/LinearLayout", "addView", "(Landroid/view/View;)V", false);
        textView(m, s.page().note(), 12f, false, 3, true);
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(PUTSTATIC, MANAGER, "current", "L" + MANAGER + ";");
        m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKEVIRTUAL, MANAGER, "refreshStatus", "()V", false);
        m.visitMethodInsn(INVOKESTATIC, SERVICE, "attach", "()V", false);
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();

        m = w.visitMethod(ACC_PUBLIC, "onClick", "(Landroid/view/View;)V", null, null);
        m.visitCode(); Label back = new Label();
        m.visitVarInsn(ALOAD, 1); m.visitVarInsn(ALOAD, 0);
        m.visitFieldInsn(GETFIELD, MANAGER, "copyButton", "Landroid/widget/Button;");
        m.visitJumpInsn(IF_ACMPNE, back);
        m.visitVarInsn(ALOAD, 0); m.visitLdcInsn("clipboard");
        m.visitMethodInsn(INVOKEVIRTUAL, MANAGER, "getSystemService", "(Ljava/lang/String;)Ljava/lang/Object;", false);
        m.visitTypeInsn(CHECKCAST, "android/content/ClipboardManager");
        m.visitLdcInsn(s.page().clipboardLabel()); m.visitVarInsn(ALOAD, 0);
        m.visitFieldInsn(GETFIELD, MANAGER, "reportText", "Ljava/lang/String;");
        m.visitMethodInsn(INVOKESTATIC, "android/content/ClipData", "newPlainText", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Landroid/content/ClipData;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/content/ClipboardManager", "setPrimaryClip", "(Landroid/content/ClipData;)V", false);
        m.visitInsn(RETURN);
        m.visitLabel(back);
        m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKEVIRTUAL, MANAGER, "finish", "()V", false);
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();

        m = w.visitMethod(ACC_PROTECTED, "onResume", "()V", null, null);
        m.visitCode(); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKESPECIAL, "android/app/Activity", "onResume", "()V", false);
        m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKEVIRTUAL, MANAGER, "refreshStatus", "()V", false);
        m.visitMethodInsn(INVOKESTATIC, SERVICE, "attach", "()V", false);
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();

        m = w.visitMethod(ACC_PROTECTED, "onDestroy", "()V", null, null);
        m.visitCode(); Label other = new Label();
        m.visitFieldInsn(GETSTATIC, MANAGER, "current", "L" + MANAGER + ";"); m.visitVarInsn(ALOAD, 0);
        m.visitJumpInsn(IF_ACMPNE, other);
        m.visitInsn(ACONST_NULL); m.visitFieldInsn(PUTSTATIC, MANAGER, "current", "L" + MANAGER + ";");
        m.visitLabel(other); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKESPECIAL, "android/app/Activity", "onDestroy", "()V", false);
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();

        // bind(service), UI thread: locals 0 this, 1 service, 2 switch, 3 error.
        m = w.visitMethod(0, "bind", "(L" + XSERVICE + ";)V", null, null);
        m.visitCode();
        Label start = new Label(), end = new Label(), fail = new Label();
        m.visitTryCatchBlock(start, end, fail, "java/lang/Throwable");
        m.visitLabel(start);
        for (int j = 0; j < s.items().size(); j++) {
            GenerateModule.Config c = configs.get(s.items().get(j).configIndex());
            m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, MANAGER, "switches", "[Landroid/widget/Switch;");
            integer(m, j); m.visitInsn(AALOAD); m.visitVarInsn(ASTORE, 2);
            m.visitVarInsn(ALOAD, 2);
            m.visitVarInsn(ALOAD, 1); m.visitLdcInsn(c.group());
            m.visitMethodInsn(INVOKEVIRTUAL, XSERVICE, "getRemotePreferences", "(Ljava/lang/String;)L" + PREFS + ";", false);
            m.visitLdcInsn(c.key()); m.visitInsn(c.fallback() ? ICONST_1 : ICONST_0);
            m.visitMethodInsn(INVOKEINTERFACE, PREFS, "getBoolean", "(Ljava/lang/String;Z)Z", true);
            m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/Switch", "setChecked", "(Z)V", false);
            m.visitVarInsn(ALOAD, 2);
            m.visitTypeInsn(NEW, TOGGLE); m.visitInsn(DUP); m.visitLdcInsn(c.group()); m.visitLdcInsn(c.key());
            m.visitMethodInsn(INVOKESPECIAL, TOGGLE, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V", false);
            m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/Switch", "setOnCheckedChangeListener",
                    "(L" + CHECK_LISTENER + ";)V", false);
            m.visitVarInsn(ALOAD, 2); m.visitVarInsn(ALOAD, 0);
            m.visitFieldInsn(GETFIELD, MANAGER, "available", "[Z"); integer(m, j); m.visitInsn(BALOAD);
            m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/Switch", "setEnabled", "(Z)V", false);
        }
        m.visitLabel(end); m.visitInsn(RETURN);
        m.visitLabel(fail); m.visitVarInsn(ASTORE, 3);
        m.visitLdcInsn(LOG_TAG); m.visitLdcInsn("settings read failed"); m.visitVarInsn(ALOAD, 3);
        m.visitMethodInsn(INVOKESTATIC, "android/util/Log", "w",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I", false);
        m.visitInsn(POP); m.visitInsn(RETURN);
        m.visitMaxs(0, 0); m.visitEnd();
        diagnosticMethods(w, s, hooks, diagnostic);
        save(root, MANAGER, w);
    }

    /** Presents the exact installed/rolled-back state and keeps controls disabled on failure. */
    private static void diagnosticMethods(ClassWriter w, Settings s, List<GenerateModule.Hook> hooks,
                                           GenerateModule.Diagnostic diagnostic) {
        // locals: 0 activity, 1 snapshot, 2 status, 3 detail, 4/5 time, 6 builder,
        // 7 package, 8 index, 9 error, 10 failed groups, 11 usable status, 12 active hooks.
        MethodVisitor m = w.visitMethod(ACC_PUBLIC, "refreshStatus", "()V", null, null);
        m.visitCode();
        Label start = new Label(), end = new Label(), fail = new Label();
        Label notStale = new Label(), checkModule = new Label(), noTime = new Label();
        m.visitTryCatchBlock(start, end, fail, "java/lang/Throwable");
        m.visitLabel(start);
        m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKESTATIC, StatusTransportGenerator.STATUS, "read",
                "(Landroid/content/Context;)Landroid/os/Bundle;", false);
        m.visitVarInsn(ASTORE, 1);
        m.visitVarInsn(ALOAD, 1); m.visitLdcInsn("status"); m.visitLdcInsn("");
        m.visitMethodInsn(INVOKEVIRTUAL, "android/os/Bundle", "getString",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        m.visitVarInsn(ASTORE, 2);
        m.visitVarInsn(ALOAD, 1); m.visitLdcInsn("detail"); m.visitLdcInsn("");
        m.visitMethodInsn(INVOKEVIRTUAL, "android/os/Bundle", "getString",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        m.visitVarInsn(ASTORE, 3);
        m.visitVarInsn(ALOAD, 1); m.visitLdcInsn("groups"); m.visitLdcInsn("");
        m.visitMethodInsn(INVOKEVIRTUAL, "android/os/Bundle", "getString",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        m.visitVarInsn(ASTORE, 10);
        m.visitVarInsn(ALOAD, 1); m.visitLdcInsn("time");
        m.visitMethodInsn(INVOKEVIRTUAL, "android/os/Bundle", "getLong", "(Ljava/lang/String;)J", false);
        m.visitVarInsn(LSTORE, 4);
        m.visitLdcInsn(diagnostic.readyState()); m.visitVarInsn(ALOAD, 2);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        m.visitVarInsn(ALOAD, 0); m.visitInsn(SWAP);
        m.visitFieldInsn(PUTFIELD, MANAGER, "hooksReady", "Z");
        Label usable = new Label(), usableDone = new Label();
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, MANAGER, "hooksReady", "Z");
        m.visitJumpInsn(IFNE, usable);
        m.visitLdcInsn(diagnostic.degradedState()); m.visitVarInsn(ALOAD, 2);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        m.visitJumpInsn(IFNE, usable);
        m.visitInsn(ICONST_0); m.visitJumpInsn(GOTO, usableDone);
        m.visitLabel(usable); m.visitInsn(ICONST_1); m.visitLabel(usableDone);
        m.visitVarInsn(ISTORE, 11);
        // A report from before a YouTube update cannot authorize feature controls.
        m.visitVarInsn(ILOAD, 11);
        m.visitJumpInsn(IFEQ, notStale);
        m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKEVIRTUAL, MANAGER, "getPackageManager", "()Landroid/content/pm/PackageManager;", false);
        m.visitLdcInsn(diagnostic.targetPackage()); m.visitInsn(ICONST_0);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/content/pm/PackageManager", "getPackageInfo",
                "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;", false);
        m.visitVarInsn(ASTORE, 7);
        m.visitVarInsn(ALOAD, 7); m.visitFieldInsn(GETFIELD, "android/content/pm/PackageInfo", "lastUpdateTime", "J");
        m.visitVarInsn(LLOAD, 4); m.visitInsn(LCMP); m.visitJumpInsn(IFLE, checkModule);
        Label stale = new Label(); m.visitJumpInsn(GOTO, stale);
        m.visitLabel(checkModule);
        m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKEVIRTUAL, MANAGER, "getPackageManager", "()Landroid/content/pm/PackageManager;", false);
        m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKEVIRTUAL, MANAGER, "getPackageName", "()Ljava/lang/String;", false);
        m.visitInsn(ICONST_0);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/content/pm/PackageManager", "getPackageInfo",
                "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;", false);
        m.visitFieldInsn(GETFIELD, "android/content/pm/PackageInfo", "lastUpdateTime", "J");
        m.visitVarInsn(LLOAD, 4); m.visitInsn(LCMP); m.visitJumpInsn(IFLE, notStale);
        m.visitLabel(stale);
        m.visitVarInsn(ALOAD, 0); m.visitInsn(ICONST_0);
        m.visitFieldInsn(PUTFIELD, MANAGER, "hooksReady", "Z");
        m.visitInsn(ICONST_0); m.visitVarInsn(ISTORE, 11);
        m.visitLdcInsn(diagnostic.staleState()); m.visitVarInsn(ASTORE, 2);
        m.visitLdcInsn(diagnostic.staleText());
        m.visitVarInsn(ASTORE, 3);
        m.visitLabel(notStale);
        for (int i = 0; i < s.items().size(); i++) {
            Item item = s.items().get(i);
            Label unavailable = new Label(), availabilityDone = new Label();
            m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, MANAGER, "hooksReady", "Z");
            m.visitJumpInsn(IFNE, availabilityDone);
            m.visitLdcInsn(diagnostic.degradedState()); m.visitVarInsn(ALOAD, 2);
            m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
            m.visitJumpInsn(IFEQ, unavailable);
            for (String group : hooks.stream().filter(h -> h.configIndex() == item.configIndex())
                    .map(GenerateModule.Hook::group).distinct().toList()) {
                m.visitVarInsn(ALOAD, 10); m.visitLdcInsn("|" + group + "|");
                m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false);
                m.visitJumpInsn(IFNE, unavailable);
            }
            m.visitLabel(availabilityDone);
            m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, MANAGER, "available", "[Z");
            integer(m, i); m.visitInsn(ICONST_1); m.visitInsn(BASTORE);
            Label stored = new Label(); m.visitJumpInsn(GOTO, stored);
            m.visitLabel(unavailable);
            m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, MANAGER, "available", "[Z");
            integer(m, i); m.visitInsn(ICONST_0); m.visitInsn(BASTORE);
            m.visitLabel(stored);
        }
        m.visitTypeInsn(NEW, "java/lang/StringBuilder"); m.visitInsn(DUP);
        m.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        m.visitVarInsn(ASTORE, 6);
        append(m, s.page().reportPrefix());
        m.visitVarInsn(ALOAD, 6); m.visitVarInsn(ALOAD, 2);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false); m.visitInsn(POP);
        append(m, "\nAndroid: ");
        m.visitVarInsn(ALOAD, 6);
        m.visitFieldInsn(GETSTATIC, "android/os/Build$VERSION", "RELEASE", "Ljava/lang/String;");
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false); m.visitInsn(POP);
        append(m, "\nBuild: ");
        m.visitVarInsn(ALOAD, 6);
        m.visitFieldInsn(GETSTATIC, "android/os/Build", "DISPLAY", "Ljava/lang/String;");
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false); m.visitInsn(POP);
        append(m, "\nHooks active: ");
        m.visitInsn(ICONST_0); m.visitVarInsn(ISTORE, 12);
        for (GenerateModule.Hook hook : hooks) {
            Label inactive = new Label(), counted = new Label();
            m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, MANAGER, "hooksReady", "Z");
            m.visitJumpInsn(IFNE, counted);
            m.visitLdcInsn(diagnostic.degradedState()); m.visitVarInsn(ALOAD, 2);
            m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
            m.visitJumpInsn(IFEQ, inactive);
            m.visitVarInsn(ALOAD, 10); m.visitLdcInsn("|" + hook.group() + "|");
            m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false);
            m.visitJumpInsn(IFNE, inactive);
            m.visitLabel(counted); m.visitIincInsn(12, 1); m.visitLabel(inactive);
        }
        m.visitVarInsn(ALOAD, 6); m.visitVarInsn(ILOAD, 12);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                "(I)Ljava/lang/StringBuilder;", false); m.visitInsn(POP);
        append(m, "/" + hooks.size() + "\nDetail: ");
        m.visitVarInsn(ALOAD, 6); m.visitVarInsn(ALOAD, 3);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false); m.visitInsn(POP);
        m.visitVarInsn(LLOAD, 4); m.visitInsn(LCONST_0); m.visitInsn(LCMP);
        m.visitJumpInsn(IFLE, noTime);
        append(m, "\nLast checked: ");
        m.visitVarInsn(ALOAD, 6); m.visitTypeInsn(NEW, "java/util/Date"); m.visitInsn(DUP);
        m.visitVarInsn(LLOAD, 4);
        m.visitMethodInsn(INVOKESPECIAL, "java/util/Date", "<init>", "(J)V", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false); m.visitInsn(POP);
        m.visitLabel(noTime);
        append(m, "\n\nFeature compatibility:\n");
        for (int i = 0; i < s.items().size(); i++) {
            Item item = s.items().get(i);
            append(m, item.title() + ": ");
            Label unavailable = new Label(), next = new Label();
            m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, MANAGER, "available", "[Z");
            integer(m, i); m.visitInsn(BALOAD);
            m.visitJumpInsn(IFEQ, unavailable); append(m, "compatible\n");
            m.visitJumpInsn(GOTO, next);
            m.visitLabel(unavailable); append(m, "unavailable\n"); m.visitLabel(next);
        }
        append(m, "\n\nHook contracts:\n");
        for (GenerateModule.Hook hook : hooks) {
            append(m, hook.hookId() + ": ");
            Label unavailable = new Label(), next = new Label();
            m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, MANAGER, "hooksReady", "Z");
            m.visitJumpInsn(IFNE, next);
            m.visitLdcInsn(diagnostic.degradedState()); m.visitVarInsn(ALOAD, 2);
            m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
            m.visitJumpInsn(IFEQ, unavailable);
            m.visitVarInsn(ALOAD, 10); m.visitLdcInsn("|" + hook.group() + "|");
            m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false);
            m.visitJumpInsn(IFNE, unavailable);
            m.visitLabel(next); append(m, "installed\n");
            Label contractDone = new Label();
            m.visitJumpInsn(GOTO, contractDone);
            m.visitLabel(unavailable); append(m, "inactive\n"); m.visitLabel(contractDone);
        }
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 6);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
        m.visitFieldInsn(PUTFIELD, MANAGER, "reportText", "Ljava/lang/String;");
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, MANAGER, "reportView", "Landroid/widget/TextView;");
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, MANAGER, "reportText", "Ljava/lang/String;");
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/TextView", "setText", "(Ljava/lang/CharSequence;)V", false);
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, MANAGER, "statusView", "Landroid/widget/TextView;");
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, MANAGER, "hooksReady", "Z");
        Label statusUnavailable = new Label(), statusDone = new Label();
        m.visitJumpInsn(IFEQ, statusUnavailable);
        m.visitLdcInsn(diagnostic.readyDisplayText());
        m.visitJumpInsn(GOTO, statusDone);
        m.visitLabel(statusUnavailable);
        m.visitVarInsn(ALOAD, 2);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()Z", false);
        Label hasStatus = new Label(); m.visitJumpInsn(IFEQ, hasStatus);
        m.visitLdcInsn(diagnostic.unavailableOpenText());
        m.visitJumpInsn(GOTO, statusDone);
        m.visitLabel(hasStatus); m.visitLdcInsn(diagnostic.unavailablePrefix()); m.visitVarInsn(ALOAD, 2);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false);
        m.visitLabel(statusDone);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/TextView", "setText", "(Ljava/lang/CharSequence;)V", false);
        m.visitVarInsn(ALOAD, 0); m.visitMethodInsn(INVOKEVIRTUAL, MANAGER, "disableSwitches", "()V", false);
        m.visitLabel(end); m.visitInsn(RETURN);
        m.visitLabel(fail); m.visitVarInsn(ASTORE, 9);
        m.visitVarInsn(ALOAD, 0); m.visitInsn(ICONST_0);
        m.visitFieldInsn(PUTFIELD, MANAGER, "hooksReady", "Z");
        m.visitVarInsn(ALOAD, 0); m.visitMethodInsn(INVOKEVIRTUAL, MANAGER, "disableSwitches", "()V", false);
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, MANAGER, "statusView", "Landroid/widget/TextView;");
        m.visitLdcInsn(diagnostic.readFailedText());
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/TextView", "setText", "(Ljava/lang/CharSequence;)V", false);
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 9);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Throwable", "toString", "()Ljava/lang/String;", false);
        m.visitFieldInsn(PUTFIELD, MANAGER, "reportText", "Ljava/lang/String;");
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();

        m = w.visitMethod(ACC_PRIVATE, "disableSwitches", "()V", null, null);
        m.visitCode(); Label loop = new Label(), done = new Label();
        m.visitInsn(ICONST_0); m.visitVarInsn(ISTORE, 1);
        m.visitLabel(loop); m.visitVarInsn(ILOAD, 1); m.visitVarInsn(ALOAD, 0);
        m.visitFieldInsn(GETFIELD, MANAGER, "switches", "[Landroid/widget/Switch;");
        m.visitInsn(ARRAYLENGTH); m.visitJumpInsn(IF_ICMPGE, done);
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, MANAGER, "hooksReady", "Z");
        Label next = new Label(); m.visitJumpInsn(IFNE, next);
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, MANAGER, "switches", "[Landroid/widget/Switch;");
        m.visitVarInsn(ILOAD, 1); m.visitInsn(AALOAD);
        m.visitInsn(ICONST_0);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/Switch", "setEnabled", "(Z)V", false);
        m.visitLabel(next);
        m.visitIincInsn(1, 1); m.visitJumpInsn(GOTO, loop);
        m.visitLabel(done); m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();
    }

    private static void append(MethodVisitor m, String text) {
        m.visitVarInsn(ALOAD, 6); m.visitLdcInsn(text);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        m.visitInsn(POP);
    }

    /** Owns the one process-lifetime XposedService and hands it to the visible manager screen. */
    private static void managerService(Path root) throws IOException {
        ClassWriter w = writer(SERVICE, "java/lang/Object", XLISTENER, "java/lang/Runnable");
        w.visitField(ACC_STATIC | ACC_VOLATILE, "service", "L" + XSERVICE + ";", null, null).visitEnd();
        w.visitField(ACC_PRIVATE | ACC_STATIC, "registered", "Z", null, null).visitEnd();
        ctor(w, "java/lang/Object");

        MethodVisitor m = w.visitMethod(ACC_STATIC | ACC_SYNCHRONIZED, "attach", "()V", null, null);
        m.visitCode(); Label unbound = new Label(), done = new Label();
        m.visitFieldInsn(GETSTATIC, SERVICE, "service", "L" + XSERVICE + ";"); m.visitJumpInsn(IFNULL, unbound);
        m.visitMethodInsn(INVOKESTATIC, SERVICE, "deliver", "()V", false); m.visitInsn(RETURN);
        m.visitLabel(unbound);
        m.visitFieldInsn(GETSTATIC, SERVICE, "registered", "Z"); m.visitJumpInsn(IFNE, done);
        m.visitInsn(ICONST_1); m.visitFieldInsn(PUTSTATIC, SERVICE, "registered", "Z");
        m.visitTypeInsn(NEW, SERVICE); m.visitInsn(DUP);
        m.visitMethodInsn(INVOKESPECIAL, SERVICE, "<init>", "()V", false);
        m.visitMethodInsn(INVOKESTATIC, XHELPER, "registerListener", "(L" + XLISTENER + ";)V", false);
        m.visitLabel(done); m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();

        m = w.visitMethod(ACC_PRIVATE | ACC_STATIC, "deliver", "()V", null, null);
        m.visitCode(); Label none = new Label();
        m.visitFieldInsn(GETSTATIC, MANAGER, "current", "L" + MANAGER + ";"); m.visitVarInsn(ASTORE, 0);
        m.visitVarInsn(ALOAD, 0); m.visitJumpInsn(IFNULL, none);
        m.visitVarInsn(ALOAD, 0); m.visitTypeInsn(NEW, SERVICE); m.visitInsn(DUP);
        m.visitMethodInsn(INVOKESPECIAL, SERVICE, "<init>", "()V", false);
        m.visitMethodInsn(INVOKEVIRTUAL, MANAGER, "runOnUiThread", "(Ljava/lang/Runnable;)V", false);
        m.visitLabel(none); m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();

        m = w.visitMethod(ACC_PUBLIC, "run", "()V", null, null);
        m.visitCode(); Label skip = new Label();
        m.visitFieldInsn(GETSTATIC, MANAGER, "current", "L" + MANAGER + ";"); m.visitVarInsn(ASTORE, 1);
        m.visitFieldInsn(GETSTATIC, SERVICE, "service", "L" + XSERVICE + ";"); m.visitVarInsn(ASTORE, 2);
        m.visitVarInsn(ALOAD, 1); m.visitJumpInsn(IFNULL, skip);
        m.visitVarInsn(ALOAD, 2); m.visitJumpInsn(IFNULL, skip);
        m.visitVarInsn(ALOAD, 1); m.visitVarInsn(ALOAD, 2);
        m.visitMethodInsn(INVOKEVIRTUAL, MANAGER, "bind", "(L" + XSERVICE + ";)V", false);
        m.visitLabel(skip); m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();

        m = w.visitMethod(ACC_PUBLIC, "onServiceBind", "(L" + XSERVICE + ";)V", null, null);
        m.visitCode(); m.visitVarInsn(ALOAD, 1); m.visitFieldInsn(PUTSTATIC, SERVICE, "service", "L" + XSERVICE + ";");
        m.visitMethodInsn(INVOKESTATIC, SERVICE, "deliver", "()V", false);
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();

        m = w.visitMethod(ACC_PUBLIC, "onServiceDied", "(L" + XSERVICE + ";)V", null, null);
        m.visitCode(); m.visitInsn(ACONST_NULL); m.visitFieldInsn(PUTSTATIC, SERVICE, "service", "L" + XSERVICE + ";");
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();
        save(root, SERVICE, w);
    }

    /** Commits one boolean to RemotePreferences; a failed commit is reported, never retried. */
    private static void managerToggle(Path root) throws IOException {
        ClassWriter w = writer(TOGGLE, "java/lang/Object", CHECK_LISTENER);
        w.visitField(ACC_PRIVATE | ACC_FINAL, "group", "Ljava/lang/String;", null, null).visitEnd();
        w.visitField(ACC_PRIVATE | ACC_FINAL, "key", "Ljava/lang/String;", null, null).visitEnd();
        MethodVisitor m = w.visitMethod(0, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V", null, null);
        m.visitCode(); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 1); m.visitFieldInsn(PUTFIELD, TOGGLE, "group", "Ljava/lang/String;");
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 2); m.visitFieldInsn(PUTFIELD, TOGGLE, "key", "Ljava/lang/String;");
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();

        // onCheckedChanged: locals 0 this, 1 button, 2 checked, 3 service.
        m = w.visitMethod(ACC_PUBLIC, "onCheckedChanged", "(Landroid/widget/CompoundButton;Z)V", null, null);
        m.visitCode();
        Label start = new Label(), end = new Label(), fail = new Label(), report = new Label();
        m.visitTryCatchBlock(start, end, fail, "java/lang/Throwable");
        m.visitLabel(start);
        m.visitFieldInsn(GETSTATIC, SERVICE, "service", "L" + XSERVICE + ";"); m.visitVarInsn(ASTORE, 3);
        m.visitVarInsn(ALOAD, 3); m.visitJumpInsn(IFNULL, report);
        m.visitVarInsn(ALOAD, 3); m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, TOGGLE, "group", "Ljava/lang/String;");
        m.visitMethodInsn(INVOKEVIRTUAL, XSERVICE, "getRemotePreferences", "(Ljava/lang/String;)L" + PREFS + ";", false);
        m.visitMethodInsn(INVOKEINTERFACE, PREFS, "edit", "()L" + EDITOR + ";", true);
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, TOGGLE, "key", "Ljava/lang/String;"); m.visitVarInsn(ILOAD, 2);
        m.visitMethodInsn(INVOKEINTERFACE, EDITOR, "putBoolean", "(Ljava/lang/String;Z)L" + EDITOR + ";", true);
        m.visitMethodInsn(INVOKEINTERFACE, EDITOR, "commit", "()Z", true);
        m.visitJumpInsn(IFEQ, report);
        m.visitLabel(end); m.visitInsn(RETURN);
        m.visitLabel(fail); m.visitInsn(POP);
        m.visitLabel(report);
        m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/CompoundButton", "getContext", "()Landroid/content/Context;", false);
        m.visitLdcInsn("Setting not saved"); m.visitInsn(ICONST_0);
        m.visitMethodInsn(INVOKESTATIC, "android/widget/Toast", "makeText",
                "(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "android/widget/Toast", "show", "()V", false);
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();
        save(root, TOGGLE, w);
    }

    // endregion
}
