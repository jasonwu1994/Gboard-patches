package dev.jason.gboardpatches.patches.gboard.registry

import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.ResourcePatchBuilder
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import dev.jason.gboardpatches.patches.gboard.features.accessibilitylayout.gboardAccessibilityLayoutFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.accessibilitylayout.gboardAccessibilityLayoutFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.accesspointcount.gboardAccessPointCountFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.accesspointcount.gboardAccessPointCountFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.accesspointsmenu.gboardAccessPointsMenuFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.accesspointsmenu.gboardAccessPointsMenuFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.advancedvoice.gboardAdvancedVoiceFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.bluetoothmicrophone.gboardBluetoothMicrophoneFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.bluetoothmicrophone.gboardBluetoothMicrophoneFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.backuprestore.gboardBackupRestoreFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.backuprestore.gboardBackupRestoreManifestPatch
import dev.jason.gboardpatches.patches.gboard.features.calculator.gboardCalculatorFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.calculator.gboardCalculatorLifecyclePatch
import dev.jason.gboardpatches.patches.gboard.features.about.gboardAboutPageResourcePatch
import dev.jason.gboardpatches.patches.gboard.features.addsymbols.gboardCustomSymbolsFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.clipboard.gboardClipboardFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.clipboard.gboardClipboardAdapterTrimPatch
import dev.jason.gboardpatches.patches.gboard.features.clipboard.gboardClipboardColumnCountPatch
import dev.jason.gboardpatches.patches.gboard.features.clipboard.gboardClipboardItemBindPatch
import dev.jason.gboardpatches.patches.gboard.features.clipboard.gboardClipboardLoaderPatch
import dev.jason.gboardpatches.patches.gboard.features.clipboard.gboardClipboardPrunePatch
import dev.jason.gboardpatches.patches.gboard.features.clipboardcontentlimit.gboardClipboardContentLimitFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.clipboardcontentlimit.gboardClipboardContentLimitFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.closeproactivesuggestions.gboardCloseProactiveSuggestionsFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.closeproactivesuggestions.gboardCloseProactiveSuggestionsFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.developeroptions.gboardDeveloperOptionsFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.cursortrackpad.gboardCursorTrackpadFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.cursortrackpad.gboardCursorTrackpadFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.emojisize.gboardEmojiSizeFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.emojisize.gboardEmojiSizeFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.webclipboard.gboardWebClipboardAssetsPatch
import dev.jason.gboardpatches.patches.gboard.features.webclipboard.gboardWebClipboardCapturePatch
import dev.jason.gboardpatches.patches.gboard.features.webclipboard.gboardWebClipboardFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.webclipboard.gboardWebClipboardManifestPatch
import dev.jason.gboardpatches.patches.gboard.features.websearch.gboardFloatingWebSearchFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.websearch.gboardFloatingWebSearchManifestPatch
import dev.jason.gboardpatches.patches.gboard.features.englishqwerty.gboardEnglishUppercaseToggleFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.gboardDeviceIntelligenceFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.gboardDeviceIntelligenceFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.gboardGrammarCheckerFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.gboardGrammarCheckerFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.gboardInlineSuggestionsFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.gboardInlineSuggestionsFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.gboardKeyShapeSelectionFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.gboardKeyShapeSelectionFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.flowmode.gboardFlowModeFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.lanftp.gboardLanFtpFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.lanftp.gboardLanFtpManifestPatch
import dev.jason.gboardpatches.patches.gboard.features.latinglobe.gboardLatinGlobeKeyIgnoreIntervalBytecodePatch
import dev.jason.gboardpatches.patches.gboard.features.latinglobe.gboardLatinGlobeKeyIgnoreIntervalFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.longpressquickactions.gboardLongPressQuickActionsFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.manualincognito.gboardManualIncognitoFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.manualincognito.gboardManualIncognitoLifecyclePatch
import dev.jason.gboardpatches.patches.gboard.features.manualincognito.gboardManualIncognitoPolicyPatch
import dev.jason.gboardpatches.patches.gboard.features.ocr.gboardOcrFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.packagerename.applyGboardApplicationDisplayName
import dev.jason.gboardpatches.patches.gboard.features.packagerename.gboardPackageRenameResourcePatch
import dev.jason.gboardpatches.patches.gboard.features.packagerename.isValidGboardAppDisplayName
import dev.jason.gboardpatches.patches.gboard.features.quickinsert.gboardQuickInsertFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.quickinsert.gboardQuickInsertFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.roundedkeyboard.gboardRoundedKeyboardFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.settingshomepage.gboardSettingsHomepageBytecodePatch
import dev.jason.gboardpatches.patches.gboard.features.settingshomepage.gboardSettingsHomepageFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.signaturebypass.gboardSignatureBypassBytecodePatch
import dev.jason.gboardpatches.patches.gboard.features.splitkeyboard.gboardSplitKeyboardFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.splitkeyboard.gboardSplitKeyboardAccessPointPatch
import dev.jason.gboardpatches.patches.gboard.features.splitkeyboard.gboardSplitKeyboardModePatch
import dev.jason.gboardpatches.patches.gboard.features.spacebarlogo.gboardSpacebarLogoFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.symbolfooter.gboardSymbolFooterOrderBytecodePatch
import dev.jason.gboardpatches.patches.gboard.features.symbolfooter.gboardSymbolFooterOrderFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.toprowswipe.gboardTopRowSwipeFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.writingtools.gboardAiWritingToolsFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.writingtools.gboardAiWritingTools1803AutoFixAcceptancePatch
import dev.jason.gboardpatches.patches.gboard.features.writingtools.gboardAiWritingTools1803AutoFixRoutePatch
import dev.jason.gboardpatches.patches.gboard.features.writingtools.gboardAiWritingTools1803GenAiInitPatch
import dev.jason.gboardpatches.patches.gboard.features.writingtools.gboardAiWritingTools1803GenAiRefreshPatch
import dev.jason.gboardpatches.patches.gboard.features.writingtools.gboardAiWritingTools1803SmartEditInitPatch
import dev.jason.gboardpatches.patches.gboard.features.writingtools.gboardAiWritingToolsFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesSettingsPatch
import dev.jason.gboardpatches.patches.gboard.shared.accesspoint.gboardAccessPointContributions1803Patch
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardTargetAdmission
import dev.jason.gboardpatches.patches.gboard.features.zhuyinbottomrow.gboardZhuyinBottomRowWeightFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.zhuyinbottomrow.gboardZhuyinBottomRowWeightSoftKeyPatch
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

private fun gboardPublicResourcePatch(
    featureId: String,
    name: String,
    description: String,
    default: Boolean,
    block: ResourcePatchBuilder.() -> Unit,
) = GboardTargetAdmission.admittedContributions(featureId).let { admitted ->
    resourcePatch(
        name = name.takeIf { admitted.isNotEmpty() },
        description = description,
        default = default,
    ) {
        if (admitted.isNotEmpty()) {
            block()
            GboardContributionWiring.wireAdmitted(featureId, admitted, this)
        }
    }
}

@Suppress("unused")
val gboardZhuyinSlideInputPatch = gboardPublicResourcePatch(
    featureId = "zhuyin_slide_input",
    name = "Zhuyin Slide Input",
    description = "注音鍵盤支持上下滑輸入\nEnable slide-up and slide-down input on the Zhuyin keyboard.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardAboutPageResourcePatch,
        gboardPatchesExtensionCarrierPatch,
    )
}

@Suppress("unused")
val gboardEnglishQwertySlideUppercaseTogglePatch = gboardPublicResourcePatch(
    featureId = "english_qwerty_up_flick_uppercase",
    name = "English QWERTY Up-Flick Uppercase",
    description = "英文 QWERTY 鍵盤上滑大小寫\nFlick up on the English QWERTY keyboard to toggle uppercase and lowercase.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardAboutPageResourcePatch,
        gboardPatchesSettingsPatch,
        gboardEnglishUppercaseToggleFeatureMarkerPatch,
    )
}

@Suppress("unused")
val gboardLongPressQuickActionsPatch = gboardPublicResourcePatch(
    featureId = "long_press_editing_shortcuts",
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
    )
}

@Suppress("unused")
val gboardSpacebarLogoPatch = gboardPublicResourcePatch(
    featureId = "g_logo_on_spacebar",
    name = "G Logo on Spacebar",
    description = "在空白鍵顯示 G Logo，並隱藏語言名稱\n" +
        "Show the G Logo on the spacebar and hide the language label.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardSpacebarLogoFeatureMarkerPatch,
    )
}

@Suppress("unused")
val gboardManualIncognitoModePatch = gboardPublicResourcePatch(
    featureId = "incognito_mode_toggle",
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
        gboardAccessPointContributions1803Patch,
    )
}

@Suppress("unused")
val gboardFloatingWebSearchPatch = gboardPublicResourcePatch(
    featureId = "floating_web_search",
    name = "Floating Web Search",
    description = "直接從 Gboard 開啟懸浮網頁，快速搜尋需要的資訊。\n" +
        "Open a floating web page directly from Gboard to quickly search for the information you need.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardFloatingWebSearchFeatureMarkerPatch,
        gboardFloatingWebSearchManifestPatch,
        gboardAccessPointContributions1803Patch,
    )
}

@Suppress("unused")
val gboardSimpleCalculatorPatch = gboardPublicResourcePatch(
    featureId = "simple_calculator",
    name = "Simple Calculator",
    description = "直接輸入算式，在 Gboard 推薦列顯示可捲動算式與答案。\n" +
        "Type an expression and show a scrollable equation plus its answer in Gboard's suggestion row.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardCalculatorFeatureMarkerPatch,
        gboardCalculatorLifecyclePatch,
    )
}

@Suppress("unused")
val gboardAdvancedVoiceTypingPatch = gboardPublicResourcePatch(
    featureId = "advanced_voice_typing",
    name = "Advanced Voice Typing",
    description = "啟用進階語音輸入（包含自動標點功能），並另外為不支援進階語音輸入的繁體中文語音啟用自動標點\n" +
        "Enable Advanced Voice Typing with automatic punctuation, and separately enable automatic punctuation for Traditional Chinese voice typing, which does not support Advanced Voice Typing.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardAdvancedVoiceFeatureMarkerPatch,
    )
}

@Suppress("unused")
val gboardBluetoothMicrophonePatch = gboardPublicResourcePatch(
    featureId = "use_bluetooth_microphone",
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
val gboardEmojiSizePatch = gboardPublicResourcePatch(
    featureId = "change_emoji_size",
    name = "Change emoji size",
    description = "變更表情符號大小\nChange emoji size.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardEmojiSizeFeatureMarkerPatch,
        gboardEmojiSizeFlagValuePatch,
    )
}

@Suppress("unused")
val gboardCursorTrackpadPatch = gboardPublicResourcePatch(
    featureId = "enable_cursor_trackpad_mode",
    name = "Enable cursor trackpad mode",
    description = "長按空白鍵開啟游標觸控板與鎖定模式\n" +
        "Long-press the space bar to use cursor trackpad and lock modes.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardCursorTrackpadFeatureMarkerPatch,
        gboardCursorTrackpadFlagValuePatch,
    )
}

@Suppress("unused")
val gboardAccessPointsMenuStylePatch = gboardPublicResourcePatch(
    featureId = "access_points_menu_style",
    name = "Access Points menu style",
    description = "切換新版或舊版 Access Points 選單樣式\n" +
        "Switch between the new and legacy Access Points menu styles.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardAccessPointsMenuFeatureMarkerPatch,
        gboardAccessPointsMenuFlagValuePatch,
    )
}

@Suppress("unused")
val gboardSplitKeyboardPatch = gboardPublicResourcePatch(
    featureId = "enable_split_keyboard",
    name = "Enable split keyboard",
    description = "啟用分離式鍵盤\nEnable the split keyboard layout.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardSplitKeyboardFeatureMarkerPatch,
        gboardSplitKeyboardAccessPointPatch,
        gboardSplitKeyboardModePatch,
    )
}

@Suppress("unused")
val gboardAccessibilityLayoutPatch = gboardPublicResourcePatch(
    featureId = "enable_accessibility_layout",
    name = "Enable accessibility layout",
    description = "啟用無障礙鍵盤配置\nEnable accessibility layout.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardAccessibilityLayoutFeatureMarkerPatch,
        gboardAccessibilityLayoutFlagValuePatch,
    )
}

@Suppress("unused")
val gboardRoundedKeyboardPanelPatch = gboardPublicResourcePatch(
    featureId = "rounded_keyboard_panel",
    name = "Rounded Keyboard Panel",
    description = "自訂鍵盤面板哪些角落呈現圓角，並分別設定上方與下方半徑。\n" +
        "Customize which corners of the keyboard panel are rounded, and set the top and bottom radii separately.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardRoundedKeyboardFeatureMarkerPatch,
    )
}

@Suppress("unused")
val gboardAccessPointCountPatch = gboardPublicResourcePatch(
    featureId = "access_point_count",
    name = "Top Toolbar Item Count",
    description = "自訂 Gboard 頂端工具列項目數量\n" +
        "Customize the Gboard top toolbar item count.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardAccessPointCountFeatureMarkerPatch,
        gboardAccessPointCountFlagValuePatch,
    )
}

@Suppress("unused")
val gboardCloseProactiveSuggestionsPatch = gboardPublicResourcePatch(
    featureId = "close_proactive_suggestions",
    name = "Close Proactive Suggestions",
    description = "在主動建議列顯示關閉按鈕\n" +
        "Show a dismiss button in the proactive suggestions bar.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardCloseProactiveSuggestionsFeatureMarkerPatch,
        gboardCloseProactiveSuggestionsFlagValuePatch,
    )
}

@Suppress("unused")
val gboardFlowModeAnimationPatch = gboardPublicResourcePatch(
    featureId = "flow_mode_animation",
    name = "Hyperspeed Typing Animation",
    description = "持續快速輸入時顯示動畫，並支援所有鍵盤\n" +
        "Show the animation during sustained fast typing with support for all keyboards.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardFlowModeFeatureMarkerPatch,
    )
}

@Suppress("unused")
val gboardQuickInsertPatch = gboardPublicResourcePatch(
    featureId = "quick_insert",
    name = "Quick Insert",
    description = "啟用快速插入面板與工具列入口\n" +
        "Enable the Quick Insert panel and toolbar access point.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardQuickInsertFeatureMarkerPatch,
        gboardQuickInsertFlagValuePatch,
    )
}

@Suppress("unused")
val gboardZhuyinQuickTraditionalSimplifiedTogglePatch = gboardPublicResourcePatch(
    featureId = "zhuyin_quick_traditional_simplified_toggle",
    name = "Zhuyin Quick Traditional/Simplified Toggle",
    description = "注音 ㄥ 上滑快速切換繁簡\nSwipe up on Zhuyin ㄥ to quickly toggle Traditional and Simplified Chinese.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardZhuyinSlideInputPatch,
    )
}

@Suppress("unused")
val gboardCustomSymbolsPatch = gboardPublicResourcePatch(
    featureId = "custom_symbols",
    name = "Custom Symbols",
    description = "新增獨立的特殊符號分頁，長按逗號->愛心\nAdd a dedicated custom symbols tab and replace the long-press comma entry with a heart shortcut.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardAboutPageResourcePatch,
        gboardCustomSymbolsFeatureMarkerPatch,
    )
}

@Suppress("unused")
val gboardCustomTopRowSwipePatch = gboardPublicResourcePatch(
    featureId = "swipeable_custom_top_row",
    name = "Swipeable Custom Top Row",
    description = "滑動鍵盤第一排，在原生列與可自訂文字/JavaScript 列之間切換\nSwipe the keyboard top row to switch between the stock row and a customizable text/JavaScript row.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardTopRowSwipeFeatureMarkerPatch,
    )
}

@Suppress("unused")
val gboardDeveloperOptionsPatch = gboardPublicResourcePatch(
    featureId = "developer_options",
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
val gboardBackupRestorePatch = gboardPublicResourcePatch(
    featureId = "backup_restore",
    name = "Backup & Restore",
    description = "匯出或還原全部 Patches 設定，並備份、比較及還原 Gboard PB/XML flag store\n" +
        "Export or restore all Patches settings, and back up, compare, or restore the Gboard PB/XML flag store.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardBackupRestoreFeatureMarkerPatch,
        gboardBackupRestoreManifestPatch,
    )
}

@Suppress("unused")
val gboardSymbolsFooterOrderPatch = gboardPublicResourcePatch(
    featureId = "emojis_stickers_gifs_tab_order",
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
val gboardClipboardEnhancementsPatch = gboardPublicResourcePatch(
    featureId = "clipboard_enhancements",
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
val gboardClipboardContentLimitPatch = gboardPublicResourcePatch(
    featureId = "clipboard_custom_character_limit",
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
val gboardWebClipboardPatch = gboardPublicResourcePatch(
    featureId = "web_clipboard",
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
val gboardLanFtpServerPatch = gboardPublicResourcePatch(
    featureId = "lan_ftp_server",
    name = "FTP Server",
    description = "新增區域網路 FTP 伺服器，支援檔案瀏覽、傳輸與下載續傳\n" +
        "Add a LAN FTP server with file browsing, transfers, and download resume.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardLanFtpFeatureMarkerPatch,
        gboardLanFtpManifestPatch,
    )
}

@Suppress("unused")
val gboardDeviceIntelligencePatch = gboardPublicResourcePatch(
    featureId = "enable_inline_autofill_suggestions",
    name = "Enable Inline Autofill Suggestions",
    description = "啟用內嵌自動填入建議\nEnable Inline Autofill Suggestions",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesExtensionCarrierPatch,
        gboardDeviceIntelligenceFlagValuePatch,
        gboardDeviceIntelligenceFeatureMarkerPatch
    )
}

@Suppress("unused")
val gboardGrammarCheckerFlagPatch = gboardPublicResourcePatch(
    featureId = "grammar_checker",
    name = "Grammar Checker",
    description = "啟用 修正和建議 > 文法檢查\nEnable Text correction > Grammar check.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesExtensionCarrierPatch,
        gboardGrammarCheckerFlagValuePatch,
        gboardGrammarCheckerFeatureMarkerPatch
    )
}

@Suppress("unused")
val gboardInlineSuggestionsFlagPatch = gboardPublicResourcePatch(
    featureId = "inline_suggestions",
    name = "Inline Suggestions",
    description = "啟用 修正和建議 > 智慧撰寫\nEnable Text correction > Smart Compose.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesExtensionCarrierPatch,
        gboardInlineSuggestionsFlagValuePatch,
        gboardInlineSuggestionsFeatureMarkerPatch
    )
}

@Suppress("unused")
val gboardKeyShapeSelectionFlagPatch = gboardPublicResourcePatch(
    featureId = "key_shape_selection",
    name = "Key Shape Selection",
    description = "啟用圓角按鍵，主題詳情 > 按鍵形狀\nEnable Key shape in Theme details.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesExtensionCarrierPatch,
        gboardKeyShapeSelectionFlagValuePatch,
        gboardKeyShapeSelectionFeatureMarkerPatch
    )
}

@Suppress("unused")
val gboardAiWritingToolsPatch = gboardPublicResourcePatch(
    featureId = "ai_writing_tools",
    name = "AI Writing Tools",
    description = "啟用 AI 撰寫工具，支援所有語言\nEnable AI writing tools with support for all languages.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardAiWritingToolsFeatureMarkerPatch,
        gboardAiWritingToolsFlagValuePatch,
        gboardAiWritingTools1803AutoFixRoutePatch,
        gboardAiWritingTools1803AutoFixAcceptancePatch,
        gboardAiWritingTools1803GenAiInitPatch,
        gboardAiWritingTools1803GenAiRefreshPatch,
        gboardAiWritingTools1803SmartEditInitPatch,
    )
}

@Suppress("unused")
val gboardOcrScanTextPatch = gboardPublicResourcePatch(
    featureId = "enable_ocr_scan_text",
    name = "Enable OCR / Scan Text",
    description = "啟用 OCR / 掃描文字功能，支援 拉丁、中文、日文、韓文 與 天城文 辨識後端\n" +
        "Enable the OCR / Scan Text feature with Latin, Chinese, Japanese, Korean, and Devanagari recognition backends.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(
        gboardPatchesSettingsPatch,
        gboardOcrFeatureMarkerPatch,
    )
}

@Suppress("unused")
val gboardSettingsHomepagePatch = gboardPublicResourcePatch(
    featureId = "settings_homepage_override",
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
val gboardLatinGlobeKeyIgnoreIntervalPatch = gboardPublicResourcePatch(
    featureId = "latin_globe_key_ignore_interval",
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
val gboardZhuyinBottomRowWeightPatch = gboardPublicResourcePatch(
    featureId = "zhuyin_bottom_row_key_sizes",
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
val gboardPackageRenamePatch = gboardPublicResourcePatch(
    featureId = "package_rename",
    name = "Package Rename",
    description = "將套件名稱改成 dev.jason.com.google.android.inputmethod.latin，並可自訂 App 名稱，以便共存安裝\n" +
        "Rename the package to dev.jason.com.google.android.inputmethod.latin and allow " +
        "a custom app name so it can be installed alongside the official Gboard.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    val appDisplayName = stringOption(
        key = "app_display_name",
        default = "GboardWu",
        values = linkedMapOf(
            "Gboard" to "Gboard",
            "Gboard Dev" to "Gboard Dev",
            "Gboard Patches" to "Gboard Patches",
            "Gboard Mod" to "Gboard Mod",
            "GboardWu" to "GboardWu",
        ),
        title = "App 名稱 / App name",
        description = "",
        required = true,
    ) { value ->
        isValidGboardAppDisplayName(value)
    }

    dependsOn(
        gboardPackageRenameResourcePatch
    )

    finalize {
        document("AndroidManifest.xml").use { manifestDocument ->
            applyGboardApplicationDisplayName(
                manifestDocument = manifestDocument,
                displayName = checkNotNull(appDisplayName.value),
            )
        }
    }
}

@Suppress("unused")
val gboardSignatureBypassPatch = gboardPublicResourcePatch(
    featureId = "add_gboard_signature_bypass",
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
        gboardSpacebarLogoPatch,
        gboardManualIncognitoModePatch,
        gboardFloatingWebSearchPatch,
        gboardSimpleCalculatorPatch,
        gboardAdvancedVoiceTypingPatch,
        gboardBluetoothMicrophonePatch,
        gboardEmojiSizePatch,
        gboardCursorTrackpadPatch,
        gboardAccessPointsMenuStylePatch,
        gboardSplitKeyboardPatch,
        gboardAccessibilityLayoutPatch,
        gboardRoundedKeyboardPanelPatch,
        gboardAccessPointCountPatch,
        gboardCloseProactiveSuggestionsPatch,
        gboardFlowModeAnimationPatch,
        gboardQuickInsertPatch,
        gboardZhuyinQuickTraditionalSimplifiedTogglePatch,
        gboardCustomSymbolsPatch,
        gboardCustomTopRowSwipePatch,
        gboardBackupRestorePatch,
        gboardDeveloperOptionsPatch,
        gboardSymbolsFooterOrderPatch,
        gboardClipboardEnhancementsPatch,
        gboardClipboardContentLimitPatch,
        gboardWebClipboardPatch,
        gboardLanFtpServerPatch,
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
    ).filterTo(linkedSetOf()) { patch -> patch.name != null }

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
