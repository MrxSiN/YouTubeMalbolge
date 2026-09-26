package toolchain.backend;

import java.util.HexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Generic MBP1-to-JVM hook compiler. Contains no feature names or target knowledge. */
final class AotHookCompiler implements Opcodes {
    private static final int OP_ARG = 1;
    private static final int OP_THIS = 2;
    private static final int OP_PROCEED = 3;
    private static final int OP_CONST_NULL = 4;
    private static final int OP_CONST_INT = 5;
    private static final int OP_MEMBER_GET = 7;
    private static final int OP_MEMBER_CALL = 8;
    private static final int OP_SET_VISIBILITY = 9;
    private static final int OP_RETURN = 10;
    private static final int OP_DROP = 11;
    private static final int OP_DUP = 12;
    private static final int OP_STRING_CONTAINS = 13;
    private static final int OP_JUMP = 14;
    private static final int OP_JUMP_FALSE = 15;
    private static final int OP_LONG_ADD = 16;
    private static final int OP_LOG = 17;
    private static final int OP_TRUTH = 18;
    private static final int OP_CONST_LONG = 19;
    private static final int OP_JUMP_NULL = 20;
    private static final int OP_UI_APPLY = 21;
    private static final int OP_LOCAL_GET = 25;
    private static final int OP_LOCAL_SET = 26;
    private static final int OP_STATE_GET = 27;
    private static final int OP_STATE_SET = 28;
    private static final int OP_ARRAY_LENGTH = 29;
    private static final int OP_LONG_ARRAY_GET = 30;
    private static final int OP_LONG_ARRAY_SET = 31;
    private static final int OP_INT_ADD = 32;
    private static final int OP_INT_LT = 33;
    private static final int OP_LONG_LT = 34;
    private static final int OP_LONG_GE = 35;
    private static final int OP_LONG_EQ = 36;
    private static final int OP_CONFIG_DYNAMIC = 37;
    private static final int OP_CALL_LONG_PORT = 38;
    private static final int OP_BOUND_FIELD_GET = 39;
    private static final int OP_STRING_LENGTH = 40;
    private static final int OP_INT_EQ = 41;
    private static final int OP_OBJECT_EQUALS = 42;
    private static final int OP_NEW_LONG_ARRAY = 43;
    private static final int OP_ASYNC_LOAD = 44;
    private static final int OP_ARG_LONG = 45;
    private static final int OP_INT_VALUE = 46;
    private static final int OP_LONG_VALUE = 47;
    private static final int OP_INT_LOCAL_GET = 48;
    private static final int OP_INT_LOCAL_SET = 49;
    private static final int OP_LONG_LOCAL_GET = 50;
    private static final int OP_LONG_LOCAL_SET = 51;
    private static final int OP_LONG_ADD_VALUE = 52;
    private static final int OP_STATE_LONG_ARRAY = 53;

    private AotHookCompiler() {}

    static void emit(MethodVisitor method, GenerateModule.Hook hook) {
        List<String> arguments = hook.arguments();
        if (arguments.size() < 5 || !arguments.get(0).equals("MBP1")) {
            throw new IllegalArgumentException("invalid MBP1 hook program");
        }
        int constants = Integer.parseInt(arguments.get(3));
        int membersAt = 4 + constants;
        if (membersAt >= arguments.size()) throw new IllegalArgumentException("missing MBP1 member table");
        int memberCount = Integer.parseInt(arguments.get(membersAt));
        int codeAt = membersAt + 1 + memberCount;
        if (codeAt >= arguments.size() || codeAt + 1 != arguments.size()) {
            throw new IllegalArgumentException("invalid MBP1 constant table");
        }
        byte[] code = HexFormat.of().parseHex(arguments.get(codeAt));
        String[] constantValues = arguments.subList(4, membersAt).toArray(String[]::new);
        int[] memberIndexes = new int[memberCount];
        for (int index = 0; index < memberCount; index++) {
            memberIndexes[index] = Integer.parseInt(arguments.get(membersAt + 1 + index));
        }
        Map<Integer, Label> labels = labels(code);
        int at = 0;
        while (at < code.length) {
            Label label = labels.get(at);
            if (label != null) method.visitLabel(label);
            int opcode = code[at++] & 0xff;
            switch (opcode) {
                case OP_ARG -> {
                    int index = code[at++] & 0xff;
                    method.visitVarInsn(ALOAD, 1);
                    GenerateModule.integer(method, index);
                    method.visitMethodInsn(INVOKEINTERFACE, GenerateModule.CHAIN, "getArg",
                            "(I)Ljava/lang/Object;", true);
                }
                case OP_THIS -> {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitMethodInsn(INVOKEINTERFACE, GenerateModule.CHAIN, "getThisObject",
                            "()Ljava/lang/Object;", true);
                }
                case OP_PROCEED -> {
                    method.visitInsn(ICONST_1); method.visitVarInsn(ISTORE, 31);
                    method.visitVarInsn(ALOAD, 1);
                    method.visitMethodInsn(INVOKEINTERFACE, GenerateModule.CHAIN, "proceed",
                            "()Ljava/lang/Object;", true);
                    method.visitInsn(DUP); method.visitVarInsn(ASTORE, 30);
                    method.visitInsn(ICONST_2); method.visitVarInsn(ISTORE, 31);
                }
                case OP_CONST_NULL -> method.visitInsn(ACONST_NULL);
                case OP_CONST_INT -> {
                    require(code, at, 4);
                    int value = (code[at] & 0xff) << 24 | (code[at + 1] & 0xff) << 16
                            | (code[at + 2] & 0xff) << 8 | code[at + 3] & 0xff;
                    at += 4;
                    GenerateModule.integer(method, value);
                    method.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf",
                            "(I)Ljava/lang/Integer;", false);
                }
                case OP_CONST_LONG -> {
                    require(code, at, 8);
                    long value = 0;
                    for (int i = 0; i < 8; i++) value = value << 8 | code[at + i] & 0xffL;
                    at += 8;
                    method.visitLdcInsn(value);
                    method.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf",
                            "(J)Ljava/lang/Long;", false);
                }
                case OP_MEMBER_GET -> {
                    require(code, at, 2);
                    int index = unsignedShort(code, at); at += 2;
                    method.visitMethodInsn(INVOKESTATIC, GenerateModule.RESOLVER, "debug",
                            "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                    GenerateModule.integer(method, memberIndexes[index]);
                    method.visitInsn(SWAP);
                    method.visitMethodInsn(INVOKESTATIC, ProgramMembersGenerator.NAME, "read",
                            "(ILjava/lang/Object;)Ljava/lang/Object;", false);
                    method.visitInsn(DUP); method.visitLdcInsn(GenerateModule.LOG_TAG); method.visitInsn(SWAP);
                    method.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf",
                            "(Ljava/lang/Object;)Ljava/lang/String;", false);
                    method.visitMethodInsn(INVOKESTATIC, "android/util/Log", "i",
                            "(Ljava/lang/String;Ljava/lang/String;)I", false);
                    method.visitInsn(POP);
                }
                case OP_MEMBER_CALL -> {
                    require(code, at, 3);
                    int index = unsignedShort(code, at); at += 2;
                    int count = code[at++] & 0xff;
                    for (int arg = count - 1; arg >= 0; arg--) method.visitVarInsn(ASTORE, 2 + arg);
                    method.visitVarInsn(ASTORE, 2 + count);
                    GenerateModule.integer(method, memberIndexes[index]);
                    method.visitVarInsn(ALOAD, 2 + count);
                    GenerateModule.integer(method, count);
                    method.visitTypeInsn(ANEWARRAY, "java/lang/Object");
                    for (int arg = 0; arg < count; arg++) {
                        method.visitInsn(DUP); GenerateModule.integer(method, arg);
                        method.visitVarInsn(ALOAD, 2 + arg); method.visitInsn(AASTORE);
                    }
                    method.visitMethodInsn(INVOKESTATIC, ProgramMembersGenerator.NAME, "call",
                            "(ILjava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
                }
                case OP_SET_VISIBILITY -> {
                    require(code, at, 4);
                    int value = (code[at] & 0xff) << 24 | (code[at + 1] & 0xff) << 16
                            | (code[at + 2] & 0xff) << 8 | code[at + 3] & 0xff;
                    at += 4;
                    method.visitTypeInsn(CHECKCAST, "android/view/View");
                    GenerateModule.integer(method, value);
                    method.visitMethodInsn(INVOKEVIRTUAL, "android/view/View", "setVisibility", "(I)V", false);
                }
                case OP_RETURN -> method.visitInsn(ARETURN);
                case OP_DROP -> method.visitInsn(POP);
                case OP_DUP -> method.visitInsn(DUP);
                case OP_STRING_CONTAINS -> {
                    require(code, at, 2);
                    int index = unsignedShort(code, at); at += 2;
                    method.visitTypeInsn(CHECKCAST, "java/lang/String");
                    method.visitLdcInsn(constantValues[index]);
                    method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "contains",
                            "(Ljava/lang/CharSequence;)Z", false);
                }
                case OP_JUMP, OP_JUMP_FALSE, OP_JUMP_NULL -> {
                    require(code, at, 2);
                    int relative = (short) unsignedShort(code, at); at += 2;
                    Label target = labels.get(at + relative);
                    if (target == null) throw new IllegalArgumentException("invalid MBP1 branch");
                    method.visitJumpInsn(opcode == OP_JUMP ? GOTO : opcode == OP_JUMP_FALSE ? IFEQ : IFNULL, target);
                }
                case OP_TRUTH -> {
                    method.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                    method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                }
                case OP_LONG_ADD -> {
                    method.visitTypeInsn(CHECKCAST, "java/lang/Long");
                    method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                    method.visitVarInsn(LSTORE, 2);
                    method.visitTypeInsn(CHECKCAST, "java/lang/Long");
                    method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                    method.visitVarInsn(LLOAD, 2); method.visitInsn(LADD);
                    method.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                }
                case OP_LOG -> {
                    require(code, at, 2);
                    int index = unsignedShort(code, at); at += 2;
                    GenerateModule.log(method, "i", constantValues[index]);
                }
                case OP_UI_APPLY -> method.visitMethodInsn(INVOKESTATIC,
                        UiModelGenerator.ENTRY_UI, "inject", "(Ljava/lang/Object;)V", false);
                case OP_LOCAL_GET -> method.visitVarInsn(ALOAD, 32 + (code[at++] & 0xff));
                case OP_LOCAL_SET -> method.visitVarInsn(ASTORE, 32 + (code[at++] & 0xff));
                case OP_STATE_GET -> {
                    GenerateModule.integer(method, code[at++] & 0xff);
                    method.visitMethodInsn(INVOKESTATIC, GenerateModule.STATE, "get", "(I)Ljava/lang/Object;", false);
                }
                case OP_STATE_SET -> {
                    int index = code[at++] & 0xff;
                    GenerateModule.integer(method, index); method.visitInsn(SWAP);
                    method.visitMethodInsn(INVOKESTATIC, GenerateModule.STATE, "set", "(ILjava/lang/Object;)V", false);
                }
                case OP_ARRAY_LENGTH -> {
                    method.visitInsn(ARRAYLENGTH);
                }
                case OP_LONG_ARRAY_GET -> method.visitInsn(LALOAD);
                case OP_LONG_ARRAY_SET -> method.visitInsn(LASTORE);
                case OP_INT_ADD, OP_INT_LT -> {
                    if (opcode == OP_INT_ADD) {
                        method.visitInsn(IADD);
                    } else {
                        Label yes = new Label(), compared = new Label();
                        method.visitJumpInsn(IF_ICMPLT, yes); method.visitInsn(ICONST_0); method.visitJumpInsn(GOTO, compared);
                        method.visitLabel(yes); method.visitInsn(ICONST_1); method.visitLabel(compared);
                    }
                }
                case OP_LONG_LT, OP_LONG_GE, OP_LONG_EQ -> {
                    method.visitInsn(LCMP);
                    Label yes = new Label(), compared = new Label();
                    method.visitJumpInsn(opcode == OP_LONG_LT ? IFLT : opcode == OP_LONG_GE ? IFGE : IFEQ, yes);
                    method.visitInsn(ICONST_0); method.visitJumpInsn(GOTO, compared);
                    method.visitLabel(yes); method.visitInsn(ICONST_1); method.visitLabel(compared);
                }
                case OP_CONFIG_DYNAMIC -> {
                    method.visitInsn(L2I);
                    method.visitMethodInsn(INVOKESTATIC, GenerateModule.SNAP, "enabled", "(I)Z", false);
                }
                case OP_CALL_LONG_PORT -> {
                    method.visitMethodInsn(INVOKESTATIC, GenerateModule.SEEK, "seek", "(J)Z", false);
                }
                case OP_BOUND_FIELD_GET -> {
                    require(code, at, 5); at += 4;
                    GenerateModule.integer(method, code[at++] & 0xff);
                    method.visitMethodInsn(INVOKESTATIC, GenerateModule.STATE, "get", "(I)Ljava/lang/Object;", false);
                    method.visitTypeInsn(CHECKCAST, "java/lang/reflect/Field"); method.visitInsn(SWAP);
                    method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "get",
                            "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                }
                case OP_STRING_LENGTH -> {
                    method.visitTypeInsn(CHECKCAST, "java/lang/String");
                    method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
                    method.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                }
                case OP_INT_EQ -> {
                    method.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                    method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                    method.visitVarInsn(ISTORE, 14);
                    method.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                    method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                    method.visitVarInsn(ILOAD, 14);
                    Label yes = new Label(), compared = new Label();
                    method.visitJumpInsn(IF_ICMPEQ, yes); method.visitInsn(ICONST_0); method.visitJumpInsn(GOTO, compared);
                    method.visitLabel(yes); method.visitInsn(ICONST_1); method.visitLabel(compared);
                }
                case OP_OBJECT_EQUALS -> method.visitMethodInsn(INVOKESTATIC, "java/util/Objects", "equals",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                case OP_NEW_LONG_ARRAY -> {
                    method.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                    method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                    method.visitIntInsn(NEWARRAY, T_LONG);
                }
                case OP_ASYNC_LOAD -> {
                    method.visitTypeInsn(CHECKCAST, "java/lang/String");
                    method.visitMethodInsn(INVOKESTATIC, GenerateModule.STORE, "refresh", "(Ljava/lang/String;)V", false);
                }
                case OP_ARG_LONG -> {
                    int index = code[at++] & 0xff;
                    method.visitVarInsn(ALOAD, 1); GenerateModule.integer(method, index);
                    method.visitMethodInsn(INVOKEINTERFACE, GenerateModule.CHAIN, "getArg",
                            "(I)Ljava/lang/Object;", true);
                    method.visitTypeInsn(CHECKCAST, "java/lang/Long");
                    method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                }
                case OP_INT_VALUE -> {
                    require(code, at, 4);
                    int value = (code[at] & 0xff) << 24 | (code[at + 1] & 0xff) << 16
                            | (code[at + 2] & 0xff) << 8 | code[at + 3] & 0xff;
                    at += 4; GenerateModule.integer(method, value);
                }
                case OP_LONG_VALUE -> {
                    require(code, at, 8); long value = 0;
                    for (int i = 0; i < 8; i++) value = value << 8 | code[at + i] & 0xffL;
                    at += 8; method.visitLdcInsn(value);
                }
                case OP_INT_LOCAL_GET -> method.visitVarInsn(ILOAD, 40 + (code[at++] & 0xff));
                case OP_INT_LOCAL_SET -> method.visitVarInsn(ISTORE, 40 + (code[at++] & 0xff));
                case OP_LONG_LOCAL_GET -> method.visitVarInsn(LLOAD, 48 + 2 * (code[at++] & 0xff));
                case OP_LONG_LOCAL_SET -> method.visitVarInsn(LSTORE, 48 + 2 * (code[at++] & 0xff));
                case OP_LONG_ADD_VALUE -> method.visitInsn(LADD);
                case OP_STATE_LONG_ARRAY -> {
                    GenerateModule.integer(method, code[at++] & 0xff);
                    method.visitMethodInsn(INVOKESTATIC, GenerateModule.STATE, "get", "(I)Ljava/lang/Object;", false);
                    method.visitTypeInsn(CHECKCAST, "[J");
                }
                default -> throw new IllegalArgumentException("unsupported MBP1 opcode " + opcode);
            }
        }
    }

    private static void require(byte[] code, int at, int count) {
        if (at + count > code.length) throw new IllegalArgumentException("truncated MBP1 instruction");
    }

    private static int unsignedShort(byte[] code, int at) {
        return (code[at] & 0xff) << 8 | code[at + 1] & 0xff;
    }

    private static Map<Integer, Label> labels(byte[] code) {
        Map<Integer, Label> labels = new HashMap<>();
        int at = 0;
        labels.put(0, new Label());
        while (at < code.length) {
            int opcode = code[at++] & 0xff;
            int size = switch (opcode) {
                case OP_ARG, OP_LOCAL_GET, OP_LOCAL_SET, OP_STATE_GET, OP_STATE_SET,
                        OP_ARG_LONG, OP_INT_LOCAL_GET, OP_INT_LOCAL_SET, OP_LONG_LOCAL_GET,
                        OP_LONG_LOCAL_SET, OP_STATE_LONG_ARRAY -> 1;
                case OP_CONST_INT, OP_SET_VISIBILITY, OP_INT_VALUE -> 4;
                case OP_MEMBER_GET, OP_STRING_CONTAINS, OP_LOG -> 2;
                case OP_MEMBER_CALL -> 3;
                case OP_BOUND_FIELD_GET -> 5;
                case OP_JUMP, OP_JUMP_FALSE, OP_JUMP_NULL -> 2;
                case OP_CONST_LONG, OP_LONG_VALUE -> 8;
                default -> 0;
            };
            require(code, at, size);
            if (opcode == OP_JUMP || opcode == OP_JUMP_FALSE || opcode == OP_JUMP_NULL) {
                int target = at + 2 + (short) unsignedShort(code, at);
                if (target < 0 || target > code.length) throw new IllegalArgumentException("invalid MBP1 branch");
                labels.computeIfAbsent(target, ignored -> new Label());
            }
            at += size;
        }
        labels.computeIfAbsent(code.length, ignored -> new Label());
        return labels;
    }

    static boolean failOpen(GenerateModule.Hook hook) {
        return hook.arguments().size() > 2 && hook.arguments().get(0).equals("MBP1")
                && hook.arguments().get(2).equals("1");
    }

    static String constant(GenerateModule.Hook hook, int index) {
        int count = Integer.parseInt(hook.arguments().get(3));
        if (index < 0 || index >= count) throw new IllegalArgumentException("MBP1 constant index");
        return hook.arguments().get(4 + index);
    }

    static boolean uses(GenerateModule.Hook hook, int wanted) {
        List<String> arguments = hook.arguments();
        int constants = Integer.parseInt(arguments.get(3));
        int membersAt = 4 + constants;
        int members = Integer.parseInt(arguments.get(membersAt));
        byte[] code = HexFormat.of().parseHex(arguments.get(membersAt + 1 + members));
        int at = 0;
        while (at < code.length) {
            int opcode = code[at++] & 0xff;
            if (opcode == wanted) return true;
            at += switch (opcode) {
                case OP_ARG, OP_LOCAL_GET, OP_LOCAL_SET, OP_STATE_GET, OP_STATE_SET,
                        OP_ARG_LONG, OP_INT_LOCAL_GET, OP_INT_LOCAL_SET, OP_LONG_LOCAL_GET,
                        OP_LONG_LOCAL_SET, OP_STATE_LONG_ARRAY -> 1;
                case OP_CONST_INT, OP_SET_VISIBILITY, OP_INT_VALUE -> 4;
                case OP_MEMBER_GET, OP_STRING_CONTAINS, OP_LOG, OP_JUMP, OP_JUMP_FALSE, OP_JUMP_NULL -> 2;
                case OP_MEMBER_CALL -> 3;
                case OP_BOUND_FIELD_GET -> 5;
                case OP_CONST_LONG, OP_LONG_VALUE -> 8;
                default -> 0;
            };
        }
        return false;
    }

    static int[] members(GenerateModule.Hook hook) {
        List<String> arguments = hook.arguments();
        int constants = Integer.parseInt(arguments.get(3));
        int membersAt = 4 + constants;
        int count = Integer.parseInt(arguments.get(membersAt));
        int[] result = new int[count];
        for (int index = 0; index < count; index++) {
            result[index] = Integer.parseInt(arguments.get(membersAt + 1 + index));
        }
        return result;
    }

    static List<int[]> fieldBindings(GenerateModule.Hook hook) {
        List<String> arguments = hook.arguments();
        int constants = Integer.parseInt(arguments.get(3));
        int membersAt = 4 + constants;
        int members = Integer.parseInt(arguments.get(membersAt));
        byte[] code = HexFormat.of().parseHex(arguments.get(membersAt + 1 + members));
        List<int[]> result = new ArrayList<>();
        int at = 0;
        while (at < code.length) {
            int opcode = code[at++] & 0xff;
            if (opcode == OP_BOUND_FIELD_GET) {
                result.add(new int[] {unsignedShort(code, at), unsignedShort(code, at + 2), code[at + 4] & 0xff});
            }
            at += switch (opcode) {
                case OP_ARG, OP_LOCAL_GET, OP_LOCAL_SET, OP_STATE_GET, OP_STATE_SET,
                        OP_ARG_LONG, OP_INT_LOCAL_GET, OP_INT_LOCAL_SET, OP_LONG_LOCAL_GET,
                        OP_LONG_LOCAL_SET, OP_STATE_LONG_ARRAY -> 1;
                case OP_CONST_INT, OP_SET_VISIBILITY, OP_INT_VALUE -> 4;
                case OP_MEMBER_GET, OP_STRING_CONTAINS, OP_LOG,
                        OP_JUMP, OP_JUMP_FALSE, OP_JUMP_NULL -> 2;
                case OP_MEMBER_CALL -> 3;
                case OP_BOUND_FIELD_GET -> 5;
                case OP_CONST_LONG, OP_LONG_VALUE -> 8;
                default -> 0;
            };
        }
        return result;
    }
}
