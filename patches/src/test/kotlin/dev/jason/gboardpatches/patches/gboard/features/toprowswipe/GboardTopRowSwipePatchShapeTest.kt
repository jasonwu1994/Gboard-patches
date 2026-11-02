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
    fun `softkey contribution delegates both phases to the family composer`() {
        assertTrue(TOP_ROW_SWIPE_ENTRY_DELEGATE.contains("patchIncomingSoftKeyMetadata"))
        assertFalse(TOP_ROW_SWIPE_ENTRY_DELEGATE.contains("afterSoftKeyBound"))
        assertTrue(TOP_ROW_SWIPE_ENTRY_DELEGATE.contains(TOP_ROW_SWIPE_RUNTIME_CLASS))

        val source = readSource(
            "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/toprowswipe/" +
                "GboardTopRowSwipeSoftKeyPatch.kt"
        )
        assertTrue(source.contains("GboardSoftKeyFamilyFeature.TOP_ROW_SWIPE"))
        assertTrue(source.contains("gboardSoftKeyFamilyFeaturePatch"))
        assertFalse(source.contains("addInstructions"))
    }

    @Test
    fun `target r fixture keeps result register intact at return tracking site`() {
        val targetLikeFixture = """
            .method public final s(Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;J)Z
                .locals 3
                iput-object p1, p0, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->e:Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;
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
    fun `pointer delegates and gesture feature token stay on the agreed runtime contract`() {
        assertTrue(TOP_ROW_SWIPE_POINTER_DELEGATE.contains("maybeArmAndResolveTopRowOwner"))
        assertTrue(TOP_ROW_SWIPE_FINISH_SESSION_DELEGATE.contains("finishSwipeSession"))
        assertTrue(TOP_ROW_SWIPE_CLEAR_SESSION_DELEGATE.contains("clearSwipeSession"))
        assertTrue(TOP_ROW_SWIPE_POINTER_DELEGATE.contains(TOP_ROW_SWIPE_RUNTIME_CLASS))
        val source = readSource(
            "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/toprowswipe/" +
                "GboardTopRowSwipeGesturePatch.kt"
        )
        assertTrue(source.contains("GboardGestureFamilyFeature.TOP_ROW_SWIPE"))
        assertTrue(source.contains("gboardGestureFamilyFeaturePatch"))
        assertFalse(source.contains("RuntimeCallId"))
        assertFalse(source.contains("addInstructions"))
    }

    @Test
    fun `generated bindings drive all five exported top row anchors`() {
        assertEquals("Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->s(Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;J)Z", GboardVersionBindings.softKeyBind.reference)
        assertEquals("Lqin;->B(Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;FFJI)V", GboardVersionBindings.pointerOwner.reference)
        assertEquals("Lqin;->s(J)V", GboardVersionBindings.pointerCancel.reference)
        assertEquals("Lqin;->C()V", GboardVersionBindings.pointerReset.reference)
        assertEquals("Lqil;->f(Lqin;Lqae;Lqbc;Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;JZZIZJI)V", GboardVersionBindings.gestureDispatch.reference)
    }

    @Test
    fun `target qin owner fixture proves injected register shape after ac`() {
        val targetLikeFixture = """
            .locals 14
            move/from16 v0, p2
            move/from16 v1, p3
            move-wide/from16 v11, p4
            iget v2, p0, Lqin;->a:I
            iget-object v3, p0, Lqin;->m:Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;
            move/from16 v13, p6
            invoke-virtual {p0, v11, v12, v13}, Lqin;->r(JI)V
            invoke-direct {p0}, Lqin;->ac()V
        """.trimIndent()

        assertTrue(targetLikeFixture.contains("move/from16 v0, p2"))
        assertTrue(targetLikeFixture.contains("move/from16 v1, p3"))
        assertTrue(targetLikeFixture.contains("iget-object v3, p0, Lqin;->m:"))
        assertTrue(targetLikeFixture.indexOf("Lqin;->r(JI)V") < targetLikeFixture.indexOf("Lqin;->ac()V"))
        assertTrue(
            TOP_ROW_SWIPE_POINTER_DELEGATE.contains(
                "invoke-static {p0, p1, v3, v0, v1}, $TOP_ROW_SWIPE_RUNTIME_CLASS->maybeArmAndResolveTopRowOwner"
            )
        )
    }

    @Test
    fun `gesture family composer owns helper topology and generated stock bindings`() {
        val source = readSource(
            "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/shared/" +
                "GboardGestureFamilyComposer.kt"
        )
        assertTrue(source.contains("GboardVersionBindings.gestureStockDelegateField"))
        assertTrue(source.contains("GboardVersionBindings.gestureStockDispatch"))
        assertTrue(source.contains("jasondevDispatchWithTopRow"))
        assertTrue(source.contains("jasondevDispatchOrToggle"))
        assertFalse(source.contains("Lpbj;"))
        assertFalse(source.contains("Lpbh;"))
        assertFalse(source.contains("p13"))
    }

    private fun readSource(path: String): String =
        Files.readString(Path.of(path)).replace("\r\n", "\n")
}
