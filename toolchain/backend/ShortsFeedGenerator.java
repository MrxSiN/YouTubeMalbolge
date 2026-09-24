package toolchain.backend;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Emits the Shorts item filter selected by the Malbolge Effects. */
final class ShortsFeedGenerator implements Opcodes {
    static final String NAME = GenerateModule.P + "ShortsFeedPort";
    private static final String METHOD = "Ljava/lang/reflect/Method;";
    private static final String CHAIN = GenerateModule.CHAIN;

    private ShortsFeedGenerator() {}

    static void generate(Path root, List<String> args) throws IOException {
        if (args.size() != 6) throw new IllegalArgumentException("incomplete Shorts predicate binding");
        ClassWriter w = GenerateModule.writer(NAME, "java/lang/Object");
        w.visitField(ACC_PRIVATE | ACC_STATIC, "predicate", METHOD, null, null).visitEnd();
        bind(w, args);
        filter(w);
        GenerateModule.save(root, NAME, w);
    }

    private static void bind(ClassWriter w, List<String> args) {
        MethodVisitor m = w.visitMethod(ACC_PUBLIC | ACC_STATIC, "bind", "(Ljava/lang/ClassLoader;)V",
                null, new String[] {"java/lang/Throwable"});
        m.visitCode();
        GenerateModule.Member member = new GenerateModule.Member(args.get(0), args.get(1),
                args.get(2), args.get(3), Integer.parseInt(args.get(4)), args.get(5));
        GenerateModule.resolveMember(m, member, 0, 1, 2, "Shorts predicate binding mismatch");
        m.visitVarInsn(ALOAD, 2);
        m.visitTypeInsn(CHECKCAST, "java/lang/reflect/Method");
        m.visitFieldInsn(PUTSTATIC, NAME, "predicate", METHOD);
        m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();
    }

    private static void filter(ClassWriter w) {
        MethodVisitor m = w.visitMethod(ACC_PUBLIC | ACC_STATIC, "filter",
                "(L" + CHAIN + ";)Ljava/lang/Object;", null,
                new String[] {"java/lang/Throwable"});
        Label start = new Label(), end = new Label(), failed = new Label(), original = new Label();
        m.visitTryCatchBlock(start, end, failed, "java/lang/Throwable");
        m.visitCode(); m.visitLabel(start);
        m.visitFieldInsn(GETSTATIC, NAME, "predicate", METHOD);
        m.visitInsn(ACONST_NULL);
        m.visitInsn(ICONST_1); m.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        m.visitInsn(DUP); m.visitInsn(ICONST_0);
        m.visitVarInsn(ALOAD, 0); m.visitInsn(ICONST_0);
        m.visitMethodInsn(INVOKEINTERFACE, CHAIN, "getArg", "(I)Ljava/lang/Object;", true);
        m.visitInsn(AASTORE);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "invoke",
                "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
        m.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
        m.visitJumpInsn(IFEQ, original);
        m.visitLdcInsn(GenerateModule.LOG_TAG); m.visitLdcInsn("Shorts ad blocked before insertion");
        m.visitMethodInsn(INVOKESTATIC, "android/util/Log", "i", "(Ljava/lang/String;Ljava/lang/String;)I", false);
        m.visitInsn(POP);
        m.visitVarInsn(ALOAD, 0); m.visitInsn(ICONST_4);
        m.visitMethodInsn(INVOKEINTERFACE, CHAIN, "getArg", "(I)Ljava/lang/Object;", true);
        m.visitTypeInsn(CHECKCAST, "java/lang/Long");
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
        m.visitInsn(LCONST_1); m.visitInsn(LADD);
        m.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        m.visitLabel(end); m.visitInsn(ARETURN);
        m.visitLabel(failed);
        m.visitVarInsn(ASTORE, 1);
        m.visitLdcInsn(GenerateModule.LOG_TAG); m.visitLdcInsn("Shorts ad filter failed");
        m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKESTATIC, "android/util/Log", "e", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I", false);
        m.visitInsn(POP);
        m.visitLabel(original);
        m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKEINTERFACE, CHAIN, "proceed", "()Ljava/lang/Object;", true);
        m.visitInsn(ARETURN);
        m.visitMaxs(0, 0); m.visitEnd();
    }
}
