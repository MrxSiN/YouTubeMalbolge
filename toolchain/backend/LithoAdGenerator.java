package toolchain.backend;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Cold-bound target members and the generated handler for ad component replacement. */
final class LithoAdGenerator implements Opcodes {
    private static final String NAME = GenerateModule.P + "LithoAdPort";
    private static final String FIELD = "Ljava/lang/reflect/Field;";
    private static final String METHOD = "Ljava/lang/reflect/Method;";

    private LithoAdGenerator() {}

    static void generate(Path root, List<String> args) throws IOException {
        if (args.size() < 12) throw new IllegalArgumentException("incomplete Litho ad binding");
        ClassWriter w = GenerateModule.writer(NAME, "java/lang/Object");
        w.visitField(ACC_PRIVATE | ACC_STATIC, "identifier", FIELD, null, null).visitEnd();
        w.visitField(ACC_PRIVATE | ACC_STATIC, "factory", METHOD, null, null).visitEnd();
        w.visitField(ACC_PRIVATE | ACC_STATIC, "value", FIELD, null, null).visitEnd();
        GenerateModule.ctor(w, "java/lang/Object");
        bind(w, args);
        filter(w, args.subList(11, args.size()));
        GenerateModule.save(root, NAME, w);
    }

    private static void boundField(MethodVisitor m, String owner, String field,
                                   String superclass, String descriptor, String target) {
        GenerateModule.loadClass(m, owner, 0);
        m.visitInsn(DUP);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getSuperclass", "()Ljava/lang/Class;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
        m.visitLdcInsn(superclass.replace('/', '.'));
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label ownerMatched = new Label();
        m.visitJumpInsn(IFNE, ownerMatched);
        GenerateModule.throwState(m, "Litho ad field owner mismatch: " + owner + "." + field);
        m.visitLabel(ownerMatched);
        m.visitLdcInsn(field);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredField",
                "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false);
        m.visitVarInsn(ASTORE, 1);
        Label typed = new Label(), modifiers = new Label();
        m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "getType", "()Ljava/lang/Class;", false);
        GenerateModule.typeClass(m, Type.getType(descriptor), 0);
        m.visitJumpInsn(IF_ACMPEQ, typed);
        GenerateModule.throwState(m, "Litho ad field type mismatch: " + owner + "." + field);
        m.visitLabel(typed);
        m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "getModifiers", "()I", false);
        GenerateModule.integer(m, 29);
        m.visitInsn(IAND);
        GenerateModule.integer(m, 17);
        m.visitJumpInsn(IF_ICMPEQ, modifiers);
        GenerateModule.throwState(m, "Litho ad field access mismatch: " + owner + "." + field);
        m.visitLabel(modifiers);
        m.visitVarInsn(ALOAD, 1);
        m.visitInsn(ICONST_1);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "setAccessible", "(Z)V", false);
        m.visitVarInsn(ALOAD, 1);
        m.visitFieldInsn(PUTSTATIC, NAME, target, FIELD);
    }

    private static void bind(ClassWriter w, List<String> args) {
        MethodVisitor m = w.visitMethod(ACC_PUBLIC | ACC_STATIC, "bind", "(Ljava/lang/ClassLoader;)V",
                null, new String[] {"java/lang/Throwable"});
        m.visitCode();
        boundField(m, args.get(0), args.get(1), args.get(2), "Ljava/lang/String;", "identifier");
        GenerateModule.Member factory = new GenerateModule.Member("METHOD", args.get(3),
                args.get(4), args.get(5), 9, args.get(6));
        GenerateModule.resolveMember(m, factory, 0, 2, 3, "Litho ad factory mismatch");
        m.visitVarInsn(ALOAD, 3);
        m.visitTypeInsn(CHECKCAST, "java/lang/reflect/Method");
        m.visitFieldInsn(PUTSTATIC, NAME, "factory", METHOD);
        boundField(m, args.get(7), args.get(8), args.get(9), args.get(10), "value");
        m.visitInsn(RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
    }

    private static void filter(ClassWriter w, List<String> patterns) {
        MethodVisitor m = w.visitMethod(ACC_PUBLIC | ACC_STATIC, "filter",
                "(L" + GenerateModule.CHAIN + ";)Ljava/lang/Object;", null,
                new String[] {"java/lang/Throwable"});
        m.visitCode();
        m.visitFieldInsn(GETSTATIC, NAME, "identifier", FIELD);
        m.visitVarInsn(ALOAD, 0);
        m.visitInsn(ICONST_1);
        m.visitMethodInsn(INVOKEINTERFACE, GenerateModule.CHAIN, "getArg",
                "(I)Ljava/lang/Object;", true);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;", false);
        m.visitTypeInsn(CHECKCAST, "java/lang/String");
        m.visitVarInsn(ASTORE, 1);
        Label noMatch = new Label(), matched = new Label();
        m.visitVarInsn(ALOAD, 1);
        m.visitJumpInsn(IFNULL, noMatch);
        for (String pattern : patterns) {
            m.visitVarInsn(ALOAD, 1);
            m.visitLdcInsn(pattern);
            m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false);
            m.visitJumpInsn(IFNE, matched);
        }
        m.visitJumpInsn(GOTO, noMatch);
        m.visitLabel(matched);
        m.visitFieldInsn(GETSTATIC, NAME, "value", FIELD);
        m.visitFieldInsn(GETSTATIC, NAME, "factory", METHOD);
        m.visitInsn(ACONST_NULL);
        m.visitInsn(ICONST_1);
        m.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        m.visitInsn(DUP);
        m.visitInsn(ICONST_0);
        m.visitVarInsn(ALOAD, 0);
        m.visitInsn(ICONST_0);
        m.visitMethodInsn(INVOKEINTERFACE, GenerateModule.CHAIN, "getArg",
                "(I)Ljava/lang/Object;", true);
        m.visitInsn(AASTORE);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "invoke",
                "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;", false);
        m.visitInsn(ARETURN);
        m.visitLabel(noMatch);
        m.visitInsn(ACONST_NULL);
        m.visitInsn(ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
    }
}
