package dev.jason.gboardpatches.patches.gboard.features.rambler

import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardRamblerOfficialSelectorWrapperTest {
    @Test
    fun `role 3 writes official selection before invoking stock aE so listeners see updated flag`() {
        val reference =
            "Lcom/google/android/apps/inputmethod/latin/preference/VoiceSettingsFragment;->" +
                "morpheRamblerStockaE(ZLqvt;" +
                "Lcom/google/android/libraries/inputmethod/preferencewidgets/CustomSelectorWithWidgetPreference;" +
                "Lcom/google/android/libraries/inputmethod/preferencewidgets/CustomSelectorWithWidgetPreference;)V"

        val body = rambler1853WrapperBody(
            reference = reference,
            static = true,
            words = 4,
            returnType = "V",
            role = 3,
        )

        assertEquals(
            listOf(
                RuntimeCallEmitter.invoke(RuntimeCallId.RAMBLER_RUNTIME_UPDATE_OFFICIAL_SELECTION, "p0"),
                "invoke-static/range {p0 .. p3}, $reference",
                "return-void",
            ),
            body.lines(),
        )
    }

    @Test
    fun `role 4 reconciles passive nca#a reads through observeOfficialSelectionRead and returns its result`() {
        val reference = "Lnca;->morpheRamblerStocka(Landroid/content/Context;)Z"

        val body = rambler1853WrapperBody(
            reference = reference,
            static = true,
            words = 1,
            returnType = "Z",
            role = 4,
        )

        assertEquals(
            listOf(
                "invoke-static/range {p0 .. p0}, $reference",
                "move-result v0",
                RuntimeCallEmitter.invoke(
                    RuntimeCallId.RAMBLER_RUNTIME_OBSERVE_OFFICIAL_SELECTION_READ,
                    "p0, v0",
                ),
                "move-result v0",
                "return v0",
            ),
            body.lines(),
        )
        assertFalse(body.contains("updateOfficialSelection"))
    }

    @Test
    fun `voice settings roles 0 1 2 wrap stock calls in exception-safe voice settings scope`() {
        val role0 = rambler1853WrapperBody(
            reference = "Lcom/google/android/apps/inputmethod/latin/preference/VoiceSettingsFragment;->morpheRamblerStockaC()I",
            static = false,
            words = 1,
            returnType = "I",
            role = 0,
        )
        assertEquals(
            listOf(
                RuntimeCallEmitter.invoke(RuntimeCallId.RAMBLER_RUNTIME_ENTER_VOICE_SETTINGS_SCOPE, ""),
                ":start",
                "invoke-direct/range {p0 .. p0}, Lcom/google/android/apps/inputmethod/latin/preference/VoiceSettingsFragment;->morpheRamblerStockaC()I",
                "move-result v0",
                ":end",
                RuntimeCallEmitter.invoke(RuntimeCallId.RAMBLER_RUNTIME_EXIT_VOICE_SETTINGS_SCOPE, ""),
                "return v0",
                ".catch Ljava/lang/Throwable; {:start .. :end} :failed",
                ":failed",
                "move-exception v1",
                RuntimeCallEmitter.invoke(RuntimeCallId.RAMBLER_RUNTIME_EXIT_VOICE_SETTINGS_SCOPE, ""),
                "throw v1",
            ),
            role0.lines(),
        )

        val role1 = rambler1853WrapperBody(
            reference = "Lcom/google/android/apps/inputmethod/latin/preference/VoiceSettingsFragment;->morpheRamblerStockac()V",
            static = false,
            words = 1,
            returnType = "V",
            role = 1,
        )
        assertTrue(role1.lines().none { it.isBlank() })
        assertTrue(role1.contains("return-void"))

        val role2 = rambler1853WrapperBody(
            reference = "Lcom/google/android/apps/inputmethod/latin/preference/VoiceSettingsFragment;->morpheRamblerStockf(Landroid/os/Bundle;)V",
            static = false,
            words = 2,
            returnType = "V",
            role = 2,
        )
        assertTrue(role2.contains("invoke-direct/range {p0 .. p1},"))
        assertTrue(role2.lines().none { it.isBlank() })
    }

    @Test
    fun `role 5 wraps default selection check in exception-safe suppression scope`() {
        val role5 = rambler1853WrapperBody(
            reference = "Lfhv;->morpheRamblerStockhU()V",
            static = false,
            words = 1,
            returnType = "V",
            role = 5,
        )
        assertEquals(
            listOf(
                RuntimeCallEmitter.invoke(RuntimeCallId.RAMBLER_RUNTIME_ENTER_DEFAULT_SELECTION_SUPPRESSION, ""),
                ":start",
                "invoke-direct/range {p0 .. p0}, Lfhv;->morpheRamblerStockhU()V",
                ":end",
                RuntimeCallEmitter.invoke(RuntimeCallId.RAMBLER_RUNTIME_EXIT_DEFAULT_SELECTION_SUPPRESSION, ""),
                "return-void",
                ".catch Ljava/lang/Throwable; {:start .. :end} :failed",
                ":failed",
                "move-exception v1",
                RuntimeCallEmitter.invoke(RuntimeCallId.RAMBLER_RUNTIME_EXIT_DEFAULT_SELECTION_SUPPRESSION, ""),
                "throw v1",
            ),
            role5.lines(),
        )
    }
}
