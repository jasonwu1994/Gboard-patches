package dev.jason.gboardpatches.patches.gboard.registry

import com.google.gson.JsonParser
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardTargetAdmission
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

internal object GboardCapabilityWiringVerifier {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) { "Expected product catalog path" }
        val catalog = JsonParser.parseString(
            Files.readString(Path.of(args[0]), StandardCharsets.UTF_8),
        ).asJsonObject
        val features = catalog.getAsJsonArray("features").associateBy { feature ->
            feature.asJsonObject.get("feature_id").asString
        }
        val admitted = GboardTargetAdmission.admittedContributionIdsByFeature
        val expectedPatchNames = admitted.keys.map { featureId ->
            features[featureId]?.asJsonObject?.get("public_patch_name")?.asString
                ?: error("Admitted feature $featureId is missing from the product catalog")
        }.toSet()
        val actualPatchNames = GboardPublishedPatchCatalog.morpheRegistrations
            .filter { patch -> patch.name != "Independent cover/main Resize profiles" }
            .mapNotNull { patch -> patch.name }
            .toSet()
        check(actualPatchNames == expectedPatchNames) {
            "Published capability wiring mismatch: expected=$expectedPatchNames, actual=$actualPatchNames"
        }

        val multiContributionFeatures = features.mapValues { (_, feature) ->
            feature.asJsonObject
                .getAsJsonArray("contributions")
                .map { contribution ->
                    contribution.asJsonObject.get("contribution_id").asString
                }
                .toSet()
        }.filterValues { contributionIds -> contributionIds.size > 1 }
        val admittedMultiContributionFeatures = multiContributionFeatures.keys
            .associateWith { featureId -> admitted[featureId].orEmpty() }
            .filterValues(Set<String>::isNotEmpty)
        check(
            GboardContributionWiring.registeredContributionIdsByFeature.keys ==
                admittedMultiContributionFeatures.keys,
        ) {
            "Multi-contribution feature wiring inventory mismatch: " +
                "admitted=${admittedMultiContributionFeatures.keys}, " +
                "registered=${GboardContributionWiring.registeredContributionIdsByFeature.keys}"
        }
        admittedMultiContributionFeatures.forEach { (featureId, admittedIds) ->
            val registeredIds =
                GboardContributionWiring.registeredContributionIdsByFeature.getValue(featureId)
            check(registeredIds == admittedIds) {
                "$featureId contribution wiring mismatch: admitted=$admittedIds, " +
                    "registered=$registeredIds"
            }
            val activeIds = GboardContributionWiring.activeContributionIds(featureId, admittedIds)
            check(activeIds == admittedIds) {
                "$featureId active contribution wiring mismatch: admitted=$admittedIds, " +
                    "active=$activeIds"
            }
        }
    }
}
