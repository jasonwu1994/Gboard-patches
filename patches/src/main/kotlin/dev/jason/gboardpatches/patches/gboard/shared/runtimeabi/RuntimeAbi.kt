package dev.jason.gboardpatches.patches.gboard.shared.runtimeabi

internal enum class RuntimeCallId(internal val encodedAbi: String) {
    ACCESS_POINT_COUNT_RUNTIME_APPLY_FLAG_VALUE("Ldev/jason/gboardpatches/extension/accesspointcount/GboardAccessPointCountRuntime;->applyFlagValue(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;"),
    ACCESSIBILITY_LAYOUT_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE("Ldev/jason/gboardpatches/extension/accessibilitylayout/GboardAccessibilityLayout1803Runtime;->applyOverriddenFlagValue(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;"),
    ACCESS_POINT_CONTRIBUTIONS_1803_AFTER_CONTROLLER_CREATED("Ldev/jason/gboardpatches/extension/accesspoint/GboardAccessPointContributions1803Runtime;->afterControllerCreated(Ljava/lang/Object;Landroid/content/Context;)V"),
    ACCESS_POINT_CONTRIBUTIONS_1803_INCLUDE_ORDER_CATALOG("Ldev/jason/gboardpatches/extension/accesspoint/GboardAccessPointContributions1803Runtime;->includeOrderCatalog(Landroid/content/Context;Ljava/lang/Object;)Ljava/lang/Object;"),
    ACCESS_POINTS_MENU_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE("Ldev/jason/gboardpatches/extension/accesspointsmenu/GboardAccessPointsMenu1803Runtime;->applyOverriddenFlagValue(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;"),
    ADD_SYMBOLS_RUNTIME_APPEND_CUSTOM_CORPUS_ITEM("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->appendCustomCorpusItem(Ljava/lang/Object;)Ljava/lang/Object;"),
    ADD_SYMBOLS_RUNTIME_BIND_CUSTOM_VIEW_HOLDER("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->bindCustomViewHolder(Ljava/lang/Object;Ljava/lang/Object;I)Z"),
    ADD_SYMBOLS_RUNTIME_BRIDGE_PROVIDER_RECEIVER("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->bridgeProviderReceiver(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
    ADD_SYMBOLS_RUNTIME_CREATE_CUSTOM_VIEW_HOLDER("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->createCustomViewHolder(Ljava/lang/Object;Ljava/lang/Object;I)Ljava/lang/Object;"),
    ADD_SYMBOLS_RUNTIME_ENSURE_EXTENSION_PROVIDER_MAPPING("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->ensureExtensionProviderMapping(Ljava/lang/Object;Ljava/lang/Object;)V"),
    ADD_SYMBOLS_RUNTIME_HANDLE_CATEGORY_BIND("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->handleCategoryBind(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)Z"),
    ADD_SYMBOLS_RUNTIME_HANDLE_CATEGORY_CHANGE("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->handleCategoryChange(Ljava/lang/Object;II)Z"),
    ADD_SYMBOLS_RUNTIME_INTERCEPT_HISTORY_WRITE("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->interceptHistoryWrite(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)Z"),
    ADD_SYMBOLS_RUNTIME_ON_EMOTICON_BODY_READY("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->onEmoticonBodyReady(Ljava/lang/Object;)V"),
    ADD_SYMBOLS_RUNTIME_ON_EMOTICON_KEYBOARD_CONSTRUCTED("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->onEmoticonKeyboardConstructed(Ljava/lang/Object;)V"),
    ADD_SYMBOLS_RUNTIME_ON_EMOTICON_RECYCLER_ADAPTER_CONSTRUCTED("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->onEmoticonRecyclerAdapterConstructed(Ljava/lang/Object;Ljava/lang/Object;)V"),
    ADD_SYMBOLS_RUNTIME_ON_EXPRESSION_CORPUS_FOOTER_TAB_CLICK("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->onExpressionCorpusFooterTabClick(Ljava/lang/Object;Ljava/lang/Object;)V"),
    ADD_SYMBOLS_RUNTIME_ON_HEADER_CALLBACK_AFTER("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->onHeaderCallbackAfter(Ljava/lang/Object;)V"),
    ADD_SYMBOLS_RUNTIME_ON_KEYBOARD_READY("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->onKeyboardReady(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V"),
    ADD_SYMBOLS_RUNTIME_OVERRIDE_CATEGORY_NAME_FROM_INDEX("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->overrideCategoryNameFromIndex(Ljava/lang/Object;I)Ljava/lang/String;"),
    ADD_SYMBOLS_RUNTIME_RESOLVE_CUSTOM_VIEW_TYPE("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->resolveCustomViewType(Ljava/lang/Object;I)I"),
    ADD_SYMBOLS_RUNTIME_RESOLVE_SELECTED_CATEGORY_INDEX("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->resolveSelectedCategoryIndex(Ljava/lang/Object;Ljava/lang/Object;)I"),
    ADD_SYMBOLS_RUNTIME_REWRITE_METRICS_KEYBOARD_TYPE("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->rewriteMetricsKeyboardType(Ljava/lang/Object;)Ljava/lang/Object;"),
    ADD_SYMBOLS_RUNTIME_REWRITE_NAVIGATION_KEYBOARD_TYPE("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->rewriteNavigationKeyboardType(Ljava/lang/Object;)Ljava/lang/Object;"),
    ADD_SYMBOLS_RUNTIME_REWRITE_PROVIDER_REQUEST_TYPE("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->rewriteProviderRequestType(Ljava/lang/Object;)Ljava/lang/Object;"),
    ADD_SYMBOLS_RUNTIME_REWRITE_PROVIDER_WRAPPER_KEYBOARD_TYPE("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->rewriteProviderWrapperKeyboardType(Ljava/lang/Object;)Ljava/lang/Object;"),
    ADD_SYMBOLS_RUNTIME_SHOULD_SKIP_DETACHED_VIEW_TRANSFORM("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->shouldSkipDetachedViewTransform(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z"),
    ADD_SYMBOLS_RUNTIME_SHOULD_SKIP_HEADER_START_EDGE_UPDATE("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsRuntime;->shouldSkipHeaderStartEdgeUpdate(Ljava/lang/Object;)Z"),
    ADD_SYMBOLS_SOFT_KEY_RUNTIME_PATCH_SOFT_KEY_METADATA("Ldev/jason/gboardpatches/extension/addsymbols/GboardAddSymbolsSoftKeyRuntime;->patchSoftKeyMetadata(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
    ADVANCED_VOICE_RUNTIME_AFTER_FLAG_VALUE("Ldev/jason/gboardpatches/extension/advancedvoice/GboardAdvancedVoice1803Runtime;->afterFlagValue(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
    ADVANCED_VOICE_RUNTIME_AFTER_1803_NATIVE_SPLIT_READINESS("Ldev/jason/gboardpatches/extension/advancedvoice/GboardAdvancedVoice1803Runtime;->after1803NativeSplitReadiness(Z)Z"),
    ADVANCED_VOICE_RUNTIME_AFTER_MDD_PROVIDER_CONSTRUCTED("Ldev/jason/gboardpatches/extension/advancedvoice/GboardAdvancedVoice1803Runtime;->afterMddProviderConstructed(Ljava/lang/Object;)V"),
    ADVANCED_VOICE_RUNTIME_BEFORE_FORMATTER_CONSTRUCTED("Ldev/jason/gboardpatches/extension/advancedvoice/GboardAdvancedVoice1803Runtime;->beforeFormatterConstructed(Ljava/util/Locale;Ljava/lang/Object;Z)Z"),
    ADVANCED_VOICE_RUNTIME_INCLUDE_EXACT_ZH_TW_SUPPORTED_LOCALE("Ldev/jason/gboardpatches/extension/advancedvoice/GboardAdvancedVoice1803Runtime;->includeExactZhTwSupportedLocale(Ljava/lang/Object;)Ljava/lang/Object;"),
    ADVANCED_VOICE_RUNTIME_SEED_APPLICATION_CONTEXT("Ldev/jason/gboardpatches/extension/advancedvoice/GboardAdvancedVoice1803Runtime;->seedApplicationContext(Landroid/content/Context;)V"),
    BLUETOOTH_MICROPHONE_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE("Ldev/jason/gboardpatches/extension/bluetoothmicrophone/GboardBluetoothMicrophone1803Runtime;->applyOverriddenFlagValue(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
    CALCULATOR_RUNTIME_ON_INPUT_VIEW_STARTED("Ldev/jason/gboardpatches/extension/calculator/GboardCalculatorLifecycleRuntime;->onInputViewStarted(Ljava/lang/Object;)V"),
    CALCULATOR_RUNTIME_ON_SELECTION_UPDATED("Ldev/jason/gboardpatches/extension/calculator/GboardCalculatorLifecycleRuntime;->onSelectionUpdated(Ljava/lang/Object;Landroid/view/View;II)V"),
    CALCULATOR_RUNTIME_ON_INPUT_WINDOW_HIDDEN("Ldev/jason/gboardpatches/extension/calculator/GboardCalculatorLifecycleRuntime;->onInputWindowHidden()V"),
    CLIPBOARD_CONTENT_LIMIT_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE("Ldev/jason/gboardpatches/extension/clipboardcontentlimit/GboardClipboardContentLimitRuntime;->applyOverriddenFlagValue(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;"),
    CLOSE_PROACTIVE_SUGGESTIONS_RUNTIME_APPLY_FLAG_VALUE("Ldev/jason/gboardpatches/extension/closeproactivesuggestions/GboardCloseProactiveSuggestionsRuntime;->applyFlagValue(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;"),
    CLIPBOARD_RUNTIME_AFTER_ADAPTER_TRIM("Ldev/jason/gboardpatches/extension/clipboard/GboardClipboardRuntime;->afterAdapterTrim(Ljava/lang/Object;)V"),
    CLIPBOARD_RUNTIME_AFTER_ITEM_BIND("Ldev/jason/gboardpatches/extension/clipboard/GboardClipboardRuntime;->afterItemBind(Ljava/lang/Object;Ljava/lang/Object;I)V"),
    CLIPBOARD_RUNTIME_BEFORE_ITEM_BIND("Ldev/jason/gboardpatches/extension/clipboard/GboardClipboardRuntime;->beforeItemBind(Ljava/lang/Object;Ljava/lang/Object;I)V"),
    CLIPBOARD_RUNTIME_HANDLE_CUSTOM_PRUNE("Ldev/jason/gboardpatches/extension/clipboard/GboardClipboardRuntime;->handleCustomPrune(Ljava/lang/Object;)Z"),
    CLIPBOARD_RUNTIME_MAYBE_BUILD_LOADER_RESULT("Ldev/jason/gboardpatches/extension/clipboard/GboardClipboardRuntime;->maybeBuildLoaderResult(Ljava/lang/Object;)Ljava/lang/Object;"),
    CLIPBOARD_RUNTIME_RESOLVE_COLUMN_COUNT_OVERRIDE("Ldev/jason/gboardpatches/extension/clipboard/GboardClipboardRuntime;->resolveColumnCountOverride(Ljava/lang/Object;)Ljava/lang/Integer;"),
    CURSOR_TRACKPAD_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE("Ldev/jason/gboardpatches/extension/cursortrackpad/GboardCursorTrackpad1803Runtime;->applyOverriddenFlagValue(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;"),
    DEVICE_INTELLIGENCE_RUNTIME_APPLY_FLAG_VALUE("Ldev/jason/gboardpatches/extension/featureflags/GboardFeatureFlagsRuntime;->applyDeviceIntelligenceFlagValue(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;"),
    EMOJI_SIZE_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE("Ldev/jason/gboardpatches/extension/emojisize/GboardEmojiSize1803Runtime;->applyOverriddenFlagValue(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;"),
    FEATURE_FLAGS_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE("Ldev/jason/gboardpatches/extension/featureflags/GboardFeatureFlagsRuntime;->applyOverriddenFlagValue(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;"),
    FLOW_MODE_RUNTIME_APPLY_FLAG_VALUE("Ldev/jason/gboardpatches/extension/flowmode/GboardFlowModeRuntime;->applyFlagValue(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;"),
    FLOW_MODE_RUNTIME_MAP_KEY_CODE("Ldev/jason/gboardpatches/extension/flowmode/GboardFlowModeRuntime;->mapKeyCode(I)I"),
    GRAMMAR_CHECKER_RUNTIME_APPLY_FLAG_VALUE("Ldev/jason/gboardpatches/extension/featureflags/GboardFeatureFlagsRuntime;->applyGrammarCheckerFlagValue(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;"),
    INLINE_SUGGESTIONS_RUNTIME_APPLY_FLAG_VALUE("Ldev/jason/gboardpatches/extension/featureflags/GboardFeatureFlagsRuntime;->applyInlineSuggestionsFlagValue(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;"),
    KEY_SHAPE_SELECTION_RUNTIME_APPLY_FLAG_VALUE("Ldev/jason/gboardpatches/extension/featureflags/GboardFeatureFlagsRuntime;->applyKeyShapeSelectionFlagValue(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;"),
    ENGLISH_UPPERCASE_TOGGLE_RUNTIME_CACHE_PATCHED_METADATA("Ldev/jason/gboardpatches/extension/keyboard/GboardEnglishUppercaseToggleRuntime;->cachePatchedMetadata(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
    ENGLISH_UPPERCASE_TOGGLE_RUNTIME_GET_CACHED_PATCHED_METADATA("Ldev/jason/gboardpatches/extension/keyboard/GboardEnglishUppercaseToggleRuntime;->getCachedPatchedMetadata(Ljava/lang/Object;)Ljava/lang/Object;"),
    ENGLISH_UPPERCASE_TOGGLE_RUNTIME_IS_ENABLED("Ldev/jason/gboardpatches/extension/keyboard/GboardEnglishUppercaseToggleRuntime;->isEnabled()Z"),
    ENGLISH_UPPERCASE_TOGGLE_RUNTIME_IS_PATCHED_METADATA("Ldev/jason/gboardpatches/extension/keyboard/GboardEnglishUppercaseToggleRuntime;->isPatchedMetadata(Ljava/lang/Object;)Z"),
    LATIN_GLOBE_KEY_IGNORE_INTERVAL_RUNTIME_APPLY_OVERRIDE("Ldev/jason/gboardpatches/extension/keyboard/GboardLatinGlobeKeyIgnoreIntervalRuntime;->applyOverride(Ljava/lang/Object;)Ljava/lang/Object;"),
    LONG_PRESS_QUICK_ACTIONS_RUNTIME_MAYBE_ENSURE_LONG_PRESS_SCHEDULED("Ldev/jason/gboardpatches/extension/longpressquickactions/GboardLongPressQuickActions1803Runtime;->maybeEnsureLongPressScheduled(Ljava/lang/Object;Landroid/view/View;)V"),
    LONG_PRESS_QUICK_ACTIONS_RUNTIME_MAYBE_HANDLE_GLOBE_GESTURE("Ldev/jason/gboardpatches/extension/longpressquickactions/GboardGlobeDragRuntime;->maybeHandleGestureDispatch(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z"),
    LONG_PRESS_QUICK_ACTIONS_RUNTIME_MAYBE_HANDLE_INPUT_EVENT("Ldev/jason/gboardpatches/extension/longpressquickactions/GboardLongPressQuickActions1803Runtime;->maybeHandleInputEvent(Landroid/inputmethodservice/InputMethodService;Ljava/lang/Object;)Z"),
    LONG_PRESS_QUICK_ACTIONS_RUNTIME_MAYBE_PATCH_METADATA("Ldev/jason/gboardpatches/extension/longpressquickactions/GboardLongPressQuickActions1803Runtime;->maybePatchMetadata(Ljava/lang/Object;Landroid/view/View;)Ljava/lang/Object;"),
    LONG_PRESS_QUICK_ACTIONS_RUNTIME_ON_GLOBE_POINTER_CANCEL("Ldev/jason/gboardpatches/extension/longpressquickactions/GboardGlobeDragRuntime;->onPointerCancel(Ljava/lang/Object;)V"),
    LONG_PRESS_QUICK_ACTIONS_RUNTIME_ON_GLOBE_POINTER_FINISH("Ldev/jason/gboardpatches/extension/longpressquickactions/GboardGlobeDragRuntime;->onPointerFinish(Ljava/lang/Object;)V"),
    MANUAL_INCOGNITO_RUNTIME_APPLY_CLIPBOARD_POLICY("Ldev/jason/gboardpatches/extension/manualincognito/GboardManualIncognitoRuntime;->applyClipboardIncognitoPolicy(Z)Z"),
    MANUAL_INCOGNITO_RUNTIME_APPLY_DICTATION_FLAG("Ldev/jason/gboardpatches/extension/manualincognito/GboardManualIncognitoRuntime;->applyDictationIncognitoFlag(Z)Z"),
    MANUAL_INCOGNITO_RUNTIME_APPLY_INCOGNITO_PREDICATE("Ldev/jason/gboardpatches/extension/manualincognito/GboardManualIncognitoRuntime;->applyIncognitoPredicate(Z)Z"),
    MANUAL_INCOGNITO_RUNTIME_APPLY_VOICE_POLICY("Ldev/jason/gboardpatches/extension/manualincognito/GboardManualIncognitoRuntime;->applyVoiceIncognitoPolicy(Z)Z"),
    MANUAL_INCOGNITO_RUNTIME_ON_INPUT_STARTING("Ldev/jason/gboardpatches/extension/manualincognito/GboardManualIncognitoRuntime;->onInputStarting(Ljava/lang/Object;Landroid/view/inputmethod/EditorInfo;)V"),
    MANUAL_INCOGNITO_RUNTIME_ON_INPUT_VIEW_STARTING("Ldev/jason/gboardpatches/extension/manualincognito/GboardManualIncognitoRuntime;->onInputViewStarting(Ljava/lang/Object;Landroid/view/inputmethod/EditorInfo;)V"),
    MANUAL_INCOGNITO_RUNTIME_ON_INPUT_WINDOW_HIDDEN("Ldev/jason/gboardpatches/extension/manualincognito/GboardManualIncognitoRuntime;->onInputWindowHidden(Ljava/lang/Object;)V"),
    OCR_RUNTIME_AFTER_THIN_INIT("Ldev/jason/gboardpatches/extension/ocr/GboardOcrRuntime;->afterThinInit(Ljava/lang/Object;)V"),
    OCR_RUNTIME_APPLY_CONFIG_LABEL("Ldev/jason/gboardpatches/extension/ocr/GboardOcrRuntime;->applyConfigLabel(Ljava/lang/String;)Ljava/lang/String;"),
    OCR_RUNTIME_APPLY_FLAG_VALUE("Ldev/jason/gboardpatches/extension/ocr/GboardOcrRuntime;->applyFlagValue(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;"),
    OCR_RUNTIME_APPLY_LANGUAGE_HINT("Ldev/jason/gboardpatches/extension/ocr/GboardOcrRuntime;->applyLanguageHint(Ljava/lang/String;)Ljava/lang/String;"),
    OCR_RUNTIME_APPLY_LOGGING_LIBRARY("Ldev/jason/gboardpatches/extension/ocr/GboardOcrRuntime;->applyLoggingLibrary(Ljava/lang/String;)Ljava/lang/String;"),
    OCR_RUNTIME_APPLY_MODULE_AVAILABILITY("Ldev/jason/gboardpatches/extension/ocr/GboardOcrRuntime;->applyModuleAvailability(Z)Z"),
    OCR_RUNTIME_APPLY_MODULE_ID("Ldev/jason/gboardpatches/extension/ocr/GboardOcrRuntime;->applyModuleId(Ljava/lang/String;)Ljava/lang/String;"),
    OCR_RUNTIME_APPLY_OPTIONAL_MODULE_NAME("Ldev/jason/gboardpatches/extension/ocr/GboardOcrRuntime;->applyOptionalModuleName(Ljava/lang/String;)Ljava/lang/String;"),
    OCR_RUNTIME_APPLY_OPTIONAL_MODULE_REQUEST("Ldev/jason/gboardpatches/extension/ocr/GboardOcrRuntime;->applyOptionalModuleRequest(Ljava/lang/Object;)Ljava/lang/Object;"),
    RAMBLER_RUNTIME_ENTER_DEFAULT_SELECTION_SUPPRESSION("Ldev/jason/gboardpatches/extension/rambler/GboardRambler1803OfficialSelectionRuntime;->enterDefaultSelectionSuppression()V"),
    RAMBLER_RUNTIME_ENTER_VOICE_SETTINGS_SCOPE("Ldev/jason/gboardpatches/extension/rambler/GboardRambler1803OfficialSelectionRuntime;->enterVoiceSettingsScope()V"),
    RAMBLER_RUNTIME_EXIT_DEFAULT_SELECTION_SUPPRESSION("Ldev/jason/gboardpatches/extension/rambler/GboardRambler1803OfficialSelectionRuntime;->exitDefaultSelectionSuppression()V"),
    RAMBLER_RUNTIME_EXIT_VOICE_SETTINGS_SCOPE("Ldev/jason/gboardpatches/extension/rambler/GboardRambler1803OfficialSelectionRuntime;->exitVoiceSettingsScope()V"),
    RAMBLER_RUNTIME_UPDATE_OFFICIAL_SELECTION("Ldev/jason/gboardpatches/extension/rambler/GboardRambler1803OfficialSelectionRuntime;->updateOfficialSelection(Z)V"),
    QUICK_INSERT_RUNTIME_APPLY_FLAG_VALUE("Ldev/jason/gboardpatches/extension/quickinsert/GboardQuickInsertRuntime;->applyFlagValue(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;"),
    ROUNDED_KEYBOARD_RUNTIME_APPLY_THEME_ADMISSION("Ldev/jason/gboardpatches/extension/roundedkeyboard/GboardRoundedKeyboardRuntime;->applyThemeAdmission(Z)Z"),
    ROUNDED_KEYBOARD_RUNTIME_RESOLVE_EFFECTIVE_RADII_DP("Ldev/jason/gboardpatches/extension/roundedkeyboard/GboardRoundedKeyboardRuntime;->resolveEffectiveRadiiDp(Ljava/lang/String;)[F"),
    SETTINGS_HOMEPAGE_RUNTIME_APPLY_SETTINGS_HOMEPAGE_POLICY("Ldev/jason/gboardpatches/extension/settingshomepage/GboardSettingsHomepageRuntime;->applySettingsHomepagePolicy(Ljava/lang/Object;)V"),
    SPLIT_KEYBOARD_RUNTIME_APPLY_ACCESS_POINT_STATE("Ldev/jason/gboardpatches/extension/splitkeyboard/GboardSplitKeyboard1803Runtime;->applyAccessPointState(Ljava/lang/String;I)I"),
    SPLIT_KEYBOARD_RUNTIME_AFTER_MODE_ENVIRONMENT_CHANGED("Ldev/jason/gboardpatches/extension/splitkeyboard/GboardSplitKeyboard1803Runtime;->afterModeEnvironmentChanged(Ljava/lang/Object;)V"),
    SPLIT_KEYBOARD_RUNTIME_INCLUDE_ACCESS_POINT_IN_ORDER("Ldev/jason/gboardpatches/extension/splitkeyboard/GboardSplitKeyboard1803Runtime;->includeAccessPointInOrder(Ljava/lang/Object;)Ljava/lang/Object;"),
    SPLIT_KEYBOARD_RUNTIME_INTERCEPT_SPLIT_ACTION("Ldev/jason/gboardpatches/extension/splitkeyboard/GboardSplitKeyboard1803Runtime;->interceptSplitAction(Ljava/lang/Object;)Z"),
    SPACEBAR_LOGO_RUNTIME_AFTER_SOFT_KEY_BOUND("Ldev/jason/gboardpatches/extension/spacebarlogo/GboardSpacebarLogo1803Runtime;->afterSoftKeyBound(Ljava/lang/Object;Ljava/lang/Object;)V"),
    SPACEBAR_LOGO_RUNTIME_BEFORE_SOFT_KEY_BOUND("Ldev/jason/gboardpatches/extension/spacebarlogo/GboardSpacebarLogo1803Runtime;->beforeSoftKeyBound(Ljava/lang/Object;Ljava/lang/Object;)V"),
    SYMBOL_FOOTER_ORDER_RUNTIME_REORDER_EXPRESSION_CORPUS_LIST("Ldev/jason/gboardpatches/extension/symbolfooter/GboardSymbolFooterOrderRuntime;->reorderExpressionCorpusList(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
    TOP_ROW_SWIPE_RUNTIME_AFTER_SOFT_KEY_BOUND("Ldev/jason/gboardpatches/extension/toprowswipe/GboardTopRowSwipeRuntime;->afterSoftKeyBound(Ljava/lang/Object;)V"),
    TOP_ROW_SWIPE_RUNTIME_CLEAR_SWIPE_SESSION("Ldev/jason/gboardpatches/extension/toprowswipe/GboardTopRowSwipeRuntime;->clearSwipeSession(Ljava/lang/Object;)V"),
    TOP_ROW_SWIPE_RUNTIME_FINISH_SWIPE_SESSION("Ldev/jason/gboardpatches/extension/toprowswipe/GboardTopRowSwipeRuntime;->finishSwipeSession(Ljava/lang/Object;)V"),
    TOP_ROW_SWIPE_RUNTIME_MAYBE_ARM_AND_RESOLVE_TOP_ROW_OWNER("Ldev/jason/gboardpatches/extension/toprowswipe/GboardTopRowSwipeRuntime;->maybeArmAndResolveTopRowOwner(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;FF)Ljava/lang/Object;"),
    TOP_ROW_SWIPE_RUNTIME_MAYBE_CONSUME_QUICK_JS_TOP_ROW_PRESS("Ldev/jason/gboardpatches/extension/toprowswipe/GboardTopRowSwipeRuntime;->maybeConsumeQuickJsTopRowPress(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z"),
    TOP_ROW_SWIPE_RUNTIME_MAYBE_CONSUME_TOP_ROW_SWIPE("Ldev/jason/gboardpatches/extension/toprowswipe/GboardTopRowSwipeRuntime;->maybeConsumeTopRowSwipe(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z"),
    TOP_ROW_SWIPE_RUNTIME_PATCH_INCOMING_SOFT_KEY_METADATA("Ldev/jason/gboardpatches/extension/toprowswipe/GboardTopRowSwipeRuntime;->patchIncomingSoftKeyMetadata(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
    WEB_CLIPBOARD_CAPTURE_BOOTSTRAP_AFTER_LATIN_IME_ON_CREATE("Ldev/jason/gboardpatches/extension/webclipboard/GboardWebClipboardCaptureBootstrap;->afterLatinImeOnCreate(Ljava/lang/Object;)V"),
    AI_WRITING_TOOLS_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE("Ldev/jason/gboardpatches/extension/writingtools/GboardAiWritingToolsRuntime;->applyOverriddenFlagValue(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
    AI_WRITING_TOOLS_VOICE_COMMAND_RUNTIME_APPLY_GEN_AI_INIT_CLIENT_TYPE("Ldev/jason/gboardpatches/extension/writingtools/GboardAiWritingToolsVoiceCommandRuntime;->applyGenAiInitClientType(Ljava/lang/Object;Z)Z"),
    AI_WRITING_TOOLS_VOICE_COMMAND_RUNTIME_APPLY_SMART_EDIT_INIT_CLIENT_TYPE("Ldev/jason/gboardpatches/extension/writingtools/GboardAiWritingToolsVoiceCommandRuntime;->applySmartEditInitClientType(Ljava/lang/Object;Z)Z"),
    AI_WRITING_TOOLS_VOICE_COMMAND_RUNTIME_BEGIN_GEN_AI_REFRESH("Ldev/jason/gboardpatches/extension/writingtools/GboardAiWritingToolsVoiceCommandRuntime;->beginGenAiRefresh(Ljava/lang/Object;)V"),
    AI_WRITING_TOOLS_VOICE_COMMAND_RUNTIME_FINISH_GEN_AI_REFRESH("Ldev/jason/gboardpatches/extension/writingtools/GboardAiWritingToolsVoiceCommandRuntime;->finishGenAiRefresh(Ljava/lang/Object;)V"),
    AI_WRITING_TOOLS_VOICE_COMMAND_RUNTIME_OBSERVE_GEN_AI_REFRESH_CLIENT_TYPE("Ldev/jason/gboardpatches/extension/writingtools/GboardAiWritingToolsVoiceCommandRuntime;->observeGenAiRefreshClientType(Ljava/lang/Object;Ljava/lang/Object;)V"),
    AI_WRITING_TOOLS_VOICE_COMMAND_RUNTIME_REMEMBER_GEN_AI_INIT_CALL("Ldev/jason/gboardpatches/extension/writingtools/GboardAiWritingToolsVoiceCommandRuntime;->rememberGenAiInitCall(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V"),
    ZHUYIN_BOTTOM_ROW_WEIGHT_RUNTIME_AFTER_SOFT_KEY_BOUND("Ldev/jason/gboardpatches/extension/zhuyinbottomrow/GboardZhuyinBottomRowWeightRuntime;->afterSoftKeyBound(Ljava/lang/Object;)V"),
    ZHUYIN_SLIDE_RUNTIME_CLEAR_POINTER_STATE("Ldev/jason/gboardpatches/extension/zhuyinslide/GboardZhuyinSlideRuntime;->clearPointerState(Ljava/lang/Object;)V"),
    ZHUYIN_SLIDE_RUNTIME_MAYBE_CAPTURE_AND_SHOULD_SUPPRESS_RETARGET("Ldev/jason/gboardpatches/extension/zhuyinslide/GboardZhuyinSlideRuntime;->maybeCaptureAndShouldSuppressRetarget(Ljava/lang/Object;Ljava/lang/Object;FF)Z"),
    ZHUYIN_SLIDE_RUNTIME_PATCH_INCOMING_SOFT_KEY_METADATA("Ldev/jason/gboardpatches/extension/zhuyinslide/GboardZhuyinSlideRuntime;->patchIncomingSoftKeyMetadata(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
    ZHUYIN_TRADITIONAL_SIMPLIFIED_TOGGLE_RUNTIME_AFTER_SOFT_KEY_BOUND("Ldev/jason/gboardpatches/extension/zhuyintoggle/GboardZhuyinTraditionalSimplifiedToggleRuntime;->afterSoftKeyBound(Ljava/lang/Object;)V"),
    ZHUYIN_TRADITIONAL_SIMPLIFIED_TOGGLE_RUNTIME_MAYBE_TOGGLE("Ldev/jason/gboardpatches/extension/zhuyintoggle/GboardZhuyinTraditionalSimplifiedToggleRuntime;->maybeToggle(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z"),
    ZHUYIN_TRADITIONAL_SIMPLIFIED_TOGGLE_RUNTIME_PATCH_INCOMING_SOFT_KEY_METADATA("Ldev/jason/gboardpatches/extension/zhuyintoggle/GboardZhuyinTraditionalSimplifiedToggleRuntime;->patchIncomingSoftKeyMetadata(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
    ZHUYIN_TRADITIONAL_SIMPLIFIED_TOGGLE_RUNTIME_PATCH_POPUP_ACTION("Ldev/jason/gboardpatches/extension/zhuyintoggle/GboardZhuyinTraditionalSimplifiedToggleRuntime;->patchPopupAction(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
}

internal data class RuntimeAbi(
    val owner: String,
    val name: String,
    val parameters: List<String>,
    val returnType: String,
    val isStatic: Boolean,
) {
    val descriptor: String
        get() = parameters.joinToString(prefix = "(", postfix = ")$returnType", separator = "")

    val reference: String
        get() = "$owner->$name$descriptor"

    companion object {
        fun decode(encoded: String): RuntimeAbi {
            val owner = encoded.substringBefore("->")
            val method = encoded.substringAfter("->")
            val name = method.substringBefore('(')
            val parametersDescriptor = method.substringAfter('(').substringBefore(')')
            return RuntimeAbi(
                owner = owner,
                name = name,
                parameters = decodeTypes(parametersDescriptor),
                returnType = method.substringAfter(')'),
                isStatic = true,
            )
        }

        private fun decodeTypes(descriptor: String): List<String> = buildList {
            var index = 0
            while (index < descriptor.length) {
                val start = index
                while (descriptor[index] == '[') index++
                if (descriptor[index] == 'L') {
                    index = descriptor.indexOf(';', index) + 1
                    require(index > 0) { "Malformed object type in runtime ABI: $descriptor" }
                } else {
                    index++
                }
                add(descriptor.substring(start, index))
            }
        }
    }
}

internal object RuntimeAbiCatalog {
    private val abiByCall = RuntimeCallId.entries.associateWith { call ->
        RuntimeAbi.decode(call.encodedAbi)
    }

    fun abi(call: RuntimeCallId): RuntimeAbi = abiByCall.getValue(call)
}
