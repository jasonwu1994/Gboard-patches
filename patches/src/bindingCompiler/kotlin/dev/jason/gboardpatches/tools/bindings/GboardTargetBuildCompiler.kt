package dev.jason.gboardpatches.tools.bindings

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class CompiledGboardTargetBuild(
    val targetVersion: String,
    val profileVersion: String,
    val bundleSha256: String?,
    val projectionSha256: String?,
    val admittedFeatureIds: Set<String>,
    val admittedContributionIdsByFeature: Map<String, Set<String>>,
    val requiredBindingKeys: Set<String>,
    val artifactIdentity: GboardArtifactIdentity,
    val bindingsKotlinSource: String,
    val admissionKotlinSource: String,
)

data class GboardArtifactIdentity(
    val packageName: String,
    val apkFileType: String,
    val signatures: Set<String>,
    val versionNames: List<String>,
)

class GboardTargetBuildContractException(
    val code: String,
    message: String,
) : IllegalArgumentException("$code: $message")

object GboardTargetBuildCompiler {
    fun compile(
        catalogText: String,
        portBundleText: String?,
        reviewedBindingsText: String,
    ): CompiledGboardTargetBuild {
        val fullRequirements = TargetBindingRequirements.fromProductCatalog(catalogText)
        val catalog = parseObject(catalogText, "PRODUCT_CATALOG_INVALID")
        val admission = if (portBundleText == null) {
            fullAdmission(catalog)
        } else {
            compileAdmission(catalogText, catalog, portBundleText)
        }
        val requirements = TargetBindingRequirements.fromAdmission(
            requiredKeys = admission.requiredBindingKeys,
            bindingKinds = fullRequirements.bindingKinds,
        )
        val projection = try {
            TargetBindingCompiler.compile(
                profileText = reviewedBindingsText,
                requirements = requirements,
                requireProjectionSha256 = portBundleText != null,
                requireExternalLinkage = portBundleText != null,
            )
        } catch (error: IllegalArgumentException) {
            throw GboardTargetBuildContractException(
                "REVIEWED_BINDINGS_INVALID",
                error.message.orEmpty(),
            )
        }
        if (admission.targetVersion != null && projection.targetVersion != admission.targetVersion) {
            fail(
                "TARGET_VERSION_MISMATCH",
                "PortBundle target ${admission.targetVersion} != bindings ${projection.targetVersion}",
            )
        }
        if (admission.profileVersion != null && projection.profileVersion != admission.profileVersion) {
            fail(
                "PROFILE_VERSION_MISMATCH",
                "PortBundle profile ${admission.profileVersion} != bindings ${projection.profileVersion}",
            )
        }
        if (admission.projectionSha256 != null &&
            projection.projectionSha256 != admission.projectionSha256
        ) {
            fail(
                "REVIEWED_BINDINGS_PROJECTION_MISMATCH",
                "PortBundle projection ${admission.projectionSha256} != bindings " +
                    projection.projectionSha256,
            )
        }
        if (admission.profileSha256 != null && projection.profileSha256 != admission.profileSha256) {
            fail(
                "REVIEWED_BINDINGS_LINKAGE_MISMATCH",
                "PortBundle profile ${admission.profileSha256} != bindings ${projection.profileSha256}",
            )
        }
        if (admission.bundleSha256 != null &&
            projection.portBundleSha256 != admission.bundleSha256
        ) {
            fail(
                "REVIEWED_BINDINGS_LINKAGE_MISMATCH",
                "PortBundle digest ${admission.bundleSha256} != bindings " +
                    projection.portBundleSha256,
            )
        }
        return CompiledGboardTargetBuild(
            targetVersion = projection.targetVersion,
            profileVersion = projection.profileVersion,
            bundleSha256 = admission.bundleSha256,
            projectionSha256 = projection.projectionSha256,
            admittedFeatureIds = admission.contributionsByFeature.keys,
            admittedContributionIdsByFeature = admission.contributionsByFeature,
            requiredBindingKeys = admission.requiredBindingKeys,
            artifactIdentity = admission.artifactIdentity,
            bindingsKotlinSource = projection.kotlinSource,
            admissionKotlinSource = renderAdmission(
                targetVersion = projection.targetVersion,
                profileVersion = projection.profileVersion,
                bundleSha256 = admission.bundleSha256,
                projectionSha256 = projection.projectionSha256,
                artifactIdentity = admission.artifactIdentity,
                contributionsByFeature = admission.contributionsByFeature,
            ),
        )
    }

    private fun fullAdmission(catalog: JsonObject): Admission {
        val contributionsByFeature = catalog.getAsJsonArray("features")
            .associate { featureValue ->
                val feature = featureValue.asJsonObject
                feature.get("feature_id").asString to feature.getAsJsonArray("contributions")
                    .map { contribution -> contribution.asJsonObject.get("contribution_id").asString }
                    .toSortedSet()
            }
            .filterValues { it.isNotEmpty() }
            .toSortedMap()
        return Admission(
            targetVersion = null,
            profileVersion = null,
            bundleSha256 = null,
            profileSha256 = null,
            projectionSha256 = null,
            artifactIdentity = DEFAULT_ARTIFACT_IDENTITY,
            contributionsByFeature = contributionsByFeature,
            requiredBindingKeys = requiredKeys(catalog, contributionsByFeature),
        )
    }

    private fun compileAdmission(
        catalogText: String,
        catalog: JsonObject,
        bundleText: String,
    ): Admission {
        val bundle = parseObject(bundleText, "PORT_BUNDLE_INVALID")
        try {
            GboardPortBundleSchema.validate(bundle)
        } catch (error: IllegalArgumentException) {
            fail("PORT_BUNDLE_INVALID", error.message.orEmpty())
        }
        if (bundle.get("format")?.asString != "gboard-port-bundle.v1") {
            fail("PORT_BUNDLE_INVALID", "Unsupported PortBundle format")
        }
        if (bundle.get("contract_version")?.asString != "1.0.0") {
            fail("PORT_BUNDLE_INVALID", "Unsupported PortBundle contract version")
        }
        val declaredBundleSha = bundle.get("bundle_sha256")?.asString
            ?: fail("PORT_BUNDLE_INVALID", "Missing bundle_sha256")
        val body = bundle.deepCopy().apply { remove("bundle_sha256") }
        val actualBundleSha = sha256(GSON.toJson(body))
        if (declaredBundleSha != actualBundleSha) {
            fail("PORT_BUNDLE_DIGEST_MISMATCH", "$declaredBundleSha != $actualBundleSha")
        }
        val productCatalog = bundle.getAsJsonObject("product_catalog")
            ?: fail("PORT_BUNDLE_INVALID", "Missing product_catalog")
        val expectedCatalogSha = sha256(normalizeCatalog(catalogText))
        if (productCatalog.get("sha256")?.asString != expectedCatalogSha) {
            fail("PRODUCT_CATALOG_DIGEST_MISMATCH", "PortBundle does not pin this catalog")
        }
        if (productCatalog.get("catalog_version")?.asString != catalog.get("catalog_version")?.asString) {
            fail("PRODUCT_CATALOG_DIGEST_MISMATCH", "Catalog version mismatch")
        }
        val validation = bundle.getAsJsonObject("validation")
            ?: fail("PORT_BUNDLE_INVALID", "Missing validation")
        val status = validation.get("status")?.asString
        if (status !in setOf("ready", "pending")) {
            fail("PORT_BUNDLE_INVALID", "PortBundle status is $status")
        }
        val errors = validation.getAsJsonArray("errors")
            ?: fail("PORT_BUNDLE_INVALID", "Missing validation.errors")
        if (errors.size() != 0) {
            fail("PORT_BUNDLE_INVALID", "PortBundle validation errors are not empty")
        }

        val catalogByFeature = catalog.getAsJsonArray("features").associateBy { value ->
            value.asJsonObject.get("feature_id").asString
        }
        val bundleFeatures = bundle.getAsJsonArray("features")
            ?: fail("PORT_BUNDLE_INVALID", "Missing features")
        val contributionsByFeature = sortedMapOf<String, Set<String>>()
        bundleFeatures.forEach { featureValue ->
            val feature = featureValue.asJsonObject
            val featureId = feature.get("feature_id")?.asString
                ?: fail("PORT_BUNDLE_INVALID", "Feature without feature_id")
            val catalogFeature = catalogByFeature[featureId]?.asJsonObject
                ?: fail("ADMITTED_CONTRIBUTION_UNKNOWN", "Unknown feature $featureId")
            val catalogContributions = catalogFeature.getAsJsonArray("contributions")
                .associateBy { it.asJsonObject.get("contribution_id").asString }
            val admitted = feature.getAsJsonArray("admitted_contributions")
                ?: fail("PORT_BUNDLE_INVALID", "$featureId has no admitted_contributions")
            val ids = admitted.map { admittedValue ->
                val contribution = admittedValue.asJsonObject
                val contributionId = contribution.get("contribution_id")?.asString
                    ?: fail("PORT_BUNDLE_INVALID", "$featureId contribution has no id")
                val catalogContribution = catalogContributions[contributionId]
                    ?: fail(
                        "ADMITTED_CONTRIBUTION_UNKNOWN",
                        "$featureId admits unknown $contributionId",
                    )
                if (canonicalContribution(catalogContribution.asJsonObject) != contribution) {
                    fail(
                        "ADMITTED_CONTRIBUTION_DRIFT",
                        "$featureId contribution $contributionId differs from catalog",
                    )
                }
                contributionId
            }.toSortedSet()
            if (ids.isNotEmpty()) contributionsByFeature[featureId] = ids
        }
        if (bundleFeatures.size() != catalogByFeature.size ||
            bundleFeatures.map { it.asJsonObject.get("feature_id").asString }.toSet() != catalogByFeature.keys
        ) {
            fail("PORT_BUNDLE_INVALID", "PortBundle feature inventory differs from catalog")
        }
        validateFeatureDependencies(catalogByFeature.mapValues { it.value.asJsonObject }, contributionsByFeature)
        val target = bundle.getAsJsonObject("target_contract")
            ?: fail("PORT_BUNDLE_INVALID", "Missing target_contract")
        val targetApp = target.getAsJsonObject("target_app")
            ?: fail("TARGET_IDENTITY_MISMATCH", "Missing target_app identity")
        if (targetApp.get("id")?.asString != "gboard" ||
            targetApp.get("package_name")?.asString != GBOARD_PACKAGE_NAME
        ) {
            fail("TARGET_IDENTITY_MISMATCH", "PortBundle target_app is not stock Gboard")
        }
        val artifactIdentity = target.getAsJsonObject("artifact_identity")
            ?: fail("TARGET_IDENTITY_MISMATCH", "Missing artifact_identity")
        val packageNames = artifactIdentity.getAsJsonArray("package_names")
            ?.map { it.asString }
            ?.toSet()
            ?: emptySet()
        if (GBOARD_PACKAGE_NAME !in packageNames) {
            fail("TARGET_IDENTITY_MISMATCH", "Artifact packages do not contain stock Gboard")
        }
        val compatibilityIdentity = GboardArtifactIdentity(
            packageName = targetApp.get("package_name").asString,
            apkFileType = artifactIdentity.get("apk_file_type").asString,
            signatures = artifactIdentity.getAsJsonArray("signatures")
                .map { it.asString }
                .toSortedSet(),
            versionNames = artifactIdentity.getAsJsonArray("version_names")
                .map { it.asString }
                .sorted(),
        )
        val requiredBindingKeys = requiredKeys(catalog, contributionsByFeature)
        val exportedBindingKeys = target.getAsJsonArray("binding_exports")
            ?.map { bindingExport ->
                if (!bindingExport.isJsonObject) {
                    fail("PORT_BUNDLE_INVALID", "binding_exports entries must be objects")
                }
                bindingExport.asJsonObject.get("key")?.asString
                    ?: fail("PORT_BUNDLE_INVALID", "binding export without key")
            }
            ?.toSet()
            ?: fail("PORT_BUNDLE_INVALID", "Missing binding_exports")
        val missingBindingEvidence = requiredBindingKeys - exportedBindingKeys
        if (missingBindingEvidence.isNotEmpty()) {
            fail(
                "PORT_BUNDLE_BINDING_EXPORT_MISSING",
                "PortBundle lacks binding evidence ${missingBindingEvidence.sorted()}",
            )
        }
        return Admission(
            targetVersion = target.get("target_version")?.asString
                ?: fail("PORT_BUNDLE_INVALID", "Missing target_version"),
            profileVersion = target.get("profile_version")?.asString
                ?: fail("PORT_BUNDLE_INVALID", "Missing profile_version"),
            bundleSha256 = declaredBundleSha,
            profileSha256 = target.get("profile_sha256").asString,
            projectionSha256 = target.get("projection_sha256").asString,
            artifactIdentity = compatibilityIdentity,
            contributionsByFeature = contributionsByFeature,
            requiredBindingKeys = requiredBindingKeys,
        )
    }

    private fun validateFeatureDependencies(
        catalogByFeature: Map<String, JsonObject>,
        admitted: Map<String, Set<String>>,
    ) {
        admitted.keys.forEach { featureId ->
            catalogByFeature.getValue(featureId).getAsJsonArray("depends_on_feature_ids")
                ?.forEach { dependency ->
                    if (dependency.asString !in admitted) {
                        fail(
                            "ADMITTED_PUBLIC_DEPENDENCY_MISSING",
                            "$featureId requires admitted feature ${dependency.asString}",
                        )
                    }
                }
        }
    }

    private fun requiredKeys(
        catalog: JsonObject,
        admitted: Map<String, Set<String>>,
    ): Set<String> {
        val result = sortedSetOf<String>()
        catalog.getAsJsonArray("features").forEach { featureValue ->
            val feature = featureValue.asJsonObject
            val admittedIds = admitted[feature.get("feature_id").asString].orEmpty()
            feature.getAsJsonArray("contributions").forEach { contributionValue ->
                val contribution = contributionValue.asJsonObject
                if (contribution.get("contribution_id").asString in admittedIds) {
                    contribution.getAsJsonArray("required_bindings")
                        .forEach { result += it.asString }
                }
            }
        }
        return result
    }

    private fun renderAdmission(
        targetVersion: String,
        profileVersion: String,
        bundleSha256: String?,
        projectionSha256: String?,
        artifactIdentity: GboardArtifactIdentity,
        contributionsByFeature: Map<String, Set<String>>,
    ): String = buildString {
        append("package dev.jason.gboardpatches.patches.gboard.shared.generated\n\n")
        append("import app.morphe.patcher.patch.ApkFileType\n\n")
        append("internal object GboardTargetAdmission {\n")
        append("    const val targetVersion = ").append(targetVersion.kotlinLiteral()).append("\n")
        append("    const val profileVersion = ").append(profileVersion.kotlinLiteral()).append("\n")
        bundleSha256?.let {
            append("    const val bundleSha256 = ").append(it.kotlinLiteral()).append("\n")
        }
        projectionSha256?.let {
            append("    const val projectionSha256 = ").append(it.kotlinLiteral()).append("\n")
        }
        append("    const val packageName = ")
            .append(artifactIdentity.packageName.kotlinLiteral()).append("\n")
        append("    val apkFileType = ApkFileType.").append(artifactIdentity.apkFileType).append("\n")
        append("    val signatures = setOf(")
            .append(artifactIdentity.signatures.joinToString(", ") { it.kotlinLiteral() })
            .append(")\n")
        append("    val versionNames = listOf(")
            .append(artifactIdentity.versionNames.joinToString(", ") { it.kotlinLiteral() })
            .append(")\n")
        append("\n    val admittedContributionIdsByFeature: Map<String, Set<String>> = ")
        if (contributionsByFeature.isEmpty()) {
            append("emptyMap()\n")
        } else {
            append("mapOf(\n")
            contributionsByFeature.forEach { (featureId, contributionIds) ->
                append("        ").append(featureId.kotlinLiteral()).append(" to setOf(")
                append(contributionIds.joinToString(", ") { it.kotlinLiteral() })
                append("),\n")
            }
            append("    )\n")
        }
        append("\n    fun admittedContributions(featureId: String): Set<String> =\n")
        append("        admittedContributionIdsByFeature[featureId].orEmpty()\n")
        append("}\n")
    }

    private fun parseObject(text: String, code: String): JsonObject = try {
        JsonParser.parseString(text).asJsonObject
    } catch (error: RuntimeException) {
        throw GboardTargetBuildContractException(code, error.message.orEmpty())
    }

    private fun normalizeCatalog(text: String): String = text.removePrefix("\uFEFF")
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    private fun canonicalContribution(contribution: JsonObject): JsonObject =
        contribution.deepCopy().apply {
            listOf("required_bindings", "runtime_calls").forEach { name ->
                val values = getAsJsonArray(name).map { it.asString }.toSortedSet()
                add(name, GSON.toJsonTree(values))
            }
        }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun fail(code: String, message: String): Nothing =
        throw GboardTargetBuildContractException(code, message)

    private fun String.kotlinLiteral(): String = buildString {
        append('"')
        this@kotlinLiteral.forEach { character ->
            append(
                when (character) {
                    '\\' -> "\\\\"
                    '"' -> "\\\""
                    '\n' -> "\\n"
                    '\r' -> "\\r"
                    '\t' -> "\\t"
                    else -> character
                },
            )
        }
        append('"')
    }

    private data class Admission(
        val targetVersion: String?,
        val profileVersion: String?,
        val bundleSha256: String?,
        val profileSha256: String?,
        val projectionSha256: String?,
        val artifactIdentity: GboardArtifactIdentity,
        val contributionsByFeature: Map<String, Set<String>>,
        val requiredBindingKeys: Set<String>,
    )

    private val GSON = GsonBuilder().disableHtmlEscaping().serializeNulls().create()
    private const val GBOARD_PACKAGE_NAME = "com.google.android.inputmethod.latin"
    private val DEFAULT_ARTIFACT_IDENTITY = GboardArtifactIdentity(
        packageName = GBOARD_PACKAGE_NAME,
        apkFileType = "APK",
        signatures = sortedSetOf(
            "7ce83c1b71f3d572fed04c8d40c5cb10ff75e6d87d9df6fbd53f0468c2905053",
            "f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83",
        ),
        versionNames = listOf("18.5.4.993608717-release-arm64-v8a"),
    )
}
