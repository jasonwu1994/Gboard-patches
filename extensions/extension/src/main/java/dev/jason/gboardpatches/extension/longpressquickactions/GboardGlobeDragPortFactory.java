package dev.jason.gboardpatches.extension.longpressquickactions;

/** Single version-selection seam for the stable globe-drag runtime. */
final class GboardGlobeDragPortFactory {
    private GboardGlobeDragPortFactory() {
    }

    static GboardGlobeDragPort create(ClassLoader classLoader) throws Throwable {
        return new GboardGlobeDragAdapter(
                GboardLongPressQuickActionsRuntime.reflectionHandles(classLoader));
    }
}
