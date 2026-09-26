package toolchain.backend;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Emits the generic resolved-member table used by AOT programs. */
final class ProgramMembersGenerator implements Opcodes {
    static final String NAME = GenerateModule.P + "ProgramMembers";
    private static final String OBJECTS = "[Ljava/lang/Object;";

    private ProgramMembersGenerator() {}

    static void generate(Path root, List<GenerateModule.Member> members) throws IOException {
        ClassWriter writer = GenerateModule.writer(NAME, "java/lang/Object");
        writer.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "members", OBJECTS, null, null).visitEnd();
        MethodVisitor method = writer.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        method.visitCode();
        GenerateModule.integer(method, members.size());
        method.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        method.visitFieldInsn(PUTSTATIC, NAME, "members", OBJECTS);
        method.visitInsn(RETURN); method.visitMaxs(0, 0); method.visitEnd();

        method = writer.visitMethod(ACC_STATIC, "bind", "(Ljava/lang/ClassLoader;I)V", null,
                new String[] {"java/lang/Throwable"});
        method.visitCode();
        for (int index = 0; index < members.size(); index++) {
            Label next = new Label();
            method.visitVarInsn(ILOAD, 1); GenerateModule.integer(method, index);
            method.visitJumpInsn(IF_ICMPNE, next);
            GenerateModule.Member member = members.get(index);
            if (member.kind().equals("FIELD")) resolveField(method, member);
            else GenerateModule.resolveMember(method, member, 0, 1, 2,
                    "program member shape mismatch: " + index);
            method.visitFieldInsn(GETSTATIC, NAME, "members", OBJECTS);
            GenerateModule.integer(method, index);
            method.visitVarInsn(ALOAD, 2);
            method.visitInsn(AASTORE);
            method.visitInsn(RETURN);
            method.visitLabel(next);
        }
        GenerateModule.throwState(method, "program member index mismatch");
        method.visitMaxs(0, 0); method.visitEnd();

        method = writer.visitMethod(ACC_STATIC, "read", "(ILjava/lang/Object;)Ljava/lang/Object;", null,
                new String[] {"java/lang/Throwable"});
        method.visitCode(); load(method, "java/lang/reflect/Field", 0);
        method.visitVarInsn(ALOAD, 1);
        method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;", false);
        method.visitInsn(ARETURN); method.visitMaxs(0, 0); method.visitEnd();

        method = writer.visitMethod(ACC_STATIC, "call",
                "(ILjava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", null,
                new String[] {"java/lang/Throwable"});
        method.visitCode();
        load(method, "java/lang/reflect/Method", 0);
        method.visitVarInsn(ALOAD, 1); method.visitVarInsn(ALOAD, 2);
        method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "invoke",
                "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
        method.visitInsn(ARETURN); method.visitMaxs(0, 0); method.visitEnd();
        GenerateModule.save(root, NAME, writer);
    }

    private static void load(MethodVisitor method, String type, int indexLocal) {
        method.visitFieldInsn(GETSTATIC, NAME, "members", OBJECTS);
        method.visitVarInsn(ILOAD, indexLocal);
        method.visitInsn(AALOAD);
        method.visitTypeInsn(CHECKCAST, type);
    }

    private static void resolveField(MethodVisitor method, GenerateModule.Member member) {
        Label ready = new Label();
        method.visitVarInsn(ALOAD, 0); method.visitLdcInsn(member.owner()); method.visitLdcInsn(member.name());
        method.visitLdcInsn(member.descriptor()); GenerateModule.integer(method, member.access());
        method.visitLdcInsn(member.superclass());
        method.visitMethodInsn(INVOKESTATIC, GenerateModule.RESOLVER, "field",
                "(Ljava/lang/ClassLoader;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;)Ljava/lang/reflect/Field;",
                false);
        method.visitInsn(DUP); method.visitJumpInsn(IFNONNULL, ready); method.visitInsn(POP);
        GenerateModule.throwState(method, "program field shape mismatch");
        method.visitLabel(ready); method.visitVarInsn(ASTORE, 2);
    }
}
