package dev.jason.gboardpatches.patches.gboard.features.zhuyintraditionalsimplifiedtoggle

import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardZhuyinTraditionalSimplifiedTogglePatchShapeTest {
    @Test
    fun `softkey gesture and popup use exact generated 1853 descriptors`() {
        assertEquals(
            "Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->" +
                "s(Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;J)Z",
            GboardVersionBindings.softKeyBind.reference,
        )
        assertEquals(
            "Lqil;->f(Lqin;Lqae;Lqbc;" +
                "Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;JZZIZJI)V",
            GboardVersionBindings.gestureDispatch.reference,
        )
        assertEquals("Lqil;->n:Lqij;", GboardVersionBindings.gestureStockDelegateField.reference)
        assertEquals(
            "Lqij;->o(Lqin;Lqae;Lqbc;" +
                "Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;JZZIZJI)V",
            GboardVersionBindings.gestureStockDispatch.reference,
        )
        assertEquals(
            "Lcom/google/android/libraries/inputmethod/popup/BasicPopupView;->" +
                "b(Lcom/google/android/libraries/inputmethod/widgets/SoftKeyboardView;" +
                "Landroid/view/View;FFLcom/google/android/libraries/inputmethod/metadata/" +
                "ActionDef;[IZ)Lqbc;",
            GboardVersionBindings.zhuyinTogglePopupAction.reference,
        )
    }

    @Test
    fun `softkey delegates copy incoming metadata and track the bound view`() {
        val source = readSource(
            "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/" +
                "zhuyintraditionalsimplifiedtoggle/" +
                "GboardZhuyinTraditionalSimplifiedToggleSoftKeyPatch.kt"
        )

        assertTrue(source.contains("GboardSoftKeyFamilyFeature.ZHUYIN_TOGGLE"))
        assertTrue(source.contains("gboardSoftKeyFamilyFeaturePatch"))
        assertFalse(source.contains("addInstructions"))
        assertTrue(ZHUYIN_TOGGLE_PATCH_INCOMING_METADATA_DELEGATE.contains("move-result-object p1"))
        assertTrue(ZHUYIN_TOGGLE_PATCH_INCOMING_METADATA_DELEGATE.contains(
            "check-cast p1, Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;"
        ))
    }

    @Test
    fun `dedicated popup patch owns no gesture helper behavior`() {
        val source = readRuntimePatchSource()

        assertTrue(source.contains("gboardZhuyinTraditionalSimplifiedTogglePopupPatch"))
        assertTrue(source.contains("applyZhuyinTogglePopupActionPatch"))
        assertTrue(source.contains("MutableClass(ownerClass)"))
        assertFalse(source.contains("ZHUYIN_TRADITIONAL_SIMPLIFIED_TOGGLE_RUNTIME_MAYBE_TOGGLE"))
        assertFalse(source.contains("jasondevDispatchOrToggle"))
        assertFalse(source.contains("Lqij;->o("))
    }

    @Test
    fun `popup patches exact 1853 action before stock and has no old z path`() {
        val source = readRuntimePatchSource()

        assertTrue(source.contains("GboardVersionBindings.zhuyinTogglePopupAction"))
        assertTrue(source.contains("ZHUYIN_TRADITIONAL_SIMPLIFIED_TOGGLE_RUNTIME_PATCH_POPUP_ACTION"))
        assertTrue(source.contains("PopupRegisterLayout.fromBinding()"))
        assertFalse(source.contains("move-result-object p5"))
        assertFalse(source.contains("check-cast p5, Lotk;"))
        assertTrue(source.contains("dependsOn(gboardPatchesExtensionCarrierPatch)"))
        assertFalse(source.contains("SoftKeyboardView;->z"))
        assertFalse(source.contains("name = \"z\""))
        assertFalse(source.contains("jasondevPatchTogglePopupMetadata"))
    }

    @Test
    fun `toggle production sources contain no legacy 17010 descriptors or ids`() {
        val combined = readSource(
            "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/" +
                "zhuyintraditionalsimplifiedtoggle/" +
                "GboardZhuyinTraditionalSimplifiedToggleSoftKeyPatch.kt"
        ) + readRuntimePatchSource() + readSource(
            "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/toprowswipe/" +
                "GboardTopRowSwipeGesturePatch.kt"
        )

        listOf(
            "Loaa;", "Loql;", "Lnzv;", "Lnxj;", "Lnxi;", "Lnye;", "Lnxl;", "Lnyf;",
            "0x7f0b23fd", "0x7f140969", "Lofi;", "Lofk;"
        ).forEach { stale ->
            assertFalse("Found stale toggle token $stale", combined.contains(stale))
        }
        val legacyOwnerIdentifier = Regex("""\b[A-Za-z_][A-Za-z0-9_]*\b""")
            .findAll(combined)
            .map { match -> match.value }
            .firstOrNull { identifier -> identifier.contains("ofi", ignoreCase = true) }
        assertTrue(
            "Found active legacy owner identifier $legacyOwnerIdentifier",
            legacyOwnerIdentifier == null
        )
    }

    @Test
    fun `public patch keeps slide dependency with independent toggle contributions`() {
        val registry = readSource(
            "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/registry/" +
                "GboardPatchRegistry.kt"
        )
        val wiring = readSource(
            "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/registry/" +
                "GboardContributionWiring.kt",
        )
        val patchStart = registry.indexOf("val gboardZhuyinQuickTraditionalSimplifiedTogglePatch")
        val patchEnd = registry.indexOf("val gboardCustomSymbolsPatch", patchStart)
        assertTrue(patchStart >= 0 && patchEnd > patchStart)
        val block = registry.substring(patchStart, patchEnd)

        assertTrue(block.contains("gboardZhuyinSlideInputPatch"))
        assertTrue(wiring.contains("gboardZhuyinTraditionalSimplifiedToggleGesturePatch"))
        assertTrue(wiring.contains("gboardZhuyinTraditionalSimplifiedToggleSoftKeyPatch"))
        assertTrue(wiring.contains("gboardZhuyinTraditionalSimplifiedTogglePopupPatch"))
        assertFalse(block.contains("gboardTopRowSwipeGesturePatch"))
    }

    private fun readRuntimePatchSource(): String = readSource(
        "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/" +
            "zhuyintraditionalsimplifiedtoggle/" +
            "GboardZhuyinTraditionalSimplifiedToggleRuntimePatch.kt"
    )

    private fun readSource(path: String): String =
        Files.readString(Path.of(path)).replace("\r\n", "\n")
}
