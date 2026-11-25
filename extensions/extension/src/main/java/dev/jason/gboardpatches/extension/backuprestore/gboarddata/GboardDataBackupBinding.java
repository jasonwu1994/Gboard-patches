package dev.jason.gboardpatches.extension.backuprestore.gboarddata;

import android.content.Context;

import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Reflection handles into Gboard's own file backup framework.
 *
 * <p>Only three class names and two accessor names are version specific. Every other member is
 * derived from method signatures, so a new Gboard version only needs the names re-located:
 *
 * <ul>
 *   <li>restore dispatcher: {@code static boolean (Context, Map)} on the class that logs
 *       {@code FileBackupRestoreHelper};</li>
 *   <li>provider registry: {@code static ImmutableMap (Context, Class)} on the class that logs
 *       {@code BackupHelper};</li>
 *   <li>provider interface: the backup data provider interface with category, id, backup and
 *       restore methods.</li>
 * </ul>
 */
public final class GboardDataBackupBinding {
    /** Version-specific names supplied by the platform adapter. */
    public static final class Names {
        final String dispatcherClass;
        final String registryClass;
        final String providerClass;
        final String categoryMethod;
        final String idMethod;

        public Names(String dispatcherClass, String registryClass, String providerClass,
                String categoryMethod, String idMethod) {
            this.dispatcherClass = require(dispatcherClass, "dispatcherClass");
            this.registryClass = require(registryClass, "registryClass");
            this.providerClass = require(providerClass, "providerClass");
            this.categoryMethod = require(categoryMethod, "categoryMethod");
            this.idMethod = require(idMethod, "idMethod");
        }

        private static String require(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " is required");
            }
            return value;
        }

        @Override
        public String toString() {
            return dispatcherClass + "/" + registryClass + "/" + providerClass + "#"
                    + categoryMethod + "," + idMethod;
        }
    }

    final Names names;
    final Method registry;
    final Method dispatcher;
    final Method immutableMapCopy;
    final Class<?> providerType;
    final Method category;
    final Method id;
    final Method backup;
    final Method restore;
    final Constructor<?> collectorConstructor;
    final Method collectorClose;
    final Field collectorQuota;

    private GboardDataBackupBinding(Names names, Method registry, Method dispatcher,
            Method immutableMapCopy, Class<?> providerType, Method category, Method id,
            Method backup, Method restore, Constructor<?> collectorConstructor,
            Method collectorClose, Field collectorQuota) {
        this.names = names;
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.immutableMapCopy = immutableMapCopy;
        this.providerType = providerType;
        this.category = category;
        this.id = id;
        this.backup = backup;
        this.restore = restore;
        this.collectorConstructor = collectorConstructor;
        this.collectorClose = collectorClose;
        this.collectorQuota = collectorQuota;
    }

    public static GboardDataBackupBinding resolve(ClassLoader classLoader, Names names)
            throws ReflectiveOperationException {
        if (classLoader == null || names == null) {
            throw new IllegalArgumentException("Class loader and names are required");
        }
        Class<?> dispatcherClass = Class.forName(names.dispatcherClass, false, classLoader);
        Class<?> registryClass = Class.forName(names.registryClass, false, classLoader);
        Class<?> providerType = Class.forName(names.providerClass, false, classLoader);
        if (!providerType.isInterface()) {
            throw new IllegalStateException("Backup provider type is not an interface");
        }

        Method dispatcher = uniqueStatic(dispatcherClass, "restore dispatcher",
                boolean.class, Context.class, Map.class);
        Method registry = uniqueStatic(registryClass, "provider registry",
                null, Context.class, Class.class);
        Class<?> immutableMapType = registry.getReturnType();
        if (!Map.class.isAssignableFrom(immutableMapType)) {
            throw new IllegalStateException("Provider registry does not return a Map");
        }
        Method immutableMapCopy = uniqueStatic(immutableMapType, "immutable map copy",
                immutableMapType, Map.class);

        Method category = providerType.getMethod(names.categoryMethod);
        Method id = providerType.getMethod(names.idMethod);
        if (category.getReturnType() != String.class || id.getReturnType() != String.class
                || category.equals(id)) {
            throw new IllegalStateException("Provider category/id accessors are not distinct "
                    + "String methods");
        }
        Method restore = null;
        Method backup = null;
        Constructor<?> collectorConstructor = null;
        for (Method method : providerType.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != 1 || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (parameters[0] == Map.class
                    && Collection.class.isAssignableFrom(method.getReturnType())) {
                restore = requireSingle(restore, method, "provider restore");
                continue;
            }
            if (method.getReturnType() == void.class
                    && AutoCloseable.class.isAssignableFrom(parameters[0])) {
                Constructor<?> constructor;
                try {
                    constructor = parameters[0].getDeclaredConstructor(OutputStream.class);
                } catch (NoSuchMethodException notCollector) {
                    continue;
                }
                backup = requireSingle(backup, method, "provider backup");
                collectorConstructor = constructor;
            }
        }
        if (restore == null || backup == null) {
            throw new IllegalStateException("Provider backup/restore methods are unavailable");
        }
        Class<?> collectorType = collectorConstructor.getDeclaringClass();
        Method collectorClose = collectorType.getMethod("close");
        collectorConstructor.setAccessible(true);
        return new GboardDataBackupBinding(names, registry, dispatcher, immutableMapCopy,
                providerType, category, id, backup, restore, collectorConstructor,
                collectorClose, singleQuotaField(collectorType));
    }

    private static Method uniqueStatic(Class<?> owner, String label, Class<?> returnType,
            Class<?>... parameters) {
        Method found = null;
        for (Method method : owner.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())
                    || !java.util.Arrays.equals(method.getParameterTypes(), parameters)) {
                continue;
            }
            if (returnType != null && method.getReturnType() != returnType) {
                continue;
            }
            found = requireSingle(found, method, label);
        }
        if (found == null) {
            throw new IllegalStateException(label + " is unavailable on " + owner.getName());
        }
        found.setAccessible(true);
        return found;
    }

    private static Method requireSingle(Method current, Method candidate, String label) {
        if (current != null) {
            throw new IllegalStateException(label + " is ambiguous");
        }
        return candidate;
    }

    /** The collector keeps a byte budget in its only mutable long field; it is optional. */
    private static Field singleQuotaField(Class<?> collectorType) {
        List<Field> candidates = new ArrayList<>();
        for (Field field : collectorType.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (field.getType() == long.class && !Modifier.isStatic(modifiers)
                    && !Modifier.isFinal(modifiers)) {
                candidates.add(field);
            }
        }
        if (candidates.size() != 1) {
            return null;
        }
        Field field = candidates.get(0);
        field.setAccessible(true);
        return field;
    }

    public String describe() {
        return names.toString();
    }
}
