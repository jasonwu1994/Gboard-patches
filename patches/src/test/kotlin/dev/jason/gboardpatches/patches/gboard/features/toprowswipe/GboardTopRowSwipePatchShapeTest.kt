package dev.jason.gboardpatches.patches.gboard.features.toprowswipe

import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings
import dev.jason.gboardpatches.patches.gboard.registry.gboardCustomTopRowSwipePatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class GboardTopRowSwipePatchShapeTest {
    @Test
    fun `public patch remains applied by default`() {
        assertTrue(gboardCustomTopRowSwipePatch.default)
    }

    @Test
    fun `softkey delegates point at the expected runtime methods`() {
        assertTrue(TOP_ROW_SWIPE_PATCH_INCOMING_METADATA_DELEGATE.contains("patchIncomingSoftKeyMetadata"))
        assertTrue(TOP_ROW_SWIPE_AFTER_BIND_DELEGATE.contains("afterSoftKeyBound"))
        assertTrue(TOP_ROW_SWIPE_PATCH_INCOMING_METADATA_DELEGATE.contains(TOP_ROW_SWIPE_RUNTIME_CLASS))
        assertTrue(
            TOP_ROW_SWIPE_AFTER_BIND_DELEGATE.contains(
                "afterSoftKeyBound(Ljava/lang/Object;)V"
            )
        )
        assertFalse(TOP_ROW_SWIPE_AFTER_BIND_DELEGATE.contains("p1"))
    }

    @Test
    fun `softkey rewrite stays at entry while visible tracking runs before every return`() {
        assertTrue(TOP_ROW_SWIPE_ENTRY_DELEGATE.contains("patchIncomingSoftKeyMetadata"))
        assertFalse(TOP_ROW_SWIPE_ENTRY_DELEGATE.contains("afterSoftKeyBound"))
        assertTrue(TOP_ROW_SWIPE_ENTRY_DELEGATE.contains(TOP_ROW_SWIPE_RUNTIME_CLASS))

        val source = readSource(
            "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/toprowswipe/" +
                "GboardTopRowSwipeSoftKeyPatch.kt"
        )
        assertTrue(source.contains("mutableMethod.addInstructions(0, TOP_ROW_SWIPE_ENTRY_DELEGATE)"))
        assertTrue(source.contains("mutableMethod.returnInstructionIndices()"))
        assertTrue(
            source.contains(
                "mutableMethod.addInstructions(returnIndex, TOP_ROW_SWIPE_AFTER_BIND_DELEGATE)"
            )
        )
    }

    @Test
    fun `target q fixture keeps result register intact at return tracking site`() {
        val targetLikeFixture = """
            .method public final q(Lowd;J)Z
                .locals 3
                iput-object p1, p0, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->e:Lowd;
                move p3, p2
                invoke-static {}, Landroid/os/Trace;->endSection()V
                return p3
                :catchall_0
                move-exception p0
                invoke-static {}, Landroid/os/Trace;->endSection()V
                throw p0
            .end method
        """.trimIndent()

        assertEquals(1, Regex("""(?m)^\s*return\s+p3$""").findAll(targetLikeFixture).count())
        assertEquals(1, Regex("""(?m)^\s*throw\s+p0$""").findAll(targetLikeFixture).count())
        assertTrue(TOP_ROW_SWIPE_AFTER_BIND_DELEGATE.contains("invoke-static {p0}"))
        assertFalse(TOP_ROW_SWIPE_AFTER_BIND_DELEGATE.contains("move-result"))
    }

    @Test
    fun `pointer and gesture delegates stay on the agreed runtime contract`() {
        assertTrue(TOP_ROW_SWIPE_POINTER_DELEGATE.contains("maybeArmAndResolveTopRowOwner"))
        assertTrue(TOP_ROW_SWIPE_FINISH_SESSION_DELEGATE.contains("finishSwipeSession"))
        assertTrue(TOP_ROW_SWIPE_CLEAR_SESSION_DELEGATE.contains("clearSwipeSession"))
        assertTrue(TOP_ROW_SWIPE_GESTURE_OWNER_DELEGATE.contains("jasondevDispatchWithTopRow"))
        assertTrue(TOP_ROW_SWIPE_POINTER_DELEGATE.contains(TOP_ROW_SWIPE_RUNTIME_CLASS))
        assertTrue(TOP_ROW_SWIPE_DISPATCH_WITH_TOP_ROW_BODY.contains("maybeConsumeTopRowSwipe"))
        assertTrue(
            TOP_ROW_SWIPE_DISPATCH_WITH_TOP_ROW_BODY.contains(
                "invoke-static {p0, p1, p2, p3, p4}, $TOP_ROW_SWIPE_RUNTIME_CLASS->maybeConsumeQuickJsTopRowPress"
            )
        )
        assertTrue(
            TOP_ROW_SWIPE_DISPATCH_WITH_TOP_ROW_BODY.contains(
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z"
            )
        )
        assertTrue(TOP_ROW_SWIPE_DISPATCH_WITH_TOP_ROW_BODY.contains("jasondevDispatchOrToggle"))
        assertTrue(TOP_ROW_SWIPE_DISPATCH_WITH_TOP_ROW_BODY.contains(TOP_ROW_SWIPE_RUNTIME_CLASS))
    }

    @Test
    fun `generated bindings drive all five exported top row anchors`() {
        assertEquals("Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->q(Lowd;J)Z", GboardVersionBindings.softKeyBind.reference)
        assertEquals("Lpbl;->B(Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;FFJI)V", GboardVersionBindings.pointerOwner.reference)
        assertEquals("Lpbl;->s(J)V", GboardVersionBindings.pointerCancel.reference)
        assertEquals("Lpbl;->C()V", GboardVersionBindings.pointerReset.reference)
        assertEquals("Lpbj;->f(Lpbl;Loth;Loud;Lowd;JZZIZJI)V", GboardVersionBindings.gestureDispatch.reference)
    }

    @Test
    fun `target pbl owner fixture proves injected register shape after ac`() {
        val targetLikeFixture = """
            .locals 14
            move/from16 v0, p2
            move/from16 v1, p3
            move-wide/from16 v11, p4
            iget v2, p0, Lpbl;->a:I
            iget-object v3, p0, Lpbl;->m:Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;
            move/from16 v13, p6
            invoke-virtual {p0, v11, v12, v13}, Lpbl;->r(JI)V
            invoke-direct {p0}, Lpbl;->ac()V
        """.trimIndent()

        assertTrue(targetLikeFixture.contains("move/from16 v0, p2"))
        assertTrue(targetLikeFixture.contains("move/from16 v1, p3"))
        assertTrue(targetLikeFixture.contains("iget-object v3, p0, Lpbl;->m:"))
        assertTrue(targetLikeFixture.indexOf("Lpbl;->r(JI)V") < targetLikeFixture.indexOf("Lpbl;->ac()V"))
        assertTrue(
            TOP_ROW_SWIPE_POINTER_DELEGATE.contains(
                "invoke-static {p0, p1, v3, v0, v1}, $TOP_ROW_SWIPE_RUNTIME_CLASS->maybeArmAndResolveTopRowOwner"
            )
        )
    }

    @Test
    fun `shared gesture dispatch helper is installed through semantic target`() {
        val source = readSource(
            "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/" +
                "zhuyintraditionalsimplifiedtoggle/GboardZhuyinTraditionalSimplifiedToggleRuntimePatch.kt"
        )
        val helperStart = source.indexOf(
            "internal fun BytecodePatchContext.installZhuyinToggleGestureDispatchHelpers"
        )
        val helperEnd = source.indexOf(
            "context(context: BytecodePatchContext)\nprivate fun installGestureDispatchHelpers",
            helperStart
        )
        assertTrue(helperStart >= 0 && helperEnd > helperStart)
        val helper = source.substring(helperStart, helperEnd)

        assertTrue(helper.contains("GboardVersionBindings.gestureDispatch.installHelper("))
        assertFalse(helper.contains("GboardVersionBindings.gestureDispatch.classType"))
        assertFalse(helper.contains("GboardVersionBindings.gestureDispatch.parameterTypes"))
        assertFalse(helper.contains("GboardVersionBindings.gestureDispatch.returnType"))
        assertTrue(helper.contains("Lpbj;->o:Lpbh;"))
        assertTrue(helper.contains("Lpbh;->o(Lpbl;Loth;Loud;Lowd;JZZIZJI)V"))
        assertFalse(helper.contains("Lofi;"))
        assertFalse(helper.contains("Lofk;Lnxi;Lnyf;Loaa;"))
    }

    private fun readSource(path: String): String =
        Files.readString(Path.of(path)).replace("\r\n", "\n")
}
