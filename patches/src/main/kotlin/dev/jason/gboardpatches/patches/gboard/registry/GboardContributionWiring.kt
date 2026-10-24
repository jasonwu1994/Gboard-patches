package dev.jason.gboardpatches.patches.gboard.registry

import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.ResourcePatchBuilder
import dev.jason.gboardpatches.patches.gboard.features.addsymbols.gboardZhuyinCustomSymbolsCorpusPatch
import dev.jason.gboardpatches.patches.gboard.features.addsymbols.gboardZhuyinCustomSymbolsEmoticonStatePatch
import dev.jason.gboardpatches.patches.gboard.features.addsymbols.gboardZhuyinCustomSymbolsEntryPatch
import dev.jason.gboardpatches.patches.gboard.features.addsymbols.gboardZhuyinCustomSymbolsHistoryPatch
import dev.jason.gboardpatches.patches.gboard.features.addsymbols.gboardZhuyinCustomSymbolsRecyclerPatch
import dev.jason.gboardpatches.patches.gboard.features.addsymbols.gboardZhuyinCustomSymbolsRoutingPatch
import dev.jason.gboardpatches.patches.gboard.features.advancedvoice.gboardAdvancedVoice1803ZhTwPatch
import dev.jason.gboardpatches.patches.gboard.features.advancedvoice.gboardAdvancedVoiceFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.englishqwerty.gboardEnglishQwertyPointerPatch
import dev.jason.gboardpatches.patches.gboard.features.englishqwerty.gboardEnglishQwertySoftKeyPatch
import dev.jason.gboardpatches.patches.gboard.features.flowmode.gboardFlowModeFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.flowmode.gboardFlowModeKeyEventPatch
import dev.jason.gboardpatches.patches.gboard.features.longpressquickactions.gboardLongPressQuickActionsInputEventPatch
import dev.jason.gboardpatches.patches.gboard.features.longpressquickactions.gboardLongPressQuickActionsGesturePatch
import dev.jason.gboardpatches.patches.gboard.features.longpressquickactions.gboardLongPressQuickActionsPointerOwnerPatch
import dev.jason.gboardpatches.patches.gboard.features.longpressquickactions.gboardLongPressQuickActionsSoftKeyPatch
import dev.jason.gboardpatches.patches.gboard.features.ocr.gboardOcrFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.ocr.gboardOcrRuntimePatch
import dev.jason.gboardpatches.patches.gboard.features.rambler.gboardRambler1803OfficialSelectorPatch
import dev.jason.gboardpatches.patches.gboard.features.roundedkeyboard.gboardRoundedKeyboardBytecodePatch
import dev.jason.gboardpatches.patches.gboard.features.spacebarlogo.gboardSpacebarLogoSoftKeyPatch
import dev.jason.gboardpatches.patches.gboard.features.toprowswipe.gboardTopRowSwipeGesturePatch
import dev.jason.gboardpatches.patches.gboard.features.toprowswipe.gboardTopRowSwipePointerPatch
import dev.jason.gboardpatches.patches.gboard.features.toprowswipe.gboardTopRowSwipeSoftKeyPatch
import dev.jason.gboardpatches.patches.gboard.features.zhuyinslide.gboardZhuyinSlidePointerAnchorPatch
import dev.jason.gboardpatches.patches.gboard.features.zhuyinslide.gboardZhuyinSlideSoftKeyPatch
import dev.jason.gboardpatches.patches.gboard.features.zhuyintraditionalsimplifiedtoggle.gboardZhuyinTraditionalSimplifiedToggleGesturePatch
import dev.jason.gboardpatches.patches.gboard.features.zhuyintraditionalsimplifiedtoggle.gboardZhuyinTraditionalSimplifiedTogglePopupPatch
import dev.jason.gboardpatches.patches.gboard.features.zhuyintraditionalsimplifiedtoggle.gboardZhuyinTraditionalSimplifiedToggleSoftKeyPatch
import dev.jason.gboardpatches.patches.gboard.features.zhuyintraditionalsimplifiedtoggle.gboardZhuyinSimplifiedCandidateButtonGesturePatch
import dev.jason.gboardpatches.patches.gboard.features.zhuyintraditionalsimplifiedtoggle.gboardZhuyinSimplifiedCandidateButtonSoftKeyPatch

internal class GboardContributionWiringUnit(
    val contributionIds: Set<String>,
    private val dependencySupplier: () -> Array<Patch<*>>,
) {
    fun dependencies(): Array<Patch<*>> = dependencySupplier()
}

internal object GboardContributionWiring {
    private val unitsByFeature = mapOf(
        "advanced_voice_typing" to listOf(
            unit("advanced_voice_typing.flag") {
                arrayOf(gboardAdvancedVoiceFlagValuePatch)
            },
            unit("advanced_voice_typing.bytecode") {
                arrayOf(
                    gboardAdvancedVoice1803ZhTwPatch,
                    gboardRambler1803OfficialSelectorPatch,
                )
            },
        ),
        "custom_symbols" to listOf(
            unit("custom_symbols.soft_key") {
                arrayOf(gboardZhuyinCustomSymbolsEntryPatch)
            },
            unit("custom_symbols.bytecode") {
                arrayOf(
                    gboardZhuyinCustomSymbolsCorpusPatch,
                    gboardZhuyinCustomSymbolsRoutingPatch,
                    gboardZhuyinCustomSymbolsEmoticonStatePatch,
                    gboardZhuyinCustomSymbolsHistoryPatch,
                    gboardZhuyinCustomSymbolsRecyclerPatch,
                )
            },
        ),
        "enable_ocr_scan_text" to listOf(
            unit("enable_ocr_scan_text.flag") {
                arrayOf(gboardOcrFlagValuePatch)
            },
            unit("enable_ocr_scan_text.bytecode") {
                arrayOf(gboardOcrRuntimePatch)
            },
        ),
        "english_qwerty_up_flick_uppercase" to listOf(
            unit("english_qwerty_up_flick_uppercase.soft_key") {
                arrayOf(gboardEnglishQwertySoftKeyPatch)
            },
            unit("english_qwerty_up_flick_uppercase.pointer") {
                arrayOf(gboardEnglishQwertyPointerPatch)
            },
        ),
        "flow_mode_animation" to listOf(
            unit("flow_mode_animation.flag") {
                arrayOf(gboardFlowModeFlagValuePatch)
            },
            unit("flow_mode_animation.key_event") {
                arrayOf(gboardFlowModeKeyEventPatch)
            },
        ),
        "g_logo_on_spacebar" to listOf(
            unit(
                "g_logo_on_spacebar.before_bind",
                "g_logo_on_spacebar.after_bind",
            ) {
                arrayOf(gboardSpacebarLogoSoftKeyPatch)
            },
        ),
        "long_press_editing_shortcuts" to listOf(
            unit("long_press_editing_shortcuts.soft_key") {
                arrayOf(gboardLongPressQuickActionsSoftKeyPatch)
            },
            unit("long_press_editing_shortcuts.input_event") {
                arrayOf(gboardLongPressQuickActionsInputEventPatch)
            },
            unit("long_press_editing_shortcuts.pointer") {
                arrayOf(gboardLongPressQuickActionsPointerOwnerPatch)
            },
            unit("long_press_editing_shortcuts.gesture") {
                arrayOf(gboardLongPressQuickActionsGesturePatch)
            },
        ),
        "rounded_keyboard_panel" to listOf(
            unit(
                "rounded_keyboard_panel.admission",
                "rounded_keyboard_panel.style",
            ) {
                arrayOf(gboardRoundedKeyboardBytecodePatch)
            },
        ),
        "swipeable_custom_top_row" to listOf(
            unit(
                "swipeable_custom_top_row.soft_key",
                "swipeable_custom_top_row.soft_key_after",
            ) {
                arrayOf(gboardTopRowSwipeSoftKeyPatch)
            },
            unit("swipeable_custom_top_row.pointer") {
                arrayOf(gboardTopRowSwipePointerPatch)
            },
            unit("swipeable_custom_top_row.gesture") {
                arrayOf(gboardTopRowSwipeGesturePatch)
            },
        ),
        "zhuyin_quick_traditional_simplified_toggle" to listOf(
            unit("zhuyin_quick_traditional_simplified_toggle.gesture") {
                arrayOf(gboardZhuyinTraditionalSimplifiedToggleGesturePatch)
            },
            unit(
                "zhuyin_quick_traditional_simplified_toggle.soft_key",
                "zhuyin_quick_traditional_simplified_toggle.soft_key_after",
            ) {
                arrayOf(gboardZhuyinTraditionalSimplifiedToggleSoftKeyPatch)
            },
            unit("zhuyin_quick_traditional_simplified_toggle.popup") {
                arrayOf(gboardZhuyinTraditionalSimplifiedTogglePopupPatch)
            },
        ),
        "zhuyin_simplified_candidate_button" to listOf(
            unit("zhuyin_simplified_candidate_button.gesture") {
                arrayOf(gboardZhuyinSimplifiedCandidateButtonGesturePatch)
            },
            unit(
                "zhuyin_simplified_candidate_button.soft_key",
                "zhuyin_simplified_candidate_button.soft_key_after",
            ) {
                arrayOf(gboardZhuyinSimplifiedCandidateButtonSoftKeyPatch)
            },
        ),
        "zhuyin_slide_input" to listOf(
            unit("zhuyin_slide_input.soft_key") {
                arrayOf(gboardZhuyinSlideSoftKeyPatch)
            },
            unit("zhuyin_slide_input.pointer") {
                arrayOf(gboardZhuyinSlidePointerAnchorPatch)
            },
        ),
    )

    val registeredContributionIdsByFeature: Map<String, Set<String>> =
        unitsByFeature.mapValues { (_, units) ->
            units.flatMapTo(linkedSetOf()) { unit -> unit.contributionIds }
        }

    fun activeContributionIds(featureId: String, admitted: Set<String>): Set<String> =
        activeUnits(featureId, admitted).flatMapTo(linkedSetOf()) { unit -> unit.contributionIds }

    fun wireAdmitted(
        featureId: String,
        admitted: Set<String>,
        builder: ResourcePatchBuilder,
    ) {
        activeUnits(featureId, admitted).forEach { unit ->
            builder.dependsOn(*unit.dependencies())
        }
    }

    private fun activeUnits(
        featureId: String,
        admitted: Set<String>,
    ): List<GboardContributionWiringUnit> {
        val units = unitsByFeature[featureId] ?: return emptyList()
        val registeredIds = units.flatMapTo(linkedSetOf()) { unit -> unit.contributionIds }
        check(admitted.all { contributionId -> contributionId in registeredIds }) {
            "$featureId admits unregistered contribution IDs ${(admitted - registeredIds).sorted()}"
        }
        units.forEach { unit ->
            val admittedUnitIds = unit.contributionIds.intersect(admitted)
            check(admittedUnitIds.isEmpty() || admittedUnitIds == unit.contributionIds) {
                "$featureId partially admits indivisible execution unit " +
                    "${unit.contributionIds.sorted()}: ${admittedUnitIds.sorted()}"
            }
        }
        return units.filter { unit -> unit.contributionIds.all(admitted::contains) }
    }

    private fun unit(
        vararg contributionIds: String,
        dependencySupplier: () -> Array<Patch<*>>,
    ) = GboardContributionWiringUnit(contributionIds.toSet(), dependencySupplier)
}
