package io.github.mrxsin.ytmalbolge;

import android.content.pm.ApplicationInfo;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.FieldData;
import org.luckypray.dexkit.result.MethodData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/** Generic cached descriptor resolver. No feature IDs or behavior live here. */
public final class RuntimeResolver {
    private static final String TAG = "YtmResolver";
    private static final String MODULE_RESOLVER_SCHEMA = "1.0.0:resolver-v3";
    private static final Object LOCK = new Object();
    private static ApplicationInfo application;
    private static String identity;
    private static Properties cache;
    private static File cacheFile;
    private static DexKitBridge bridge;
    private static int exactWeight, ownerWeight, nameWeight, accessWeight, parentWeight, opcodeWeight, minimumScore;

    private RuntimeResolver() {}

    public static Object debug(Object value) {
        if (value == null) return null;
        for (Field field : value.getClass().getDeclaredFields()) {
            if (field.getType() != String.class) continue;
            try {
                field.setAccessible(true);
                Object fieldValue = field.get(value);
                if (fieldValue != null) Log.i(TAG, value.getClass().getName() + "." + field.getName() + "=" + fieldValue);
            } catch (Throwable ignored) {}
        }
        return value;
    }

    public static void configure(int exact, int owner, int name, int access, int parent, int opcode, int minimum) {
        exactWeight = exact; ownerWeight = owner; nameWeight = name;
        accessWeight = access; parentWeight = parent; opcodeWeight = opcode; minimumScore = minimum;
    }

    public static void initialize(ApplicationInfo info, String authorityDigest) throws Exception {
        synchronized (LOCK) {
            application = info;
            identity = identity(info, authorityDigest);
            cacheFile = new File(new File(info.dataDir, "cache"), "ytm-resolution-v3.properties");
            cache = new Properties();
            if (cacheFile.isFile()) try (FileInputStream input = new FileInputStream(cacheFile)) {
                cache.load(input);
            }
            if (!identity.equals(cache.getProperty("identity"))) {
                cache.clear();
                cache.setProperty("identity", identity);
            }
        }
    }

    public static Executable executable(ClassLoader loader, String kind, String owner, String name,
                                        String descriptor, int access, String superclass,
                                        int opcodeCount) throws Exception {
        synchronized (LOCK) {
            String key = key("x", kind, owner, name, descriptor);
            Executable cached = cachedExecutable(loader, cache.getProperty(key), access, superclass, descriptor);
            if (cached != null) return cached;
            Executable exact = null;
            try {
                exact = exactExecutable(loader, kind, owner, name, descriptor);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // The checked-in descriptor is only a cheap fallback fixture; updates continue to DexKit.
            }
            if (valid(exact, access, superclass, descriptor)) {
                put(key, encode(exact));
                return exact;
            }
            MethodMatcher matcher = MethodMatcher.create().declaredClass(owner)
                    .protoShorty(shorty(descriptor)).modifiers(access);
            if ("CONSTRUCTOR".equals(kind)) matcher.name("<init>");
            List<MethodData> found = new ArrayList<>(dex(loader).findMethod(FindMethod.create().matcher(matcher)));
            MethodData winner = uniqueBest(found, owner, name, descriptor, access, superclass, opcodeCount);
            if (winner == null) {
                matcher = MethodMatcher.create().protoShorty(shorty(descriptor)).modifiers(access);
                if ("CONSTRUCTOR".equals(kind)) matcher.name("<init>");
                found = new ArrayList<>(dex(loader).findMethod(FindMethod.create().matcher(matcher)));
                winner = uniqueBest(found, owner, name, descriptor, access, superclass, opcodeCount);
            }
            if (winner == null) return null;
            Executable resolved = winner.isConstructor()
                    ? winner.getConstructorInstance(loader) : winner.getMethodInstance(loader);
            if (!valid(resolved, access, superclass, descriptor)) return null;
            resolved.setAccessible(true);
            put(key, encode(resolved));
            return resolved;
        }
    }

    public static Field field(ClassLoader loader, String owner, String name, String descriptor,
                              int access, String superclass) throws Exception {
        synchronized (LOCK) {
            String key = key("f", "FIELD", owner, name, descriptor);
            Field cached = cachedField(loader, cache.getProperty(key), access, superclass, descriptor);
            if (cached != null) return cached;
            Field exact = null;
            try {
                exact = exactField(loader, owner, name);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // The checked-in descriptor is only a cheap fallback fixture; updates continue to DexKit.
            }
            if (valid(exact, access, superclass, descriptor)) {
                put(key, encode(exact));
                return exact;
            }
            List<FieldData> found = new ArrayList<>(dex(loader).findField(FindField.create().matcher(
                    FieldMatcher.create().declaredClass(owner).modifiers(access))));
            Field winner = uniqueField(found, loader, access, superclass, descriptor);
            if (winner == null) {
                found = new ArrayList<>(dex(loader).findField(FindField.create().matcher(
                        FieldMatcher.create().modifiers(access))));
                winner = uniqueField(found, loader, access, superclass, descriptor);
            }
            if (winner != null) put(key, encode(winner));
            return winner;
        }
    }

    private static Field uniqueField(List<FieldData> found, ClassLoader loader, int access,
                                     String superclass, String descriptor) throws Exception {
            found.sort(Comparator.comparing(FieldData::getDescriptor));
            Field winner = null;
            for (FieldData item : found) {
                Field candidate = item.getFieldInstance(loader);
                if (!valid(candidate, access, superclass, descriptor)) continue;
                if (winner != null) return null;
                candidate.setAccessible(true);
                winner = candidate;
            }
            return winner;
    }

    public static void finish() {
        synchronized (LOCK) {
            if (bridge != null) {
                try {
                    bridge.close();
                } catch (Throwable error) {
                    Log.w(TAG, "DexKit close failed", error);
                } finally {
                    bridge = null;
                }
            }
        }
    }

    private static DexKitBridge dex(ClassLoader loader) {
        if (bridge == null) {
            System.loadLibrary("dexkit");
            bridge = DexKitBridge.create(loader, true);
            bridge.setThreadNum(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors())));
        }
        return bridge;
    }

    private static MethodData uniqueBest(List<MethodData> found, String owner, String name,
                                         String descriptor, int access, String superclass,
                                         int opcodeCount) {
        MethodData winner = null;
        int best = Integer.MIN_VALUE;
        boolean tied = false;
        for (MethodData item : found) {
            int score = 0;
            if (item.getDescriptor().equals("L" + owner.replace('.', '/') + ";->" + name + descriptor)) score += exactWeight;
            if (item.getDeclaredClassName().equals(owner)) score += ownerWeight;
            if (item.getName().equals(name)) score += nameWeight;
            if ((item.getModifiers() & 29) == access) score += accessWeight;
            if (item.getDeclaredClass().getSuperClass() != null
                    && sameParent(item.getDeclaredClass().getSuperClass().getName(), superclass)) score += parentWeight;
            if (opcodeCount >= 0 && close(item.getOpCodes().size(), opcodeCount)) score += opcodeWeight;
            if (score > best) { best = score; winner = item; tied = false; }
            else if (score == best) tied = true;
        }
        return !tied && best >= minimumScore ? winner : null;
    }

    private static Executable cachedExecutable(ClassLoader loader, String value, int access,
                                               String superclass, String descriptor) {
        if (value == null) return null;
        try {
            String[] p = value.split("\\|", -1);
            Executable result = exactExecutable(loader, p[0], p[1], p[2], p[3]);
            return valid(result, access, superclass, descriptor) ? result : null;
        } catch (Throwable ignored) { return null; }
    }

    private static Field cachedField(ClassLoader loader, String value, int access, String superclass,
                                     String descriptor) {
        if (value == null) return null;
        try {
            String[] p = value.split("\\|", -1);
            Field result = exactField(loader, p[1], p[2]);
            return valid(result, access, superclass, descriptor) ? result : null;
        } catch (Throwable ignored) { return null; }
    }

    private static Executable exactExecutable(ClassLoader loader, String kind, String owner,
                                              String name, String descriptor) throws Exception {
        Class<?> type = Class.forName(owner, false, loader);
        Class<?>[] parameters = parameterTypes(descriptor, loader);
        Executable result = "CONSTRUCTOR".equals(kind)
                ? type.getDeclaredConstructor(parameters) : type.getDeclaredMethod(name, parameters);
        result.setAccessible(true);
        return result;
    }

    private static Field exactField(ClassLoader loader, String owner, String name) throws Exception {
        Field result = Class.forName(owner, false, loader).getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static boolean valid(Executable value, int access, String superclass, String descriptor) {
        return value != null && (value.getModifiers() & 29) == access
                && value.getDeclaringClass().getSuperclass() != null
                && sameParent(value.getDeclaringClass().getSuperclass().getName(), superclass)
                && shape(descriptor(value)).equals(shape(descriptor));
    }

    private static boolean valid(Field value, int access, String superclass, String descriptor) {
        return value != null && (value.getModifiers() & 29) == access
                && value.getDeclaringClass().getSuperclass() != null
                && sameParent(value.getDeclaringClass().getSuperclass().getName(), superclass)
                && shape(descriptor(value.getType())).equals(shape(descriptor));
    }

    private static boolean sameParent(String actual, String expected) {
        return !(expected.startsWith("android.") || expected.startsWith("androidx.")
                || expected.startsWith("java.")) || actual.equals(expected);
    }

    private static boolean close(int actual, int expected) {
        return Math.abs(actual - expected) <= Math.max(4, expected / 5);
    }

    private static String shape(String descriptor) {
        return descriptor.replaceAll("L[^;]+;", "L;");
    }

    private static void put(String key, String value) {
        cache.setProperty(key, value);
        cacheFile.getParentFile().mkdirs();
        try (FileOutputStream output = new FileOutputStream(cacheFile)) {
            cache.store(output, "YouTubeMalbolge resolver cache");
        } catch (Throwable error) {
            Log.w(TAG, "cache write failed", error);
        }
    }

    private static String encode(Executable value) {
        String kind = value instanceof Constructor ? "CONSTRUCTOR" : "METHOD";
        return kind + "|" + value.getDeclaringClass().getName() + "|" + value.getName()
                + "|" + descriptor(value);
    }

    private static String encode(Field value) {
        return "FIELD|" + value.getDeclaringClass().getName() + "|" + value.getName() + "|" + descriptor(value.getType());
    }

    private static String key(String prefix, String kind, String owner, String name, String descriptor) {
        return prefix + "." + Integer.toHexString((kind + owner + name + descriptor).hashCode());
    }

    private static String identity(ApplicationInfo info, String authorityDigest) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(authorityDigest.getBytes(StandardCharsets.US_ASCII));
        digest.update(MODULE_RESOLVER_SCHEMA.getBytes(StandardCharsets.US_ASCII));
        digest.update(info.packageName.getBytes(StandardCharsets.UTF_8));
        try {
            Context context = (Context) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            if (context != null) {
                PackageInfo installed = context.getPackageManager().getPackageInfo(info.packageName, 0);
                digest.update(Long.toString(installed.getLongVersionCode()).getBytes(StandardCharsets.US_ASCII));
                if (installed.versionName != null) digest.update(installed.versionName.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable error) {
            Log.w(TAG, "version identity unavailable", error);
        }
        List<String> paths = new ArrayList<>();
        paths.add(info.sourceDir);
        if (info.splitSourceDirs != null) for (String path : info.splitSourceDirs) paths.add(path);
        paths.sort(String::compareTo);
        for (String path : paths) {
            File file = new File(path);
            digest.update(path.getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(file.length()).getBytes(StandardCharsets.US_ASCII));
            digest.update(Long.toString(file.lastModified()).getBytes(StandardCharsets.US_ASCII));
        }
        StringBuilder text = new StringBuilder();
        for (byte b : digest.digest()) text.append(String.format("%02x", b & 255));
        return text.toString();
    }

    private static String shorty(String descriptor) {
        StringBuilder result = new StringBuilder();
        int end = descriptor.indexOf(')');
        result.append(shortType(descriptor, end + 1));
        for (int at = 1; at < end;) {
            result.append(shortType(descriptor, at));
            while (descriptor.charAt(at) == '[') at++;
            at = descriptor.charAt(at) == 'L' ? descriptor.indexOf(';', at) + 1 : at + 1;
        }
        return result.toString();
    }

    private static char shortType(String descriptor, int at) {
        char type = descriptor.charAt(at);
        return type == '[' || type == 'L' ? 'L' : type;
    }

    private static Class<?>[] parameterTypes(String descriptor, ClassLoader loader) throws Exception {
        List<Class<?>> result = new ArrayList<>();
        int at = 1;
        while (descriptor.charAt(at) != ')') {
            int start = at;
            while (descriptor.charAt(at) == '[') at++;
            if (descriptor.charAt(at) == 'L') at = descriptor.indexOf(';', at) + 1;
            else at++;
            result.add(type(descriptor.substring(start, at), loader));
        }
        return result.toArray(Class<?>[]::new);
    }

    private static Class<?> type(String descriptor, ClassLoader loader) throws Exception {
        if (descriptor.startsWith("[")) return Class.forName(descriptor.replace('/', '.'), false, loader);
        if (descriptor.startsWith("L"))
            return Class.forName(descriptor.substring(1, descriptor.length() - 1).replace('/', '.'), false, loader);
        return switch (descriptor) {
            case "Z" -> boolean.class; case "B" -> byte.class; case "C" -> char.class;
            case "S" -> short.class; case "I" -> int.class; case "J" -> long.class;
            case "F" -> float.class; case "D" -> double.class; case "V" -> void.class;
            default -> throw new ClassNotFoundException(descriptor);
        };
    }

    private static String descriptor(Executable value) {
        StringBuilder result = new StringBuilder("(");
        for (Class<?> p : value.getParameterTypes()) result.append(descriptor(p));
        result.append(')');
        result.append(value instanceof Method ? descriptor(((Method) value).getReturnType()) : "V");
        return result.toString();
    }

    private static String descriptor(Class<?> type) {
        if (type.isArray()) return type.getName().replace('.', '/');
        if (!type.isPrimitive()) return "L" + type.getName().replace('.', '/') + ";";
        if (type == void.class) return "V"; if (type == boolean.class) return "Z";
        if (type == byte.class) return "B"; if (type == char.class) return "C";
        if (type == short.class) return "S"; if (type == int.class) return "I";
        if (type == long.class) return "J"; if (type == float.class) return "F";
        return "D";
    }
}
