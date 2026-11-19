package dev.sborka.incognita.weather.clouds.render;

import java.lang.reflect.Method;

/**
 * Detects an active Iris or Oculus shader pack through reflection, so there is no compile time dependency. With a
 * shader pack the cloud renderer steps aside: packs draw their own sky and clouds.
 */
public final class ShaderPackCheck {
    private static final String[] API_CLASSES = {
        "net.irisshaders.iris.api.v0.IrisApi",
        "net.coderbot.iris.apiimpl.IrisApiV0Impl"
    };
    private static Object api;
    private static Method inUse;
    private static boolean resolved;

    private ShaderPackCheck() {}

    public static boolean isShaderPackActive() {
        if (!resolved) {
            resolve();
        }
        if (api == null || inUse == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(inUse.invoke(api));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static void resolve() {
        resolved = true;
        for (String name : API_CLASSES) {
            try {
                Class<?> type = Class.forName(name);
                Object instance;
                try {
                    instance = type.getMethod("getInstance").invoke(null);
                } catch (NoSuchMethodException e) {
                    instance = type.getField("INSTANCE").get(null);
                }
                api = instance;
                inUse = type.getMethod("isShaderPackInUse");
                return;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Not installed; try the next known API class.
            }
        }
    }
}
