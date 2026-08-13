package dev.jason.gboardpatches.patches.gboard.registry

import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.resourcePatch
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import dev.jason.gboardpatches.patches.gboard.features.advancedvoice.gboardAdvancedVoiceFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.advancedvoice.gboardAdvancedVoiceAsrSessionPatch
import dev.jason.gboardpatches.patches.gboard.features.advancedvoice.gboardAdvancedVoiceFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.advancedvoice.gboardAdvancedVoiceFormatterPatch
import dev.jason.gboardpatches.patches.gboard.features.advancedvoice.gboardAdvancedVoiceInitialSettingsPatch
import dev.jason.gboardpatches.patches.gboard.features.advancedvoice.gboardAdvancedVoiceMddProvisioningPatch
import dev.jason.gboardpatches.patches.gboard.features.advancedvoice.gboardAdvancedVoiceNativeReadinessPatch
import dev.jason.gboardpatches.patches.gboard.features.advancedvoice.gboardAdvancedVoiceS3AirplanePatch
import dev.jason.gboardpatches.patches.gboard.features.bluetoothmicrophone.gboardBluetoothMicrophoneFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.bluetoothmicrophone.gboardBluetoothMicrophoneFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.about.gboardAboutPageResourcePatch
import dev.jason.gboardpatches.patches.gboard.features.addsymbols.gboardZhuyinCustomSymbolsCorpusPatch
import dev.jason.gboardpatches.patches.gboard.features.addsymbols.gboardZhuyinCustomSymbolsEmoticonStatePatch
import dev.jason.gboardpatches.patches.gboard.features.addsymbols.gboardZhuyinCustomSymbolsEntryPatch
import dev.jason.gboardpatches.patches.gboard.features.addsymbols.gboardCustomSymbolsFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.addsymbols.gboardZhuyinCustomSymbolsHistoryPatch
import dev.jason.gboardpatches.patches.gboard.features.addsymbols.gboardZhuyinCustomSymbolsRecyclerPatch
import dev.jason.gboardpatches.patches.gboard.features.addsymbols.gboardZhuyinCustomSymbolsRoutingPatch
import dev.jason.gboardpatches.patches.gboard.features.clipboard.gboardClipboardFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.clipboard.gboardClipboardAdapterTrimPatch
import dev.jason.gboardpatches.patches.gboard.features.clipboard.gboardClipboardColumnCountPatch
import dev.jason.gboardpatches.patches.gboard.features.clipboard.gboardClipboardItemBindPatch
import dev.jason.gboardpatches.patches.gboard.features.clipboard.gboardClipboardLoaderPatch
import dev.jason.gboardpatches.patches.gboard.features.clipboard.gboardClipboardPrunePatch
import dev.jason.gboardpatches.patches.gboard.features.clipboardcontentlimit.gboardClipboardContentLimitFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.clipboardcontentlimit.gboardClipboardContentLimitFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.developeroptions.gboardDeveloperOptionsFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.webclipboard.gboardWebClipboardAssetsPatch
import dev.jason.gboardpatches.patches.gboard.features.webclipboard.gboardWebClipboardCapturePatch
import dev.jason.gboardpatches.patches.gboard.features.webclipboard.gboardWebClipboardFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.webclipboard.gboardWebClipboardManifestPatch
import dev.jason.gboardpatches.patches.gboard.features.englishqwerty.gboardEnglishUppercaseToggleFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.englishqwerty.gboardEnglishQwertyPointerPatch
import dev.jason.gboardpatches.patches.gboard.features.englishqwerty.gboardEnglishQwertySoftKeyPatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.gboardDeviceIntelligenceFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.gboardFeatureFlagsBytecodePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.gboardGrammarCheckerFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.gboardInlineSuggestionsFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.gboardKeyShapeSelectionFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.latinglobe.gboardLatinGlobeKeyIgnoreIntervalBytecodePatch
import dev.jason.gboardpatches.patches.gboard.features.latinglobe.gboardLatinGlobeKeyIgnoreIntervalFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.longpressquickactions.gboardLongPressQuickActionsFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.longpressquickactions.gboardLongPressQuickActionsInputEventPatch
import dev.jason.gboardpatches.patches.gboard.features.longpressquickactions.gboardLongPressQuickActionsPointerOwnerPatch
import dev.jason.gboardpatches.patches.gboard.features.longpressquickactions.gboardLongPressQuickActionsSoftKeyPatch
import dev.jason.gboardpatches.patches.gboard.features.manualincognito.gboardManualIncognitoAccessPointPatch
import dev.jason.gboardpatches.patches.gboard.features.manualincognito.gboardManualIncognitoFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.manualincognito.gboardManualIncognitoLifecyclePatch
import dev.jason.gboardpatches.patches.gboard.features.manualincognito.gboardManualIncognitoPolicyPatch
import dev.jason.gboardpatches.patches.gboard.features.ocr.gboardOcrFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.ocr.gboardOcrFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.ocr.gboardOcrRuntimePatch
import dev.jason.gboardpatches.patches.gboard.features.packagerename.gboardPackageRenameResourcePatch
import dev.jason.gboardpatches.patches.gboard.features.settingshomepage.gboardSettingsHomepageBytecodePatch
import dev.jason.gboardpatches.patches.gboard.features.settingshomepage.gboardSettingsHomepageFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.signaturebypass.gboardSignatureBypassBytecodePatch
import dev.jason.gboardpatches.patches.gboard.features.symbolfooter.gboardSymbolFooterOrderBytecodePatch
import dev.jason.gboardpatches.patches.gboard.features.symbolfooter.gboardSymbolFooterOrderFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.toprowswipe.gboardTopRowSwipeFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.toprowswipe.gboardTopRowSwipeGesturePatch
import dev.jason.gboardpatches.patches.gboard.features.toprowswipe.gboardTopRowSwipePointerPatch
import dev.jason.gboardpatches.patches.gboard.features.toprowswipe.gboardTopRowSwipeSoftKeyPatch
import dev.jason.gboardpatches.patches.gboard.features.writingtools.gboardAiWritingToolsFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.writingtools.gboardAiWritingToolsBackendFactoryPatch
import dev.jason.gboardpatches.patches.gboard.features.writingtools.gboardAiWritingToolsFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.writingtools.gboardAiWritingToolsOfficialPreferencesPatch
import dev.jason.gboardpatches.patches.gboard.features.writingtools.gboardAiWritingToolsSettingsVisibilityPatch
import dev.jason.gboardpatches.patches.gboard.features.writingtools.gboardAiWritingToolsSignalPatch
import dev.jason.gboardpatches.patches.gboard.features.writingtools.gboardAiWritingToolsVoiceCommandPatch
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesSettingsPatch
import dev.jason.gboardpatches.patches.gboard.features.zhuyinbottomrow.gboardZhuyinBottomRowWeightFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.zhuyinbottomrow.gboardZhuyinBottomRowWeightSoftKeyPatch
import dev.jason.gboardpatches.patches.gboard.features.zhuyinslide.gboardZhuyinSlidePointerAnchorPatch
import dev.jason.gboardpatches.patches.gboard.features.zhuyinslide.gboardZhuyinSlideSoftKeyPatch
import dev.jason.gboardpatches.patches.gboard.features.zhuyintraditionalsimplifiedtoggle.gboardZhuyinTraditionalSimplifiedToggleRuntimePatch
import dev.jason.gboardpatches.patches.gboard.features.zhuyintraditionalsimplifiedtoggle.gboardZhuyinTraditionalSimplifiedToggleSoftKeyPatch
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

@Suppress("unused")
val gboardZhuyinSlideInputPatch = resourcePatch(
    name = "Zhuyin Slide Input",
    description = "注音鍵盤支持上下滑輸入\nEnable slide-up and slide-down input on the Zhuyin keyboard.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardAboutPageResourcePatch,
        gboardPatchesExtensionCarrierPatch,
        gboardZhuyinSlideSoftKeyPatch,
        gboardZhuyinSlidePointerAnchorPatch
    )
}

@Suppress("unused")
val gboardEnglishQwertySlideUppercaseTogglePatch = resourcePatch(
    name = "English QWERTY Up-Flick Uppercase",
    description = "英文 QWERTY 鍵盤上滑大小寫\nFlick up on the English QWERTY keyboard to toggle uppercase and lowercase.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardAboutPageResourcePatch,
        gboardPatchesSettingsPatch,
        gboardEnglishUppercaseToggleFeatureMarkerPatch,
        gboardEnglishQwertySoftKeyPatch,
        gboardEnglishQwertyPointerPatch
    )
}

@Suppress("unused")
val gboardLongPressQuickActionsPatch = resourcePatch(
    name = "Long-Press Editing Shortcuts",
    description = "在英文 QWERTY 與注音鍵盤加入全選、復原、複製、剪下、貼上與重做長按快捷鍵\n" +
        "Add Select all, Undo, Copy, Cut, Paste, and Redo long-press shortcuts " +
        "to English QWERTY and Zhuyin.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardLongPressQuickActionsFeatureMarkerPatch,
        gboardLongPressQuickActionsSoftKeyPatch,
        gboardLongPressQuickActionsInputEventPatch,
        gboardLongPressQuickActionsPointerOwnerPatch,
    )
}

@Suppress("unused")
val gboardManualIncognitoModePatch = resourcePatch(
    name = "Incognito Mode Toggle",
    description = "在 Access Point 工具列新增無痕模式切換按鈕，並可設定無痕模式下是否啟用剪貼簿與語音輸入\n" +
        "Add an Incognito toggle to the Access Point toolbar and configure clipboard and voice typing availability while Incognito mode is active.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardManualIncognitoFeatureMarkerPatch,
        gboardManualIncognitoLifecyclePatch,
        gboardManualIncognitoPolicyPatch,
        gboardManualIncognitoAccessPointPatch,
    )
}

@Suppress("unused")
val gboardAdvancedVoiceTypingPatch = resourcePatch(
    name = "Advanced Voice Typing",
    description = "啟用進階語音輸入（包含自動標點功能），並另外為不支援進階語音輸入的繁體中文語音啟用自動標點\n" +
        "Enable Advanced Voice Typing with automatic punctuation, and separately enable automatic punctuation for Traditional Chinese voice typing, which does not support Advanced Voice Typing.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardAdvancedVoiceFeatureMarkerPatch,
        gboardAdvancedVoiceAsrSessionPatch,
        gboardAdvancedVoiceFlagValuePatch,
        gboardAdvancedVoiceNativeReadinessPatch,
        gboardAdvancedVoiceInitialSettingsPatch,
        gboardAdvancedVoiceMddProvisioningPatch,
        gboardAdvancedVoiceFormatterPatch,
        gboardAdvancedVoiceS3AirplanePatch,
    )
}

@Suppress("unused")
val gboardBluetoothMicrophonePatch = resourcePatch(
    name = "Use Bluetooth Microphone",
    description = "啟用 語音輸入 -> 使用藍芽麥克風\n" +
        "Enable Voice typing -> Use Bluetooth microphone.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardBluetoothMicrophoneFeatureMarkerPatch,
        gboardBluetoothMicrophoneFlagValuePatch,
    )
}

@Suppress("unused")
val gboardZhuyinQuickTraditionalSimplifiedTogglePatch = resourcePatch(
    name = "Zhuyin Quick Traditional/Simplified Toggle",
    description = "注音 ㄥ 上滑快速切換繁簡\nSwipe up on Zhuyin ㄥ to quickly toggle Traditional and Simplified Chinese.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardZhuyinSlideInputPatch,
        gboardTopRowSwipeGesturePatch,
        gboardZhuyinTraditionalSimplifiedToggleSoftKeyPatch,
        gboardZhuyinTraditionalSimplifiedToggleRuntimePatch
    )
}

@Suppress("unused")
val gboardCustomSymbolsPatch = resourcePatch(
    name = "Custom Symbols",
    description = "新增獨立的特殊符號分頁，長按逗號->愛心\nAdd a dedicated custom symbols tab and replace the long-press comma entry with a heart shortcut.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardAboutPageResourcePatch,
        gboardCustomSymbolsFeatureMarkerPatch,
        gboardZhuyinCustomSymbolsEntryPatch,
        gboardZhuyinCustomSymbolsCorpusPatch,
        gboardZhuyinCustomSymbolsRoutingPatch,
        gboardZhuyinCustomSymbolsEmoticonStatePatch,
        gboardZhuyinCustomSymbolsHistoryPatch,
        gboardZhuyinCustomSymbolsRecyclerPatch
    )
}

@Suppress("unused")
val gboardCustomTopRowSwipePatch = resourcePatch(
    name = "Swipeable Custom Top Row",
    description = "滑動鍵盤第一排，在原生列與可自訂文字/JavaScript 列之間切換\nSwipe the keyboard top row to switch between the stock row and a customizable text/JavaScript row.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardTopRowSwipeFeatureMarkerPatch,
        gboardTopRowSwipeSoftKeyPatch,
        gboardTopRowSwipePointerPatch,
        gboardTopRowSwipeGesturePatch
    )
}

@Suppress("unused")
val gboardDeveloperOptionsPatch = resourcePatch(
    name = "Developer options",
    description = "啟用 開發人員選項 與 Flag 編輯器，你可以自己修改Flag的值\nEnable Developer options and the Flag Editor, allowing you to modify flag values.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardDeveloperOptionsFeatureMarkerPatch
    )
}

@Suppress("unused")
val gboardSymbolsFooterOrderPatch = resourcePatch(
    name = "Emojis, stickers & GIFs Tab Order",
    description = "自訂 Gboard「Emojis, stickers & GIFs」底部 tabs 的排序，支援拖曳調整\nCustomize the bottom tab order in Gboard's Emojis, stickers & GIFs panel with drag-and-drop reordering.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardSymbolFooterOrderFeatureMarkerPatch,
        gboardSymbolFooterOrderBytecodePatch
    )
}

@Suppress("unused")
val gboardClipboardEnhancementsPatch = resourcePatch(
    name = "Clipboard Enhancements",
    description = "增強剪貼簿的保留時間、數量上限、預覽行數、倒數/建立時間、順序編號與欄數\nEnhance clipboard retention time, item count limit, preview lines, countdown/creation time labels, order index, and column count.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardAboutPageResourcePatch,
        gboardPatchesSettingsPatch,
        gboardClipboardFeatureMarkerPatch,
        gboardClipboardLoaderPatch,
        gboardClipboardPrunePatch,
        gboardClipboardColumnCountPatch,
        gboardClipboardAdapterTrimPatch,
        gboardClipboardItemBindPatch
    )
}

@Suppress("unused")
val gboardClipboardContentLimitPatch = resourcePatch(
    name = "Clipboard Custom Character Limit",
    description = "自訂每個文字剪貼簿項目的最大字元數\nCustomize the maximum character count for each text clipboard item.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardClipboardContentLimitFeatureMarkerPatch,
        gboardClipboardContentLimitFlagValuePatch
    )
}

@Suppress("unused")
val gboardWebClipboardPatch = resourcePatch(
    name = "Web Clipboard",
    description = "新增手機自架的 Web Clipboard，支援瀏覽器同步、配對碼與快速設定開關\nAdd the phone-hosted Web Clipboard with browser sync, pairing, and a Quick Settings Tile.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardWebClipboardFeatureMarkerPatch,
        gboardWebClipboardManifestPatch,
        gboardWebClipboardAssetsPatch,
        gboardWebClipboardCapturePatch
    )
}

@Suppress("unused")
val gboardDeviceIntelligencePatch = resourcePatch(
    name = "Enable Inline Autofill Suggestions",
    description = "啟用內嵌自動填入建議\nEnable Inline Autofill Suggestions",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesExtensionCarrierPatch,
        gboardFeatureFlagsBytecodePatch,
        gboardDeviceIntelligenceFeatureMarkerPatch
    )
}

@Suppress("unused")
val gboardGrammarCheckerFlagPatch = resourcePatch(
    name = "Grammar Checker",
    description = "啟用 修正和建議 > 文法檢查\nEnable Text correction > Grammar check.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesExtensionCarrierPatch,
        gboardFeatureFlagsBytecodePatch,
        gboardGrammarCheckerFeatureMarkerPatch
    )
}

@Suppress("unused")
val gboardInlineSuggestionsFlagPatch = resourcePatch(
    name = "Inline Suggestions",
    description = "啟用 修正和建議 > 智慧撰寫\nEnable Text correction > Smart Compose.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesExtensionCarrierPatch,
        gboardFeatureFlagsBytecodePatch,
        gboardInlineSuggestionsFeatureMarkerPatch
    )
}

@Suppress("unused")
val gboardKeyShapeSelectionFlagPatch = resourcePatch(
    name = "Key Shape Selection",
    description = "啟用圓角按鍵，主題詳情 > 按鍵形狀\nEnable Key shape in Theme details.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesExtensionCarrierPatch,
        gboardFeatureFlagsBytecodePatch,
        gboardKeyShapeSelectionFeatureMarkerPatch
    )
}

@Suppress("unused")
val gboardAiWritingToolsPatch = resourcePatch(
    name = "AI Writing Tools",
    description = "啟用 AI 撰寫工具，支援所有語言\nEnable AI writing tools with support for all languages.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardAiWritingToolsFeatureMarkerPatch,
        gboardAiWritingToolsSettingsVisibilityPatch,
        gboardAiWritingToolsFlagValuePatch,
        gboardAiWritingToolsSignalPatch,
        gboardAiWritingToolsVoiceCommandPatch,
        gboardAiWritingToolsOfficialPreferencesPatch,
        gboardAiWritingToolsBackendFactoryPatch
    )
}

@Suppress("unused")
val gboardOcrScanTextPatch = resourcePatch(
    name = "Enable OCR / Scan Text",
    description = "啟用 OCR / 掃描文字功能，支援 拉丁、中文、日文、韓文 與 天城文 辨識後端\n" +
        "Enable the OCR / Scan Text feature with Latin, Chinese, Japanese, Korean, and Devanagari recognition backends.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardOcrFeatureMarkerPatch,
        gboardOcrFlagValuePatch,
        gboardOcrRuntimePatch,
    )
}

@Suppress("unused")
val gboardSettingsHomepagePatch = resourcePatch(
    name = "Settings Homepage Override",
    description = "允許切換新版或舊版 Gboard 設定頁面\nAllow switching between the new and legacy Gboard settings pages.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardSettingsHomepageFeatureMarkerPatch,
        gboardSettingsHomepageBytecodePatch
    )
}

@Suppress("unused")
val gboardLatinGlobeKeyIgnoreIntervalPatch = resourcePatch(
    name = "Latin Globe Key Ignore Interval",
    description = "新增英文鍵盤地球鍵忽略時間覆寫，可獨立控制輸入後切語言延遲\nAdd an independent English globe key ignore interval override for post-typing language-switch delay.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardLatinGlobeKeyIgnoreIntervalFeatureMarkerPatch,
        gboardLatinGlobeKeyIgnoreIntervalBytecodePatch
    )
}

@Suppress("unused")
val gboardZhuyinBottomRowWeightPatch = resourcePatch(
    name = "Zhuyin Bottom Row Key Sizes",
    description = "調整注音鍵盤底排按鍵大小\nAdjust Zhuyin keyboard bottom-row key sizes.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardZhuyinBottomRowWeightFeatureMarkerPatch,
        gboardZhuyinBottomRowWeightSoftKeyPatch
    )
}

@Suppress("unused")
val gboardPackageRenamePatch = resourcePatch(
    name = "Package Rename",
    description = "將套件名稱改成 dev.jason.com.google.android.inputmethod.latin 以便共存安裝\nRename the package to dev.jason.com.google.android.inputmethod.latin so it can be installed alongside the official Gboard.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPackageRenameResourcePatch
    )
}

@Suppress("unused")
val gboardSignatureBypassPatch = resourcePatch(
    name = "Add Gboard Signature Bypass",
    description = "攔截 Gboard 的簽章白名單檢查並強制通過\nBypass Gboard signature whitelist checks and force them to pass.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardSignatureBypassBytecodePatch
    )
}

object GboardPublishedPatchCatalog {
    val morpheRegistrations: Set<Patch<*>> = linkedSetOf(
        gboardZhuyinSlideInputPatch,
        gboardEnglishQwertySlideUppercaseTogglePatch,
        gboardLongPressQuickActionsPatch,
        gboardManualIncognitoModePatch,
        gboardAdvancedVoiceTypingPatch,
        gboardBluetoothMicrophonePatch,
        gboardZhuyinQuickTraditionalSimplifiedTogglePatch,
        gboardCustomSymbolsPatch,
        gboardCustomTopRowSwipePatch,
        gboardDeveloperOptionsPatch,
        gboardSymbolsFooterOrderPatch,
        gboardClipboardEnhancementsPatch,
        gboardClipboardContentLimitPatch,
        gboardWebClipboardPatch,
        gboardDeviceIntelligencePatch,
        gboardGrammarCheckerFlagPatch,
        gboardInlineSuggestionsFlagPatch,
        gboardKeyShapeSelectionFlagPatch,
        gboardAiWritingToolsPatch,
        gboardOcrScanTextPatch,
        gboardSettingsHomepagePatch,
        gboardLatinGlobeKeyIgnoreIntervalPatch,
        gboardZhuyinBottomRowWeightPatch,
        gboardPackageRenamePatch,
        gboardSignatureBypassPatch,
    )

    @Suppress("DEPRECATION")
    fun publishedInventory(version: String): String {
        val publishedPatches = morpheRegistrations.sortedBy { patch -> patch.name }.map { patch ->
            PublishedPatch(
                name = patch.name!!,
                description = patch.description,
                use = patch.use,
                dependencies = patch.dependencies.mapNotNull { dependency -> dependency.name }.sorted(),
                compatiblePackages = patch.compatiblePackages?.associate { (packageName, versions) ->
                    packageName to versions
                },
                options = patch.options.values.map { option ->
                    PublishedPatch.Option(
                        key = option.key,
                        title = option.title,
                        description = option.description,
                        required = option.required,
                        type = option.type.toString(),
                        default = option.default,
                        values = option.values,
                    )
                },
            )
        }
        val gson = GsonBuilder()
            .serializeNulls()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create()
        val inventory = JsonObject().apply {
            addProperty("version", version)
            add("patches", gson.toJsonTree(publishedPatches))
        }

        return gson.toJson(inventory) + "\n"
    }

    private class PublishedPatch(
        val name: String,
        val description: String?,
        val use: Boolean,
        val dependencies: List<String>,
        val compatiblePackages: Map<String, Set<String>?>?,
        val options: List<Option>,
    ) {
        class Option(
            val key: String,
            val title: String?,
            val description: String?,
            val required: Boolean,
            val type: String,
            val default: Any?,
            val values: Map<String, Any?>?,
        )
    }
}
