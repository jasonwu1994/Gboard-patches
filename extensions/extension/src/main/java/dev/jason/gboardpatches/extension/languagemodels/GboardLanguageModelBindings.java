package dev.jason.gboardpatches.extension.languagemodels;

import android.content.Context;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Member references packaged with the selected, reviewed target transformation. */
final class GboardLanguageModelBindings {
    private static final ConcurrentHashMap<String, Field> FIELDS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Method> METHODS = new ConcurrentHashMap<>();
    private static volatile JSONObject bindings;
    private GboardLanguageModelBindings() { }

    private static String reference(String key) throws Exception {
        JSONObject value = bindings;
        if (value == null) {
            Context context = GboardLanguageModelRuntime.application();
            if (context == null) throw new IllegalStateException("Application unavailable");
            try (var input = context.getAssets().open("language-models/target-bindings.json")) {
                java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) data.write(buffer, 0, count);
                bindings = value = new JSONObject(data.toString(StandardCharsets.UTF_8.name()));
            }
        }
        return value.getString(key);
    }

    static String name(String key) throws Exception {
        String value = reference(key).split("->", 2)[1];
        int end = value.indexOf('(');
        if (end < 0) end = value.indexOf(':');
        return value.substring(0, end);
    }

    static String owner(String key) throws Exception {
        String value = reference(key).split("->", 2)[0];
        return value.substring(1, value.length() - 1).replace('/', '.');
    }

    static Field field(Object receiver, String key) throws Exception {
        Field cached = FIELDS.get(key);
        if (cached != null) return cached;
        Class<?> type = Class.forName(owner(key), false, receiver.getClass().getClassLoader());
        Field field = type.getDeclaredField(name(key));
        field.setAccessible(true);
        FIELDS.putIfAbsent(key, field);
        return field;
    }

    static Object read(Object receiver, String key) throws Exception { return field(receiver, key).get(receiver); }

    static Method method(Object receiver, String key, Class<?>... parameters) throws Exception {
        Method cached = METHODS.get(key);
        if (cached != null) return cached;
        Class<?> type = Class.forName(owner(key), false, receiver.getClass().getClassLoader());
        Method method = type.getMethod(name(key), parameters);
        method.setAccessible(true);
        METHODS.putIfAbsent(key, method);
        return method;
    }

    static Object call(Object receiver, String key) throws Exception { return method(receiver, key).invoke(receiver); }
}
