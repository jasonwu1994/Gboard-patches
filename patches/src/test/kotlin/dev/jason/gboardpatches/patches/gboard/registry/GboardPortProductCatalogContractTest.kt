package dev.jason.gboardpatches.patches.gboard.registry

import app.morphe.patcher.patch.Patch
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.jason.gboardpatches.patches.gboard.features.accessibilitylayout.gboardAccessibilityLayoutFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.accesspointcount.gboardAccessPointCountFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.accesspointsmenu.gboardAccessPointsMenuFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.addsymbols.gboardZhuyinCustomSymbolsEntryPatch
import dev.jason.gboardpatches.patches.gboard.features.advancedvoice.gboardAdvancedVoiceFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.bluetoothmicrophone.gboardBluetoothMicrophoneFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.clipboardcontentlimit.gboardClipboardContentLimitFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.closeproactivesuggestions.gboardCloseProactiveSuggestionsFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.cursortrackpad.gboardCursorTrackpadFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.emojisize.gboardEmojiSizeFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.englishqwerty.gboardEnglishQwertySoftKeyPatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.gboardDeviceIntelligenceFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.gboardGrammarCheckerFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.gboardInlineSuggestionsFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.gboardKeyShapeSelectionFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.flowmode.gboardFlowModeFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.ocr.gboardOcrFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.quickinsert.gboardQuickInsertFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.longpressquickactions.gboardLongPressQuickActionsSoftKeyPatch
import dev.jason.gboardpatches.patches.gboard.features.spacebarlogo.gboardSpacebarLogoSoftKeyPatch
import dev.jason.gboardpatches.patches.gboard.features.toprowswipe.gboardTopRowSwipeSoftKeyPatch
import dev.jason.gboardpatches.patches.gboard.features.writingtools.gboardAiWritingToolsFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.zhuyinbottomrow.gboardZhuyinBottomRowWeightSoftKeyPatch
import dev.jason.gboardpatches.patches.gboard.features.zhuyinslide.gboardZhuyinSlideSoftKeyPatch
import dev.jason.gboardpatches.patches.gboard.features.zhuyintraditionalsimplifiedtoggle.gboardZhuyinTraditionalSimplifiedToggleSoftKeyPatch
import dev.jason.gboardpatches.patches.gboard.shared.GboardFlagFamilyFeature
import dev.jason.gboardpatches.patches.gboard.shared.GboardSoftKeyFamilyFeature
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardTargetAdmission
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardPortProductCatalogContractTest {
    private val catalogPath = repositoryRoot().resolve(CATALOG_PATH)
    private val catalog: JsonObject = JsonParser.parseString(
        Files.readString(catalogPath, StandardCharsets.UTF_8),
    ).asJsonObject

    @Test
    fun catalogIsDeterministicAndDeclaresSelectedOnlyZeroSelectionComposition() {
        assertEquals("gboard-port-product-catalog.v1", catalog["format"].asString)
        assertEquals("1.12.0", catalog["catalog_version"].asString)
        val composition = catalog.getAsJsonObject("composition")
        assertEquals(
            setOf("selected_only_call_chain", "runtime_feature_mask"),
            composition.keySet(),
        )
        assertTrue(composition["selected_only_call_chain"].asBoolean)
        assertFalse(composition["runtime_feature_mask"].asBoolean)

        val features = features()
        assertEquals(
            features.map { feature -> feature["feature_id"].asString }.sorted(),
            features.map { feature -> feature["feature_id"].asString },
        )
        assertMatchesPublishedRegistrationCount(features.size)
        assertEquals(features.size, features.map { it["feature_id"].asString }.distinct().size)
        assertEquals(features.size, features.map { it["public_patch_name"].asString }.distinct().size)

        val expectedDigest = Files.readString(
            repositoryRoot().resolve(DIGEST_PATH),
            StandardCharsets.UTF_8,
        ).trim()
        assertEquals(expectedDigest, sha256(normalizedCatalogBytes()))
    }

    @Test
    fun migrationScopeAndConsumerFilesFormAClosedDeterministicControlPlane() {
        val repositoryRoot = repositoryRoot()
        assertEquals(
            EXPECTED_MIGRATION_SCOPES,
            features().associate { feature ->
                feature["feature_id"].asString to feature["migration_scope"].asString
            },
        )

        features().forEach { feature ->
            val featureId = feature["feature_id"].asString
            val consumers = feature.getAsJsonArray("consumer_files").map { it.asString }
            assertTrue("$featureId must declare a consumer file", consumers.isNotEmpty())
            assertTrue(
                "$featureId must include the public patch registry",
                REGISTRY_PATH in consumers,
            )
            assertEquals("$featureId consumer order drift", consumers.sorted(), consumers)
            assertEquals(
                "$featureId duplicate consumer file",
                consumers.size,
                consumers.distinct().size,
            )
            consumers.forEach { consumer ->
                val path = Path.of(consumer)
                assertFalse("$featureId consumer must be relative: $consumer", path.isAbsolute)
                assertFalse("$featureId consumer must use forward slashes: $consumer", '\\' in consumer)
                assertFalse(
                    "$featureId consumer must not contain a glob: $consumer",
                    consumer.any { character -> character in "*?[]{}" },
                )
                assertFalse(
                    "$featureId consumer must not contain traversal: $consumer",
                    consumer.split('/').any { segment -> segment in setOf("", ".", "..") },
                )
                assertTrue(
                    "$featureId consumer must be production-only: $consumer",
                    consumer.startsWith("patches/src/main/kotlin/") ||
                        consumer.startsWith("patches/src/main/resources/"),
                )
                assertTrue(
                    "$featureId consumer does not exist: $consumer",
                    Files.isRegularFile(repositoryRoot.resolve(path)),
                )
            }
        }
    }

    @Test
    fun webClipboardConsumerInventoryExactlyCoversItsPackagedResources() {
        val repositoryRoot = repositoryRoot()
        val webClipboard = features().single { feature ->
            feature["feature_id"].asString == "web_clipboard"
        }
        val declaredResources = webClipboard.getAsJsonArray("consumer_files")
            .map { it.asString }
            .filter { consumer -> consumer.startsWith("patches/src/main/resources/") }
        val packagedResources = WEB_CLIPBOARD_RESOURCE_ROOTS.flatMap { resourceRoot ->
            Files.walk(repositoryRoot.resolve(resourceRoot)).use { paths ->
                paths.filter(Files::isRegularFile)
                    .map { path ->
                        repositoryRoot.relativize(path).toString().replace('\\', '/')
                    }
                    .toList()
            }
        }.sorted()

        assertEquals(packagedResources, declaredResources)
    }

    @Test
    fun admittedBindingUnionExactlyMatchesTheAuthoritative1803Profile() {
        val bindingProfile = JsonParser.parseString(
            Files.readString(
                repositoryRoot().resolve(BINDINGS_PATH),
                StandardCharsets.UTF_8,
            ),
        ).asJsonObject
        assertEquals("18.0.3", bindingProfile["target_version"].asString)
        val authoritativeKeys = bindingProfile.getAsJsonObject("bindings").keySet()
        val authoritativeKinds = bindingProfile.getAsJsonObject("bindings").entrySet()
            .associate { (key, binding) -> key to binding.asJsonObject["kind"].asString }
        val bindingContracts = catalog.getAsJsonObject("binding_contracts").entrySet()
            .associate { (key, kind) -> key to kind.asString }
        val requiredKeys = features().flatMap { feature ->
            val featureId = feature["feature_id"].asString
            val admitted = GboardTargetAdmission.admittedContributionIdsByFeature
                .getValue(featureId)
            feature.getAsJsonArray("contributions")
                .map { contribution -> contribution.asJsonObject }
                .filter { contribution ->
                    contribution["contribution_id"].asString in admitted
                }
                .flatMap { contribution ->
                    contribution.getAsJsonArray("required_bindings")
                    .map { binding -> binding.asString }
                }
        }.toSet()

        assertEquals(32, authoritativeKeys.size)
        assertEquals(authoritativeKeys, requiredKeys)
        assertEquals(
            authoritativeKinds,
            bindingContracts.filterKeys { binding -> binding in authoritativeKeys },
        )
    }

    @Test
    fun zhuyinBottomRowDeclaresBothSoftKeyBindingsResolvedByTheComposer() {
        val bottomRow = features().single { feature ->
            feature["feature_id"].asString == "zhuyin_bottom_row_key_sizes"
        }.getAsJsonArray("contributions").single().asJsonObject

        assertEquals(
            listOf("soft_key_bind", "soft_key_metadata_type"),
            bottomRow.getAsJsonArray("required_bindings").map { it.asString },
        )
    }

    @Test
    fun flagFamilyDeclaresSeventeenSelectedOnlyComposerCallsInCanonicalOrder() {
        val flagContributions = features().flatMap { feature ->
            feature.getAsJsonArray("contributions")
                .map { contribution -> contribution.asJsonObject }
                .filter { contribution ->
                    contribution["anchor_family_id"].asString == "flag_bool_getter"
                }
                .map { contribution -> feature["feature_id"].asString to contribution }
        }.sortedBy { (_, contribution) -> contribution["order"].asInt }

        assertEquals(17, flagContributions.size)
        assertEquals(
            listOf(
                10, 20, 30, 40, 100, 200, 300, 400,
                500, 600, 700, 800, 900, 1000, 1100, 1200, 1300,
            ),
            flagContributions.map { (_, contribution) -> contribution["order"].asInt },
        )
        val runtimeCalls = flagContributions.map { (_, contribution) ->
            contribution.getAsJsonArray("runtime_calls").first().asString
        }
        assertEquals(17, runtimeCalls.distinct().size)
        assertFalse("FEATURE_FLAGS_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE" in runtimeCalls)
        flagContributions.forEach { (_, contribution) ->
            assertEquals(
                setOf("flag_bool_getter", "flag_name_field"),
                contribution.getAsJsonArray("required_bindings").map { it.asString }.toSet(),
            )
        }
        runtimeCalls.forEach { runtimeCall ->
            assertTrue(runtimeCall in enumValues<RuntimeCallId>().map { it.name })
        }
    }

    @Test
    fun gestureFamilyAndTogglePopupDeclareSeparateCanonicalContributions() {
        val byFeatureId = features().associateBy { feature -> feature["feature_id"].asString }
        val topRowGesture = byFeatureId.getValue("swipeable_custom_top_row")
            .getAsJsonArray("contributions")
            .map { it.asJsonObject }
            .single { it["anchor_family_id"].asString == "gesture_dispatch" }
        assertEquals(
            listOf(
                "TOP_ROW_SWIPE_RUNTIME_MAYBE_CONSUME_TOP_ROW_SWIPE",
                "TOP_ROW_SWIPE_RUNTIME_MAYBE_CONSUME_QUICK_JS_TOP_ROW_PRESS",
            ),
            topRowGesture.getAsJsonArray("runtime_calls").map { it.asString },
        )

        val toggleContributions = byFeatureId
            .getValue("zhuyin_quick_traditional_simplified_toggle")
            .getAsJsonArray("contributions")
            .map { it.asJsonObject }
        val toggleGesture = toggleContributions.single {
            it["anchor_family_id"].asString == "gesture_dispatch"
        }
        assertEquals(
            listOf("ZHUYIN_TRADITIONAL_SIMPLIFIED_TOGGLE_RUNTIME_MAYBE_TOGGLE"),
            toggleGesture.getAsJsonArray("runtime_calls").map { it.asString },
        )
        assertEquals(
            setOf(
                "gesture_dispatch",
                "gesture_dispatcher_type",
                "gesture_stock_delegate_field",
                "gesture_stock_dispatch",
            ),
            toggleGesture.getAsJsonArray("required_bindings").map { it.asString }.toSet(),
        )

        val popup = toggleContributions.single {
            it["contribution_id"].asString ==
                "zhuyin_quick_traditional_simplified_toggle.popup"
        }
        assertEquals("dedicated_bytecode", popup["anchor_family_id"].asString)
        assertEquals(
            listOf("zhuyin_toggle_popup_action"),
            popup.getAsJsonArray("required_bindings").map { it.asString },
        )
        assertEquals(
            listOf("ZHUYIN_TRADITIONAL_SIMPLIFIED_TOGGLE_RUNTIME_PATCH_POPUP_ACTION"),
            popup.getAsJsonArray("runtime_calls").map { it.asString },
        )
    }

    @Test
    fun pointerContributionsDeclareTheirExact1803AnchorBindings() {
        val pointerContributionsById = features().flatMap { feature ->
            feature.getAsJsonArray("contributions")
                .map { it.asJsonObject }
                .filter { it["anchor_family_id"].asString == "pointer_lifecycle" }
        }.associateBy { contribution -> contribution["contribution_id"].asString }
        val pointerBindingsByContribution = pointerContributionsById.mapValues { (_, contribution) ->
                contribution.getAsJsonArray("required_bindings").map { it.asString }.toSet()
        }
        val lifecycle = setOf(
            "pointer_owner",
            "pointer_cancel",
            "pointer_reset",
            "pointer_owner_type",
            "pointer_finish",
            "pointer_pre_reset",
            "soft_key_view_type",
        )
        assertEquals(
            mapOf(
                "english_qwerty_up_flick_uppercase.pointer" to lifecycle,
                "long_press_editing_shortcuts.pointer" to
                    lifecycle,
                "swipeable_custom_top_row.pointer" to lifecycle,
                "zhuyin_slide_input.pointer" to lifecycle,
            ),
            pointerBindingsByContribution,
        )
        val pointerRuntimeCallsByContribution = pointerContributionsById.mapValues {
                (_, contribution) ->
            contribution.getAsJsonArray("runtime_calls").map { it.asString }
        }
        assertEquals(
            mapOf(
                "english_qwerty_up_flick_uppercase.pointer" to listOf(
                    "ENGLISH_UPPERCASE_TOGGLE_RUNTIME_IS_ENABLED",
                    "ENGLISH_UPPERCASE_TOGGLE_RUNTIME_IS_PATCHED_METADATA",
                ),
                "long_press_editing_shortcuts.pointer" to listOf(
                    "LONG_PRESS_QUICK_ACTIONS_RUNTIME_MAYBE_ENSURE_LONG_PRESS_SCHEDULED",
                    "LONG_PRESS_QUICK_ACTIONS_RUNTIME_ON_GLOBE_POINTER_FINISH",
                    "LONG_PRESS_QUICK_ACTIONS_RUNTIME_ON_GLOBE_POINTER_CANCEL",
                ),
                "swipeable_custom_top_row.pointer" to listOf(
                    "TOP_ROW_SWIPE_RUNTIME_MAYBE_ARM_AND_RESOLVE_TOP_ROW_OWNER",
                    "TOP_ROW_SWIPE_RUNTIME_FINISH_SWIPE_SESSION",
                    "TOP_ROW_SWIPE_RUNTIME_CLEAR_SWIPE_SESSION",
                ),
                "zhuyin_slide_input.pointer" to listOf(
                    "ZHUYIN_SLIDE_RUNTIME_MAYBE_CAPTURE_AND_SHOULD_SUPPRESS_RETARGET",
                    "ZHUYIN_SLIDE_RUNTIME_CLEAR_POINTER_STATE",
                ),
            ),
            pointerRuntimeCallsByContribution,
        )
    }

    @Test
    fun flagCatalogIsCongruentWithItsExactContributionMarkerAndRegistryDependency() {
        val byFeatureId = features().associateBy { feature -> feature["feature_id"].asString }
        val registrations = GboardPublishedPatchCatalog.morpheRegistrations

        FLAG_FEATURE_CONTRACTS.forEach { contract ->
            val feature = byFeatureId.getValue(contract.featureId)
            val contribution = feature.getAsJsonArray("contributions")
                .map { it.asJsonObject }
                .single { it["anchor_family_id"].asString == "flag_bool_getter" }
            assertEquals(
                "${contract.featureId} composition order drift",
                contract.composerFeature.compositionOrder,
                contribution["order"].asInt,
            )
            assertEquals(
                "${contract.featureId} runtime call drift",
                contract.composerFeature.runtimeCall.name,
                contribution.getAsJsonArray("runtime_calls").first().asString,
            )
            val contributionSource = Files.readString(
                repositoryRoot().resolve(contract.contributionSource),
                StandardCharsets.UTF_8,
            ).propertyBlock(contract.contributionProperty)
            val composerFeatureReference =
                "GboardFlagFamilyFeature.${contract.composerFeature.name}"
            assertTrue(
                "${contract.featureId} does not own $composerFeatureReference",
                contributionSource.contains(composerFeatureReference),
            )
            (GboardFlagFamilyFeature.entries - contract.composerFeature).forEach { siblingFeature ->
                assertFalse(
                    "${contract.featureId} contribution also owns sibling $siblingFeature",
                    contributionSource.contains("GboardFlagFamilyFeature.${siblingFeature.name}"),
                )
            }

            val marker = feature["feature_marker"].asString
            val markerSource = Files.readString(
                repositoryRoot().resolve(contract.markerSource),
                StandardCharsets.UTF_8,
            )
            assertTrue(
                "${contract.featureId} marker source does not own $marker",
                markerSource.contains("\"$marker\""),
            )

            val publicPatch = registrations.single {
                it.name == feature["public_patch_name"].asString
            }
            assertTrue(
                "${contract.featureId} public patch does not depend on its selected contribution",
                publicPatch.dependencies.any { dependency ->
                    dependency === contract.contributionPatch
                },
            )
        }
    }

    @Test
    fun softKeyCatalogMatchesTheCharacterizedPhaseOrderAndExactFeatureOwnership() {
        val byFeatureId = features().associateBy { feature -> feature["feature_id"].asString }
        val registrations = GboardPublishedPatchCatalog.morpheRegistrations

        assertEquals(8, SOFT_KEY_FEATURE_CONTRACTS.size)
        assertEquals(
            11,
            features().sumOf { feature ->
                feature.getAsJsonArray("contributions").count { contribution ->
                    contribution.asJsonObject["anchor_family_id"].asString == "soft_key_bind"
                }
            },
        )
        SOFT_KEY_FEATURE_CONTRACTS.forEach { contract ->
            val feature = byFeatureId.getValue(contract.featureId)
            val contributions = feature.getAsJsonArray("contributions")
                .map { it.asJsonObject }
                .filter { it["anchor_family_id"].asString == "soft_key_bind" }
                .associateBy { it["phase"].asString }
            val expected = buildMap {
                contract.composerFeature.beforeOrder?.let { order ->
                    put(
                        "before_stock",
                        order to contract.composerFeature.beforeRuntimeCalls.map { it.name },
                    )
                }
                contract.composerFeature.afterOrder?.let { order ->
                    put(
                        "after_stock",
                        order to contract.composerFeature.afterRuntimeCalls.map { it.name },
                    )
                }
            }
            assertEquals("${contract.featureId} SoftKey phase drift", expected.keys, contributions.keys)
            expected.forEach { (phase, phaseContract) ->
                val contribution = contributions.getValue(phase)
                assertEquals(
                    "${contract.featureId} $phase order drift",
                    phaseContract.first,
                    contribution["order"].asInt,
                )
                assertEquals(
                    "${contract.featureId} $phase runtime inventory drift",
                    phaseContract.second,
                    contribution.getAsJsonArray("runtime_calls").map { it.asString },
                )
            }

            val contributionSource = Files.readString(
                repositoryRoot().resolve(contract.contributionSource),
                StandardCharsets.UTF_8,
            ).propertyBlock(contract.contributionProperty)
            val featureReference = "GboardSoftKeyFamilyFeature.${contract.composerFeature.name}"
            assertTrue(
                "${contract.featureId} does not own $featureReference",
                contributionSource.contains(featureReference),
            )
            (GboardSoftKeyFamilyFeature.entries - contract.composerFeature).forEach { sibling ->
                assertFalse(
                    "${contract.featureId} contribution also owns sibling $sibling",
                    contributionSource.contains("GboardSoftKeyFamilyFeature.${sibling.name}"),
                )
            }

            val publicPatch = registrations.single {
                it.name == feature["public_patch_name"].asString
            }
            assertTrue(
                "${contract.featureId} public patch does not depend on its SoftKey contribution",
                publicPatch.dependencies.any { dependency ->
                    dependency === contract.contributionPatch
                },
            )
        }
    }

    @Test
    fun catalogCoversThePublishedRegistryAndItsPublicDependencies() {
        val features = features()
        val byPatchName = features.associateBy { feature -> feature["public_patch_name"].asString }
        val byFeatureId = features.associateBy { feature -> feature["feature_id"].asString }
        val registrations = GboardPublishedPatchCatalog.morpheRegistrations
        val publishedNames = registrations.mapNotNull { patch -> patch.name }.toSet()

        assertEquals(publishedNames, byPatchName.keys)
        registrations.forEach { patch ->
            val feature = byPatchName.getValue(patch.name!!)
            val expectedDependencies = feature.getAsJsonArray("depends_on_feature_ids")
                ?.map { dependency ->
                    byFeatureId.getValue(dependency.asString)["public_patch_name"].asString
                }
                .orEmpty()
                .sorted()
            val actualDependencies = patch.dependencies.mapNotNull { dependency -> dependency.name }
                .filter { dependencyName -> dependencyName in publishedNames }
                .sorted()
            assertEquals("Public dependency drift for ${patch.name}", expectedDependencies, actualDependencies)
        }
    }

    @Test
    fun markersBindingsAndRuntimeCallsAreLocallyVerifiable() {
        val repositoryRoot = repositoryRoot()
        val featureSource = Files.walk(
            repositoryRoot.resolve("patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features"),
        ).use { paths ->
            paths.filter { path -> Files.isRegularFile(path) && path.toString().endsWith(".kt") }
                .sorted()
                .map { path -> Files.readString(path, StandardCharsets.UTF_8) }
                .toList()
                .joinToString("\n")
        }
        val knownCalls = enumValues<RuntimeCallId>().map { call -> call.name }.toSet()
        val knownFamilies = catalog.getAsJsonArray("anchor_families").map { it.asString }.toSet()

        features().forEach { feature ->
            val contributions = feature.getAsJsonArray("contributions").map { it.asJsonObject }
            val isFlagFeature = contributions.any { contribution ->
                contribution["anchor_family_id"].asString == "flag_bool_getter"
            }
            val marker = feature["feature_marker"]
            if (!marker.isJsonNull && !isFlagFeature) {
                assertTrue(
                    "Marker is not owned by feature source: ${marker.asString}",
                    featureSource.contains("\"${marker.asString}\""),
                )
            }

            assertTrue(contributions.isNotEmpty())
            assertEquals(
                contributions.size,
                contributions.map { contribution -> contribution["contribution_id"].asString }.distinct().size,
            )
            contributions.forEach { contribution ->
                assertTrue(contribution["anchor_family_id"].asString in knownFamilies)
                contribution.getAsJsonArray("runtime_calls").forEach { runtimeCall ->
                    assertTrue(
                        "Unknown RuntimeCallId ${runtimeCall.asString}",
                        runtimeCall.asString in knownCalls,
                    )
                }
                contribution.getAsJsonArray("required_bindings").forEach { binding ->
                    assertTrue(binding.asString.matches(Regex("[a-z][a-z0-9_]*")))
                }
            }
        }
    }

    @Test
    fun schemaIsCheckedInBesideTheCatalog() {
        val schemaName = catalog["\$schema"].asString
        assertEquals("gboard-port-product-catalog.schema.json", schemaName)
        val schema = JsonParser.parseString(
            Files.readString(catalogPath.resolveSibling(schemaName), StandardCharsets.UTF_8),
        ).asJsonObject
        assertNotNull(schema)
        val definitions = schema.getAsJsonObject("definitions")
        val requiredFeatureFields = definitions.getAsJsonObject("feature")
            .getAsJsonArray("required").map { it.asString }.toSet()
        assertTrue(
            requiredFeatureFields.containsAll(setOf("migration_scope", "consumer_files")),
        )
        assertEquals(
            setOf(
                "generic",
                "version-neutral-extension",
                "version-sensitive",
            ),
            definitions.getAsJsonObject("migrationScope")
                .getAsJsonArray("enum").map { it.asString }.toSet(),
        )
        val consumerPattern = definitions.getAsJsonObject("consumerFile")["pattern"]
            .asString.toRegex()
        features().flatMap { feature ->
            feature.getAsJsonArray("consumer_files").map { it.asString }
        }.forEach { consumer ->
            assertTrue("Schema rejected checked-in consumer $consumer", consumerPattern.matches(consumer))
        }
        INVALID_CONSUMER_PATHS.forEach { consumer ->
            assertFalse("Schema accepted invalid consumer $consumer", consumerPattern.matches(consumer))
        }
    }

    private fun features(): List<JsonObject> =
        catalog.getAsJsonArray("features").map { element -> element.asJsonObject }

    private fun String.propertyBlock(propertyName: String): String {
        val startToken = "internal val $propertyName"
        val start = indexOf(startToken)
        check(start >= 0) { "Missing contribution property $propertyName" }
        val next = indexOf("\ninternal val ", start + startToken.length)
        return substring(start, if (next >= 0) next else length)
    }

    private fun normalizedCatalogBytes(): ByteArray = Files.readString(
        catalogPath,
        StandardCharsets.UTF_8,
    ).removePrefix("\uFEFF").replace("\r\n", "\n").toByteArray(StandardCharsets.UTF_8)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun repositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { directory -> directory.parent }
            .firstOrNull { candidate -> Files.isRegularFile(candidate.resolve("settings.gradle.kts")) }
            ?: error("Could not locate repository root from $workingDirectory")
    }

    private data class FlagFeatureContract(
        val featureId: String,
        val composerFeature: GboardFlagFamilyFeature,
        val contributionPatch: Patch<*>,
        val contributionProperty: String,
        val contributionSource: String,
        val markerSource: String,
    )

    private data class SoftKeyFeatureContract(
        val featureId: String,
        val composerFeature: GboardSoftKeyFamilyFeature,
        val contributionPatch: Patch<*>,
        val contributionProperty: String,
        val contributionSource: String,
    )

    private companion object {
        const val FEATURE_ROOT =
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/"
        const val CATALOG_PATH =
            "patches/src/main/resources/gboard/gboard-port-product-catalog.json"
        const val DIGEST_PATH =
            "patches/src/main/resources/gboard/gboard-port-product-catalog.sha256"
        const val BINDINGS_PATH =
            "patches/src/main/resources/gboard/gboard-version-bindings.json"
        const val REGISTRY_PATH =
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/registry/GboardPatchRegistry.kt"
        val WEB_CLIPBOARD_RESOURCE_ROOTS = listOf(
            "patches/src/main/resources/web-clipboard-assets",
            "patches/src/main/resources/web-clipboard-res",
        )
        val INVALID_CONSUMER_PATHS = listOf(
            "../patches/src/main/kotlin/Feature.kt",
            "patches/src/main/kotlin/../Feature.kt",
            "patches/src/main/kotlin/./Feature.kt",
            "patches/src/main/kotlin//Feature.kt",
            "/patches/src/main/kotlin/Feature.kt",
            "C:/patches/src/main/kotlin/Feature.kt",
            "patches/src/test/kotlin/FeatureTest.kt",
            "patches/src/main/kotlin/**/*.kt",
        )
        val EXPECTED_MIGRATION_SCOPES = mapOf(
            "access_point_count" to "version-sensitive",
            "access_points_menu_style" to "version-sensitive",
            "add_gboard_signature_bypass" to "version-sensitive",
            "advanced_voice_typing" to "version-sensitive",
            "ai_writing_tools" to "version-sensitive",
            "backup_restore" to "version-neutral-extension",
            "change_emoji_size" to "version-sensitive",
            "clipboard_custom_character_limit" to "version-sensitive",
            "clipboard_enhancements" to "version-sensitive",
            "close_proactive_suggestions" to "version-sensitive",
            "custom_symbols" to "version-sensitive",
            "developer_options" to "version-sensitive",
            "emojis_stickers_gifs_tab_order" to "version-sensitive",
            "enable_accessibility_layout" to "version-sensitive",
            "enable_cursor_trackpad_mode" to "version-sensitive",
            "enable_inline_autofill_suggestions" to "version-sensitive",
            "enable_ocr_scan_text" to "version-sensitive",
            "enable_split_keyboard" to "version-sensitive",
            "english_qwerty_up_flick_uppercase" to "version-sensitive",
            "flow_mode_animation" to "version-sensitive",
            "floating_web_search" to "version-sensitive",
            "g_logo_on_spacebar" to "version-sensitive",
            "grammar_checker" to "version-sensitive",
            "incognito_mode_toggle" to "version-sensitive",
            "inline_suggestions" to "version-sensitive",
            "key_shape_selection" to "version-sensitive",
            "lan_ftp_server" to "version-neutral-extension",
            "latin_globe_key_ignore_interval" to "version-sensitive",
            "long_press_editing_shortcuts" to "version-sensitive",
            "package_rename" to "generic",
            "quick_insert" to "version-sensitive",
            "rounded_keyboard_panel" to "version-sensitive",
            "settings_homepage_override" to "version-sensitive",
            "simple_calculator" to "version-sensitive",
            "swipe_down_to_dismiss_keyboard" to "version-sensitive",
            "swipeable_custom_top_row" to "version-sensitive",
            "use_bluetooth_microphone" to "version-sensitive",
            "web_clipboard" to "version-sensitive",
            "zhuyin_bottom_row_key_sizes" to "version-sensitive",
            "zhuyin_quick_traditional_simplified_toggle" to "version-sensitive",
            "zhuyin_slide_input" to "version-sensitive",
        )
        val FLAG_FEATURE_CONTRACTS = listOf(
            FlagFeatureContract(
                "access_point_count",
                GboardFlagFamilyFeature.ACCESS_POINT_COUNT,
                gboardAccessPointCountFlagValuePatch,
                "gboardAccessPointCountFlagValuePatch",
                FEATURE_ROOT + "accesspointcount/GboardAccessPointCountFlagValuePatch.kt",
                FEATURE_ROOT + "accesspointcount/GboardAccessPointCountFeatureMarkerPatch.kt",
            ),
            FlagFeatureContract(
                "access_points_menu_style",
                GboardFlagFamilyFeature.ACCESS_POINTS_MENU_STYLE,
                gboardAccessPointsMenuFlagValuePatch,
                "gboardAccessPointsMenuFlagValuePatch",
                FEATURE_ROOT + "accesspointsmenu/GboardAccessPointsMenuFlagValuePatch.kt",
                FEATURE_ROOT + "accesspointsmenu/GboardAccessPointsMenuFeatureMarkerPatch.kt",
            ),
            FlagFeatureContract(
                "advanced_voice_typing",
                GboardFlagFamilyFeature.ADVANCED_VOICE_TYPING,
                gboardAdvancedVoiceFlagValuePatch,
                "gboardAdvancedVoiceFlagValuePatch",
                FEATURE_ROOT + "advancedvoice/GboardAdvancedVoiceFlagValuePatch.kt",
                FEATURE_ROOT + "advancedvoice/GboardAdvancedVoiceFeatureMarkerPatch.kt",
            ),
            FlagFeatureContract(
                "ai_writing_tools",
                GboardFlagFamilyFeature.AI_WRITING_TOOLS,
                gboardAiWritingToolsFlagValuePatch,
                "gboardAiWritingToolsFlagValuePatch",
                FEATURE_ROOT + "writingtools/GboardAiWritingToolsFlagValuePatch.kt",
                FEATURE_ROOT + "writingtools/GboardAiWritingToolsPatchConstants.kt",
            ),
            FlagFeatureContract(
                "change_emoji_size",
                GboardFlagFamilyFeature.CHANGE_EMOJI_SIZE,
                gboardEmojiSizeFlagValuePatch,
                "gboardEmojiSizeFlagValuePatch",
                FEATURE_ROOT + "emojisize/GboardEmojiSizeFlagValuePatch.kt",
                FEATURE_ROOT + "emojisize/GboardEmojiSizeFeatureMarkerPatch.kt",
            ),
            FlagFeatureContract(
                "clipboard_custom_character_limit",
                GboardFlagFamilyFeature.CLIPBOARD_CUSTOM_CHARACTER_LIMIT,
                gboardClipboardContentLimitFlagValuePatch,
                "gboardClipboardContentLimitFlagValuePatch",
                FEATURE_ROOT + "clipboardcontentlimit/GboardClipboardContentLimitFlagValuePatch.kt",
                FEATURE_ROOT + "clipboardcontentlimit/GboardClipboardContentLimitFeatureMarkerPatch.kt",
            ),
            FlagFeatureContract(
                "close_proactive_suggestions",
                GboardFlagFamilyFeature.CLOSE_PROACTIVE_SUGGESTIONS,
                gboardCloseProactiveSuggestionsFlagValuePatch,
                "gboardCloseProactiveSuggestionsFlagValuePatch",
                FEATURE_ROOT + "closeproactivesuggestions/" +
                    "GboardCloseProactiveSuggestionsFlagValuePatch.kt",
                FEATURE_ROOT + "closeproactivesuggestions/" +
                    "GboardCloseProactiveSuggestionsFeatureMarkerPatch.kt",
            ),
            FlagFeatureContract(
                "enable_accessibility_layout",
                GboardFlagFamilyFeature.ENABLE_ACCESSIBILITY_LAYOUT,
                gboardAccessibilityLayoutFlagValuePatch,
                "gboardAccessibilityLayoutFlagValuePatch",
                FEATURE_ROOT + "accessibilitylayout/GboardAccessibilityLayoutFlagValuePatch.kt",
                FEATURE_ROOT + "accessibilitylayout/GboardAccessibilityLayoutFeatureMarkerPatch.kt",
            ),
            FlagFeatureContract(
                "enable_cursor_trackpad_mode",
                GboardFlagFamilyFeature.ENABLE_CURSOR_TRACKPAD_MODE,
                gboardCursorTrackpadFlagValuePatch,
                "gboardCursorTrackpadFlagValuePatch",
                FEATURE_ROOT + "cursortrackpad/GboardCursorTrackpadFlagValuePatch.kt",
                FEATURE_ROOT + "cursortrackpad/GboardCursorTrackpadFeatureMarkerPatch.kt",
            ),
            FlagFeatureContract(
                "enable_inline_autofill_suggestions",
                GboardFlagFamilyFeature.DEVICE_INTELLIGENCE,
                gboardDeviceIntelligenceFlagValuePatch,
                "gboardDeviceIntelligenceFlagValuePatch",
                FEATURE_ROOT + "featureflags/GboardFeatureFlagsBytecodePatch.kt",
                FEATURE_ROOT + "featureflags/GboardDeviceIntelligenceFeatureMarkerPatch.kt",
            ),
            FlagFeatureContract(
                "enable_ocr_scan_text",
                GboardFlagFamilyFeature.ENABLE_OCR_SCAN_TEXT,
                gboardOcrFlagValuePatch,
                "gboardOcrFlagValuePatch",
                FEATURE_ROOT + "ocr/GboardOcrFlagValuePatch.kt",
                FEATURE_ROOT + "ocr/GboardOcrFeatureMarkerPatch.kt",
            ),
            FlagFeatureContract(
                "grammar_checker",
                GboardFlagFamilyFeature.GRAMMAR_CHECKER,
                gboardGrammarCheckerFlagValuePatch,
                "gboardGrammarCheckerFlagValuePatch",
                FEATURE_ROOT + "featureflags/GboardFeatureFlagsBytecodePatch.kt",
                FEATURE_ROOT + "featureflags/GboardGrammarCheckerFeatureMarkerPatch.kt",
            ),
            FlagFeatureContract(
                "flow_mode_animation",
                GboardFlagFamilyFeature.FLOW_MODE_ANIMATION,
                gboardFlowModeFlagValuePatch,
                "gboardFlowModeFlagValuePatch",
                FEATURE_ROOT + "flowmode/GboardFlowModeFlagValuePatch.kt",
                FEATURE_ROOT + "flowmode/GboardFlowModeFeatureMarkerPatch.kt",
            ),
            FlagFeatureContract(
                "inline_suggestions",
                GboardFlagFamilyFeature.INLINE_SUGGESTIONS,
                gboardInlineSuggestionsFlagValuePatch,
                "gboardInlineSuggestionsFlagValuePatch",
                FEATURE_ROOT + "featureflags/GboardFeatureFlagsBytecodePatch.kt",
                FEATURE_ROOT + "featureflags/GboardInlineSuggestionsFeatureMarkerPatch.kt",
            ),
            FlagFeatureContract(
                "key_shape_selection",
                GboardFlagFamilyFeature.KEY_SHAPE_SELECTION,
                gboardKeyShapeSelectionFlagValuePatch,
                "gboardKeyShapeSelectionFlagValuePatch",
                FEATURE_ROOT + "featureflags/GboardFeatureFlagsBytecodePatch.kt",
                FEATURE_ROOT + "featureflags/GboardKeyShapeSelectionFeatureMarkerPatch.kt",
            ),
            FlagFeatureContract(
                "quick_insert",
                GboardFlagFamilyFeature.QUICK_INSERT,
                gboardQuickInsertFlagValuePatch,
                "gboardQuickInsertFlagValuePatch",
                FEATURE_ROOT + "quickinsert/GboardQuickInsertFlagValuePatch.kt",
                FEATURE_ROOT + "quickinsert/GboardQuickInsertFeatureMarkerPatch.kt",
            ),
            FlagFeatureContract(
                "use_bluetooth_microphone",
                GboardFlagFamilyFeature.USE_BLUETOOTH_MICROPHONE,
                gboardBluetoothMicrophoneFlagValuePatch,
                "gboardBluetoothMicrophoneFlagValuePatch",
                FEATURE_ROOT + "bluetoothmicrophone/GboardBluetoothMicrophoneFlagValuePatch.kt",
                FEATURE_ROOT + "bluetoothmicrophone/GboardBluetoothMicrophoneFeatureMarkerPatch.kt",
            ),
        )
        val SOFT_KEY_FEATURE_CONTRACTS = listOf(
            SoftKeyFeatureContract(
                "custom_symbols",
                GboardSoftKeyFamilyFeature.CUSTOM_SYMBOLS,
                gboardZhuyinCustomSymbolsEntryPatch,
                "gboardZhuyinCustomSymbolsEntryPatch",
                FEATURE_ROOT + "addsymbols/GboardZhuyinCustomSymbolsEntryPatch.kt",
            ),
            SoftKeyFeatureContract(
                "english_qwerty_up_flick_uppercase",
                GboardSoftKeyFamilyFeature.ENGLISH_QWERTY,
                gboardEnglishQwertySoftKeyPatch,
                "gboardEnglishQwertySoftKeyPatch",
                FEATURE_ROOT + "englishqwerty/GboardEnglishQwertySoftKeyPatch.kt",
            ),
            SoftKeyFeatureContract(
                "g_logo_on_spacebar",
                GboardSoftKeyFamilyFeature.SPACEBAR_LOGO,
                gboardSpacebarLogoSoftKeyPatch,
                "gboardSpacebarLogoSoftKeyPatch",
                FEATURE_ROOT + "spacebarlogo/GboardSpacebarLogoSoftKeyPatch.kt",
            ),
            SoftKeyFeatureContract(
                "long_press_editing_shortcuts",
                GboardSoftKeyFamilyFeature.LONG_PRESS_QUICK_ACTIONS,
                gboardLongPressQuickActionsSoftKeyPatch,
                "gboardLongPressQuickActionsSoftKeyPatch",
                FEATURE_ROOT +
                    "longpressquickactions/GboardLongPressQuickActionsSoftKeyPatch.kt",
            ),
            SoftKeyFeatureContract(
                "swipeable_custom_top_row",
                GboardSoftKeyFamilyFeature.TOP_ROW_SWIPE,
                gboardTopRowSwipeSoftKeyPatch,
                "gboardTopRowSwipeSoftKeyPatch",
                FEATURE_ROOT + "toprowswipe/GboardTopRowSwipeSoftKeyPatch.kt",
            ),
            SoftKeyFeatureContract(
                "zhuyin_bottom_row_key_sizes",
                GboardSoftKeyFamilyFeature.ZHUYIN_BOTTOM_ROW,
                gboardZhuyinBottomRowWeightSoftKeyPatch,
                "gboardZhuyinBottomRowWeightSoftKeyPatch",
                FEATURE_ROOT + "zhuyinbottomrow/GboardZhuyinBottomRowWeightSoftKeyPatch.kt",
            ),
            SoftKeyFeatureContract(
                "zhuyin_quick_traditional_simplified_toggle",
                GboardSoftKeyFamilyFeature.ZHUYIN_TOGGLE,
                gboardZhuyinTraditionalSimplifiedToggleSoftKeyPatch,
                "gboardZhuyinTraditionalSimplifiedToggleSoftKeyPatch",
                FEATURE_ROOT + "zhuyintraditionalsimplifiedtoggle/" +
                    "GboardZhuyinTraditionalSimplifiedToggleSoftKeyPatch.kt",
            ),
            SoftKeyFeatureContract(
                "zhuyin_slide_input",
                GboardSoftKeyFamilyFeature.ZHUYIN_SLIDE,
                gboardZhuyinSlideSoftKeyPatch,
                "gboardZhuyinSlideSoftKeyPatch",
                FEATURE_ROOT + "zhuyinslide/GboardZhuyinSlideSoftKeyPatch.kt",
            ),
        )
    }
}
