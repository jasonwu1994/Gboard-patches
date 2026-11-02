package dev.jason.gboardpatches.patches.gboard.features.englishqwerty

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardEnglishQwertyPatchSourceTest {
    @Test
    fun softKeyPatchUses1853ActionModelAndOnlyTheTargetEnglishKeyIds() {
        val source = readSoftKeySource()

        assertTrue(source.contains("SoftKeyDef;->f:I"))
        assertTrue(source.contains("SoftKeyDef;->i:Lwns;"))
        assertTrue(source.contains("SoftKeyDef;->g(Lqae;)Lcom/google/android/libraries/inputmethod/metadata/ActionDef;"))
        assertTrue(source.contains("ActionDef;->f:Lwns;"))
        assertTrue(source.contains("Lqbc;->e:Ljava/lang/Object;"))
        assertTrue(source.contains("Lqaf;->a:Lqae;"))
        assertTrue(source.contains("Lqaf;->t([Ljava/lang/String;)V"))
        assertTrue(source.contains("Lqaf;->s(ILqbb;Ljava/lang/Object;)V"))
        assertTrue(source.contains("Lqaf;->c()Lcom/google/android/libraries/inputmethod/metadata/ActionDef;"))
        assertTrue(source.contains("Lqcv;->k(Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;)V"))
        assertTrue(source.contains("Lqcv;->t(Lcom/google/android/libraries/inputmethod/metadata/ActionDef;)V"))
        assertTrue(source.contains("Lqcv;->b()Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;"))

        val admittedIds = Regex("""(?m)^\s*(0x[0-9a-f]+) -> :sswitch_0$""")
            .findAll(source)
            .map { match -> match.groupValues[1] }
            .toSet()
        assertEquals(TARGET_ENGLISH_QWERTY_KEY_IDS, admittedIds)
        assertFalse(admittedIds.contains("0x7f0b19aa"))
        assertFalse(admittedIds.contains("0x7f0b1899"))

        listOf("Loaa;", "Lnzv;", "Lnxi;", "Lnxj;", "Lnxl;", "Lnye;")
            .forEach { legacyDescriptor -> assertFalse(source.contains(legacyDescriptor)) }
        assertFalse(source.contains("0x7f0b0607"))
    }

    @Test
    fun softKeyPatchPreservesDisabledAndExistingNativeSlideUpMetadata() {
        val source = readSoftKeySource()

        assertTrue(source.contains("ENGLISH_UPPERCASE_TOGGLE_RUNTIME_IS_ENABLED"))
        assertTrue(source.contains("ENGLISH_UPPERCASE_TOGGLE_RUNTIME_IS_PATCHED_METADATA"))
        assertTrue(source.contains("ENGLISH_UPPERCASE_TOGGLE_RUNTIME_GET_CACHED_PATCHED_METADATA"))
        assertTrue(source.contains("ENGLISH_UPPERCASE_TOGGLE_RUNTIME_CACHE_PATCHED_METADATA"))
        assertFalse(source.contains("->markPatchedMetadata(Ljava/lang/Object;)V"))
        assertTrue(source.contains("sget-object v0, Lqae;->c:Lqae;"))
        assertTrue(source.contains(
            "invoke-virtual {p1, v0}, Lcom/google/android/libraries/inputmethod/metadata/" +
                "SoftKeyDef;->g(Lqae;)Lcom/google/android/libraries/inputmethod/metadata/ActionDef;"
        ))
        assertFalse(source.contains("jasondevStripSlideUpAction"))
        assertFalse(source.contains("Ljava/util/EnumMap;->remove"))
    }

    @Test
    fun softKeyCacheRunsAfterEnabledAndNativeGuardsAndBeforeCloning() {
        val source = readSoftKeySource()
        val patchBody = tripleQuotedValue(source, "PATCH_INCOMING_METADATA_BODY")
        val enabledIndex = patchBody.indexOf("ENGLISH_UPPERCASE_TOGGLE_RUNTIME_IS_ENABLED")
        val nativeLookupIndex = patchBody.indexOf(
            "invoke-virtual {p1, v0}, Lcom/google/android/libraries/inputmethod/metadata/" +
                "SoftKeyDef;->g(Lqae;)Lcom/google/android/libraries/inputmethod/metadata/ActionDef;"
        )
        val cacheGetIndex = patchBody.indexOf("ENGLISH_UPPERCASE_TOGGLE_RUNTIME_GET_CACHED_PATCHED_METADATA")
        val builderIndex = patchBody.indexOf("new-instance v4, Lqcv;")
        val cachePutIndex = patchBody.indexOf("ENGLISH_UPPERCASE_TOGGLE_RUNTIME_CACHE_PATCHED_METADATA")

        assertTrue(enabledIndex >= 0)
        assertTrue(enabledIndex < nativeLookupIndex)
        assertTrue(nativeLookupIndex < cacheGetIndex)
        assertTrue(cacheGetIndex < builderIndex)
        assertTrue(builderIndex < cachePutIndex)
        assertTrue(patchBody.contains(
            "check-cast v0, Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;"
        ))
        assertTrue(patchBody.contains(":catch_0\n    return-object p1"))
    }

    @Test
    fun softKeyPatchOnlyBuildsAsciiCaseToggleWithoutEnglishHintOrSlideDown() {
        val source = readSoftKeySource()

        assertTrue(source.contains("jasondevToggleAsciiCase"))
        assertTrue(source.contains("sget-object v6, Lqae;->c:Lqae;"))
        assertFalse(source.contains("jasondevResolveEnglishSlideDown"))
        assertFalse(source.contains("jasondevSyncSyntheticEnglishHint"))
        assertFalse(source.contains("jasondevSyncEnglishHintView"))
        assertFalse(source.contains("Lqae;->d:Lqae;"))
        assertFalse(source.contains("slidedown_data"))
    }

    @Test
    fun softKeyPatchFailsClosedAndReturnsOriginalMetadataOnRuntimeError() {
        val source = readSoftKeySource()

        assertTrue(source.contains(".catch Ljava/lang/Throwable;"))
        assertTrue(source.contains(":catch_0"))
        assertTrue(source.contains(":cond_return_original"))
        assertTrue(source.contains("return-object p1"))
    }

    @Test
    fun softKeyContributionDelegatesOrderingToTheFamilyComposer() {
        val source = readSoftKeySource()

        assertTrue(source.contains("GboardSoftKeyFamilyFeature.ENGLISH_QWERTY"))
        assertTrue(source.contains("gboardSoftKeyFamilyFeaturePatch"))
        assertFalse(source.contains("topRowDelegateCallIndex"))
        assertFalse(source.contains("mutableMethod.addInstructions"))
    }

    private fun readSoftKeySource(): String =
        readSource(
            "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/englishqwerty/" +
                "GboardEnglishQwertySoftKeyPatch.kt"
        )

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(Path.of(relativePath)), StandardCharsets.UTF_8).replace("\r\n", "\n")

    private fun tripleQuotedValue(source: String, name: String): String =
        Regex(
            "(?:private|internal) val $name = " + "\"\"\"" + "(.*?)" + "\"\"\"",
            setOf(RegexOption.DOT_MATCHES_ALL)
        ).find(source)?.groupValues?.get(1) ?: error("$name body not found")

    private companion object {
        val TARGET_ENGLISH_QWERTY_KEY_IDS = setOf(
            "0x7f0b198e", "0x7f0b1877", "0x7f0b19fe", "0x7f0b18a5",
            "0x7f0b1905", "0x7f0b1813", "0x7f0b1992", "0x7f0b1878",
            "0x7f0b19c5", "0x7f0b1886", "0x7f0b1a06", "0x7f0b18a9",
            "0x7f0b19e6", "0x7f0b1890", "0x7f0b1934", "0x7f0b1838",
            "0x7f0b1971", "0x7f0b1860", "0x7f0b1987", "0x7f0b1875",
            "0x7f0b18d4", "0x7f0b17f7", "0x7f0b199d", "0x7f0b187d",
            "0x7f0b18f7", "0x7f0b180f", "0x7f0b191b", "0x7f0b1826",
            "0x7f0b191f", "0x7f0b1828", "0x7f0b192e", "0x7f0b1833",
            "0x7f0b1945", "0x7f0b1849", "0x7f0b1949", "0x7f0b184b",
            "0x7f0b1951", "0x7f0b184f", "0x7f0b1a0d", "0x7f0b18ad",
            "0x7f0b1a03", "0x7f0b18a8", "0x7f0b18e9", "0x7f0b1808",
            "0x7f0b19fb", "0x7f0b18a3", "0x7f0b18e4", "0x7f0b1806",
            "0x7f0b1965", "0x7f0b1857", "0x7f0b195e", "0x7f0b1853"
        )
    }
}
