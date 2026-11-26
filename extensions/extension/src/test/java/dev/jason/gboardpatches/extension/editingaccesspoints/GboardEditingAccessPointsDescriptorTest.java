package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.content.Context;
import android.graphics.drawable.Icon;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class GboardEditingAccessPointsDescriptorTest {
    @Test public void registersEveryActionWithItsSelfDrawnIconAndNoTargetDrawable() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ClassLoader loader = getClass().getClassLoader();
        GboardEditingAccessPointsContribution.Handles handles =
                new GboardEditingAccessPointsContribution.Handles(loader);
        Object controller = Class.forName("mwv", false, loader).getDeclaredConstructor().newInstance();
        AtomicInteger called = new AtomicInteger();
        for (EditorAction action : EditorAction.values()) {
            Icon icon = GboardEditingAccessPointIcons.createIcon(context, action.token);
            assertNotNull(action.token, icon);
            int code = action.dispatchType == EditorAction.DispatchType.CONTEXT_MENU
                    ? action.contextMenuId : action.keyCode;
            handles.register(controller, action, icon,
                    GboardEditingAccessPointsContribution.label(action), () -> called.set(code));
        }
        Map<?, ?> descriptors = (Map<?, ?>) field(controller, "testDescriptors");
        assertEquals(EditorAction.values().length, descriptors.size());
        for (EditorAction action : EditorAction.values()) {
            Object descriptor = descriptors.get(action.token);
            assertEquals(GboardEditingAccessPointsContribution.label(action), field(descriptor, "f"));
            assertEquals(0, field(descriptor, "c"));
            Map<?, ?> actions = (Map<?, ?>) field(descriptor, "j");
            ((Runnable) field(actions.get("PRESS"), "payload")).run();
            assertEquals(action.dispatchType == EditorAction.DispatchType.CONTEXT_MENU
                    ? action.contextMenuId : action.keyCode, called.get());
        }
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getField(name);
        return field.get(owner);
    }
}
