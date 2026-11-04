package dev.jason.gboardpatches.extension.longpressquickactions;

import android.view.inputmethod.InputConnection;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class GboardSelectAllActionRuntimeTest {
    private static final int[] COPY_AND_CUT = {android.R.id.copy, android.R.id.cut};

    @Test
    public void disabledOptionLeavesUnselectedCopyAndCutAsNoOps() {
        for (int action : COPY_AND_CUT) {
            for (String selection : new String[] {null, ""}) {
                Editor editor = new Editor(selection, "whole text");
                Assert.assertFalse(GboardLongPressQuickActionsRuntime
                        .attemptContextMenuAction(editor.connection, action, false));
                editor.assertCalls("selection");
                editor.calls.clear();
                editor.reads = 0;
                Assert.assertTrue(GboardLongPressQuickActionsRuntime
                        .consumeRecognizedContextMenuAction(
                                () -> editor.connection, action, false));
                editor.assertCalls("selection");
            }
        }
    }

    @Test
    public void disabledOptionStillAllowsSelectedCopyAndCut() {
        for (int action : COPY_AND_CUT) {
            Editor editor = new Editor("selected text", "whole text");
            Assert.assertTrue(GboardLongPressQuickActionsRuntime
                    .attemptContextMenuAction(editor.connection, action, false));
            editor.assertCalls("selection", action);
        }
    }

    @Test
    public void existingSelectionIncludingWhitespaceIsUsedWithoutSelectAll() {
        for (int action : COPY_AND_CUT) {
            for (String selection : new String[] {"selected text", " "}) {
                Editor editor = new Editor(selection, "whole text");
                Assert.assertTrue(GboardLongPressQuickActionsRuntime
                        .attemptContextMenuAction(editor.connection, action, true));
                editor.assertCalls("selection", action);
            }
        }
    }

    @Test
    public void absentSelectionSelectsAllOnceBeforeCopyOrCut() {
        for (int action : COPY_AND_CUT) {
            for (String selection : new String[] {null, ""}) {
                Editor editor = new Editor(selection, "whole text");
                Assert.assertTrue(GboardLongPressQuickActionsRuntime
                        .attemptContextMenuAction(editor.connection, action, true));
                editor.assertCalls("selection", android.R.id.selectAll, "selection", action);
            }
        }
    }

    @Test
    public void stillEmptyAfterSelectAllStopsWithoutCopyCutOrRetry() {
        for (int action : COPY_AND_CUT) {
            for (String selection : new String[] {null, ""}) {
                Editor editor = new Editor(null, selection);
                Assert.assertFalse(GboardLongPressQuickActionsRuntime
                        .attemptContextMenuAction(editor.connection, action, true));
                editor.assertCalls("selection", android.R.id.selectAll, "selection");
                assertConsumed(editor, action);
            }
        }
    }

    @Test
    public void selectedWhitespaceAfterSelectAllIsValidContent() {
        for (int action : COPY_AND_CUT) {
            Editor editor = new Editor(null, " ");
            Assert.assertTrue(GboardLongPressQuickActionsRuntime
                    .attemptContextMenuAction(editor.connection, action, true));
            editor.assertCalls("selection", android.R.id.selectAll, "selection", action);
        }
    }

    @Test
    public void rejectedSelectAllStopsBeforeSecondRead() {
        for (int action : COPY_AND_CUT) {
            Editor editor = new Editor(null, "whole text");
            editor.selectAllAccepted = false;
            Assert.assertFalse(GboardLongPressQuickActionsRuntime
                    .attemptContextMenuAction(editor.connection, action, true));
            editor.assertCalls("selection", android.R.id.selectAll);
            assertConsumed(editor, action);
        }
    }

    @Test
    public void selectionReadExceptionsAreConsumedWithoutFurtherActions() {
        for (int action : COPY_AND_CUT) {
            for (int failedRead : new int[] {1, 2}) {
                Editor editor = new Editor(null, "whole text");
                editor.failedRead = failedRead;
                assertConsumed(editor, action);
                if (failedRead == 1) {
                    editor.assertCalls("selection");
                } else {
                    editor.assertCalls("selection", android.R.id.selectAll, "selection");
                }
            }
        }
    }

    @Test
    public void actionExceptionsAreConsumedWithoutRetry() {
        for (int action : COPY_AND_CUT) {
            for (int failedAction : new int[] {android.R.id.selectAll, action}) {
                Editor editor = new Editor(null, "whole text");
                editor.failedAction = failedAction;
                assertConsumed(editor, action);
                if (failedAction == android.R.id.selectAll) {
                    editor.assertCalls("selection", android.R.id.selectAll);
                } else {
                    editor.assertCalls("selection", android.R.id.selectAll, "selection", action);
                }
            }
        }
    }

    @Test
    public void rejectedCopyOrCutIsNotRetriedAndEventIsConsumed() {
        for (int action : COPY_AND_CUT) {
            Editor editor = new Editor(null, "whole text");
            editor.actionAccepted = false;
            Assert.assertFalse(GboardLongPressQuickActionsRuntime
                    .attemptContextMenuAction(editor.connection, action, true));
            editor.assertCalls("selection", android.R.id.selectAll, "selection", action);
            assertConsumed(editor, action);
        }
    }

    @Test
    public void otherActionsDoNotReadSelectionOrAddSelectAll() {
        for (int action : new int[] {android.R.id.selectAll, android.R.id.paste}) {
            Editor editor = new Editor(null, null);
            Assert.assertTrue(GboardLongPressQuickActionsRuntime
                    .attemptContextMenuAction(editor.connection, action, true));
            editor.assertCalls(action);
        }
    }

    @Test
    public void missingOrThrowingConnectionSupplierStillConsumesRecognizedEvent() {
        Assert.assertFalse(GboardLongPressQuickActionsRuntime
                .attemptContextMenuAction(null, android.R.id.copy, true));
        Assert.assertTrue(GboardLongPressQuickActionsRuntime
                .consumeRecognizedContextMenuAction(() -> null, android.R.id.copy, true));
        Assert.assertTrue(GboardLongPressQuickActionsRuntime
                .consumeRecognizedContextMenuAction(() -> {
                    throw new IllegalStateException("editor unavailable");
                }, android.R.id.cut, true));
    }

    private static void assertConsumed(Editor editor, int action) {
        editor.calls.clear();
        editor.reads = 0;
        Assert.assertTrue(GboardLongPressQuickActionsRuntime
                .consumeRecognizedContextMenuAction(() -> editor.connection, action, true));
    }

    private static final class Editor {
        final List<Object> calls = new ArrayList<>();
        final InputConnection connection;
        int reads;
        int failedRead;
        int failedAction;
        boolean selectAllAccepted = true;
        boolean actionAccepted = true;

        Editor(CharSequence before, CharSequence after) {
            connection = (InputConnection) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {InputConnection.class},
                    (proxy, method, args) -> {
                        if ("getSelectedText".equals(method.getName())) {
                            calls.add("selection");
                            if (++reads == failedRead) {
                                throw new IllegalStateException("selection unavailable");
                            }
                            // Fail immediately if dispatch ever starts reading in a loop.
                            Assert.assertTrue("At most two selection reads", reads <= 2);
                            return reads == 1 ? before : after;
                        }
                        if ("performContextMenuAction".equals(method.getName())) {
                            int action = (Integer) args[0];
                            calls.add(action);
                            if (action == failedAction) {
                                throw new IllegalStateException("action unavailable");
                            }
                            return action == android.R.id.selectAll
                                    ? selectAllAccepted : actionAccepted;
                        }
                        throw new AssertionError("Unexpected InputConnection call: " + method);
                    });
        }

        void assertCalls(Object... expected) {
            Assert.assertEquals(Arrays.asList(expected), calls);
        }
    }
}
