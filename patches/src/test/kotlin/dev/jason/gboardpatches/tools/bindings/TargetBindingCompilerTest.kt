package dev.jason.gboardpatches.tools.bindings

import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetBindingCompilerTest {
    @Test
    fun `dollar members and nested descriptors remain literal Kotlin strings`() {
        val profile = """{"format":"gboard-version-bindings.v1","target_version":"18.5.4","profile_version":"2.0.0","bindings":{"callback_case":{"kind":"field","class_type":"Lsample/Outer${'$'}Nested;","field_name":"${'$'}cid","field_type":"I","confidence":1},"callback_hook":{"kind":"method","class_type":"Lsample/Outer${'$'}Nested;","method_name":"${'$'}hook","parameter_types":["[Lsample/Outer${'$'}Nested;"],"return_type":"V","confidence":1}}}"""
        val source = compileMatching(profile).kotlinSource
        val escapedDollar = "\\" + '$'
        assertTrue(source.contains("classType = \"Lsample/Outer${escapedDollar}Nested;\""))
        assertTrue(source.contains("name = \"${escapedDollar}cid\""))
        assertTrue(source.contains("name = \"${escapedDollar}hook\""))
        assertTrue(source.contains("listOf(\"[Lsample/Outer${escapedDollar}Nested;\")"))
        assertFalse(source.contains("name = \"${'$'}cid\""))
        assertEquals(source, compileMatching(profile).kotlinSource)
    }

    @Test
    fun `valid LS contract compiles to a deterministic LF projection`() {
        val profile =
            """{"format":"gboard-version-bindings.v1","target_version":"18.0.0","profile_version":"2.0.0","bindings":{"sample_parameter_type":{"kind":"type","source":{"binding":"sample_method","component":"parameter","index":0},"confidence":1},"sample_method":{"kind":"method","class_type":"Lsample/Owner;","method_name":"run","parameter_types":["I"],"return_type":"V","confidence":1}}}"""

        val projection = compileMatching(profile)

        assertEquals("18.0.0", projection.targetVersion)
        assertEquals("2.0.0", projection.profileVersion)
        assertNull(projection.projectionSha256)
        assertEquals(64, projection.sourceSha256.length)
        assertFalse(projection.kotlinSource.contains('\r'))
        assertEquals(projection, compileMatching(profile))
        assertEquals(
            """package dev.jason.gboardpatches.patches.gboard.shared.generated

import dev.jason.gboardpatches.patches.gboard.shared.GboardFieldTarget
import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget
import dev.jason.gboardpatches.patches.gboard.shared.GboardTypeTarget

internal object GboardVersionBindings {
    const val targetVersion = "18.0.0"
    const val profileVersion = "2.0.0"
    const val sourceSha256 = "4cfa24da7ff50824baed933ec3045ab988f62e29d3263be37324509afc332062"

    val `sampleMethod` = GboardMethodTarget(
        classType = "Lsample/Owner;",
        name = "run",
        parameterTypes = listOf("I"),
        returnType = "V",
    )

    val `sampleParameterType` = GboardTypeTarget("I")
}
""",
            projection.kotlinSource,
        )
    }

    @Test
    fun `JSON property and binding order do not change the generated projection`() {
        val first =
            """{"format":"gboard-version-bindings.v1","target_version":"18.0.0","profile_version":"2.0.0","bindings":{"sample_field":{"kind":"field","class_type":"Lsample/Owner;","field_name":"value","field_type":"I","confidence":1},"sample_method":{"kind":"method","class_type":"Lsample/Owner;","method_name":"run","parameter_types":[],"return_type":"V","confidence":1}}}"""
        val reordered =
            """{
                "bindings": {
                  "sample_method": {"confidence": 1.0, "return_type": "V", "parameter_types": [], "method_name": "run", "class_type": "Lsample/Owner;", "kind": "method"},
                  "sample_field": {"confidence": 1.0, "field_type": "I", "field_name": "value", "class_type": "Lsample/Owner;", "kind": "field"}
                },
                "profile_version": "2.0.0",
                "target_version": "18.0.0",
                "format": "gboard-version-bindings.v1"
            }""".trimIndent()

        assertEquals(compileMatching(first), compileMatching(reordered))
    }

    @Test
    fun `derived type roles are resolved from exact source coordinates`() {
        val profile =
            """{"format":"gboard-version-bindings.v1","target_version":"18.0.0","profile_version":"2.0.0","bindings":{"field":{"kind":"field","class_type":"Lsample/Fields;","field_name":"value","field_type":"Ljava/lang/String;","confidence":1},"field_type":{"kind":"type","source":{"binding":"field","component":"field_type"},"confidence":1},"method":{"kind":"method","class_type":"Lsample/Owner;","method_name":"run","parameter_types":["I"],"return_type":"Ljava/lang/Object;","confidence":1},"owner_type":{"kind":"type","source":{"binding":"method","component":"owner"},"confidence":1},"parameter_type":{"kind":"type","source":{"binding":"method","component":"parameter","index":0},"confidence":1},"return_type":{"kind":"type","source":{"binding":"method","component":"return"},"confidence":1}}}"""

        val source = compileMatching(profile).kotlinSource

        assertTrue(source.contains("val `fieldType` = GboardTypeTarget(\"Ljava/lang/String;\")"))
        assertTrue(source.contains("val `ownerType` = GboardTypeTarget(\"Lsample/Owner;\")"))
        assertTrue(source.contains("val `parameterType` = GboardTypeTarget(\"I\")"))
        assertTrue(source.contains("val `returnType` = GboardTypeTarget(\"Ljava/lang/Object;\")"))
    }

    @Test
    fun `derived type roles fail closed on missing source wrong kind and bad index`() {
        val profiles = listOf(
            """{"format":"gboard-version-bindings.v1","target_version":"18.0.0","profile_version":"2.0.0","bindings":{"derived":{"kind":"type","source":{"binding":"missing","component":"owner"},"confidence":1}}}""",
            """{"format":"gboard-version-bindings.v1","target_version":"18.0.0","profile_version":"2.0.0","bindings":{"derived":{"kind":"type","source":{"binding":"field","component":"parameter","index":0},"confidence":1},"field":{"kind":"field","class_type":"Lsample/Owner;","field_name":"value","field_type":"I","confidence":1}}}""",
            """{"format":"gboard-version-bindings.v1","target_version":"18.0.0","profile_version":"2.0.0","bindings":{"derived":{"kind":"type","source":{"binding":"method","component":"parameter","index":1},"confidence":1},"method":{"kind":"method","class_type":"Lsample/Owner;","method_name":"run","parameter_types":["I"],"return_type":"V","confidence":1}}}""",
        )

        profiles.forEach { profile ->
            assertThrows(IllegalArgumentException::class.java) { compileMatching(profile) }
        }
    }

    @Test
    fun `schema kind descriptors and rendered literals fail closed`() {
        val keywordProfile =
            """{"format":"gboard-version-bindings.v1","target_version":"18.0.0","profile_version":"2.0.0","bindings":{"import":{"kind":"method","class_type":"Lsample/Owner;","method_name":"run","parameter_types":[],"return_type":"V","confidence":1}}}"""
        assertTrue(compileMatching(keywordProfile).kotlinSource.contains("val `import` ="))

        val invalidProfiles = listOf(
            """{"format":"gboard-version-bindings.v1","target_version":"18.0.0","profile_version":"2.0.0","bindings":{"sample":{"kind":"method","class_type":"sample.Owner","method_name":"run","parameter_types":[],"return_type":"V","confidence":1}}}""",
            """{"format":"gboard-version-bindings.v1","target_version":"18.0.0","profile_version":"2.0.0","bindings":{"sample":{"kind":"unknown","confidence":1}}}""",
            """{"format":"gboard-version-bindings.v1","target_version":"18.0.0","profile_version":"2.0.0","bindings":{"sample":{"kind":"method","class_type":"Lsample/Owner;","method_name":"run\naway","parameter_types":[],"return_type":"V","confidence":1}}}""",
            """{"format":"gboard-version-bindings.v1","target_version":"18.0.0","profile_version":"2.0.0","extra":true,"bindings":{"sample":{"kind":"method","class_type":"Lsample/Owner;","method_name":"run","parameter_types":[],"return_type":"V","confidence":1}}}""",
        )
        invalidProfiles.forEach { profile ->
            assertThrows(IllegalArgumentException::class.java) { compileMatching(profile) }
        }
    }

    @Test
    fun `external override requires the current LS projection digest`() {
        val legacy =
            """{"format":"gboard-version-bindings.v1","target_version":"18.0.0","profile_version":"2.0.0","bindings":{"sample":{"kind":"method","class_type":"Lsample/Owner;","method_name":"run","parameter_types":[],"return_type":"V","confidence":1}}}"""
        val currentLsExport = legacy.replace(
            "\"bindings\"",
            "\"projection_sha256\":\"${"a".repeat(64)}\",\"bindings\"",
        )

        assertThrows(IllegalArgumentException::class.java) {
            compileMatching(legacy, requireProjectionSha256 = true)
        }
        val projection = compileMatching(currentLsExport, requireProjectionSha256 = true)
        assertEquals("a".repeat(64), projection.projectionSha256)
        assertTrue(
            projection.kotlinSource.contains(
                "const val projectionSha256 = \"${"a".repeat(64)}\"",
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            compileMatching(
                currentLsExport.replace("a".repeat(64), "NOT_A_DIGEST"),
                requireProjectionSha256 = true,
            )
        }
    }

    @Test
    fun `external linkage is an optional pair and required for capability-aware exports`() {
        val legacy =
            """{"format":"gboard-version-bindings.v1","target_version":"18.0.0","profile_version":"2.0.0","projection_sha256":"${"a".repeat(64)}","bindings":{"sample":{"kind":"method","class_type":"Lsample/Owner;","method_name":"run","parameter_types":[],"return_type":"V","confidence":1}}}"""
        val linked = JsonParser.parseString(legacy).asJsonObject.apply {
            addProperty("profile_sha256", "b".repeat(64))
            addProperty("port_bundle_sha256", "c".repeat(64))
        }.toString()

        assertThrows(IllegalArgumentException::class.java) {
            compileMatching(legacy, requireExternalLinkage = true)
        }
        val projection = compileMatching(linked, requireExternalLinkage = true)
        assertEquals("b".repeat(64), projection.profileSha256)
        assertEquals("c".repeat(64), projection.portBundleSha256)

        val halfLinked = JsonParser.parseString(linked).asJsonObject.apply {
            remove("port_bundle_sha256")
        }.toString()
        assertThrows(IllegalArgumentException::class.java) { compileMatching(halfLinked) }
    }

    @Test
    fun `authoritative 1854 admitted contract exactly matches sparse requirements and build output`() {
        val profile = Files.readString(BINDINGS_PATH, StandardCharsets.UTF_8)
        val profileBindings = JsonParser.parseString(profile).asJsonObject
            .getAsJsonObject("bindings")
        val fullRequirements = authoritativeRequirements()
        val requirements = TargetBindingRequirements.fromAdmission(
            requiredKeys = profileBindings.keySet(),
            bindingKinds = fullRequirements.bindingKinds,
        )
        val generated = Files.readString(GENERATED_BINDINGS_PATH, StandardCharsets.UTF_8)

        val projection = TargetBindingCompiler.compile(profile, requirements)
        assertEquals("18.5.4", projection.targetVersion)
        assertEquals(238, profileBindings.size())
        assertEquals(AUTHORITATIVE_SOURCE_SHA256, projection.sourceSha256)
        assertEquals(
            requirements.keys,
            profileBindings.keySet(),
        )
        assertEquals(projection.kotlinSource, generated)

        val missing = JsonParser.parseString(profile).asJsonObject
        missing.getAsJsonObject("bindings").remove("soft_key_bind")
        assertThrows(IllegalArgumentException::class.java) {
            TargetBindingCompiler.compile(missing.toString(), requirements)
        }
        val unexpected = JsonParser.parseString(profile).asJsonObject
        unexpected.getAsJsonObject("bindings").add(
            "unexpected_binding",
            unexpected.getAsJsonObject("bindings").get("soft_key_bind"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            TargetBindingCompiler.compile(unexpected.toString(), requirements)
        }
    }

    @Test
    fun `sidecar schema and source layout enforce one renderer`() {
        val schema = JsonParser.parseString(
            Files.readString(BINDINGS_SCHEMA_PATH, StandardCharsets.UTF_8),
        ).asJsonObject
        assertEquals(
            setOf("format", "target_version", "profile_version", "bindings"),
            schema.getAsJsonArray("required").map { it.asString }.toSet(),
        )
        assertTrue(schema.getAsJsonObject("properties").has("projection_sha256"))

        val productionSources = listOf(
            Path.of("src/main/kotlin"),
            Path.of("src/bindingCompiler/kotlin"),
        ).flatMap { root ->
            Files.walk(root).use { paths ->
                paths.filter { path -> Files.isRegularFile(path) && path.toString().endsWith(".kt") }
                    .toList()
            }
        }
        assertTrue(productionSources.none { path -> path.fileName.toString() == "GboardVersionBindings.kt" })
        val renderers = productionSources.filter { path ->
            Files.readString(path, StandardCharsets.UTF_8)
                .contains("internal object GboardVersionBindings {\\n")
        }
        assertEquals(
            listOf(
                Path.of(
                    "src/bindingCompiler/kotlin/dev/jason/gboardpatches/tools/bindings/" +
                        "TargetBindingCompiler.kt",
                ),
            ),
            renderers,
        )
        val generator = Files.readString(
            Path.of(
                "src/bindingCompiler/kotlin/dev/jason/gboardpatches/tools/bindings/" +
                    "TargetBindingGenerator.kt",
            ),
            StandardCharsets.UTF_8,
        )
        assertTrue(generator.contains("GboardTargetBuildCompiler.compile("))
        assertFalse(generator.contains("GboardMethodTarget("))

        val buildScript = Files.readString(Path.of("build.gradle.kts"), StandardCharsets.UTF_8)
        assertTrue(buildScript.contains("gboardPortBundleFile"))
        assertTrue(buildScript.contains("gboardReviewedBindingsFile"))
        assertTrue(buildScript.contains("productCatalogFile.set(gboardProductCatalog)"))
        assertTrue(buildScript.contains("admissionOutputFile"))
    }

    private fun compileMatching(
        profile: String,
        requireProjectionSha256: Boolean = false,
        requireExternalLinkage: Boolean = false,
    ): VerifiedProjection {
        val bindings = JsonParser.parseString(profile).asJsonObject.getAsJsonObject("bindings")
        val kinds = bindings.entrySet().associate { (key, value) ->
            key to value.asJsonObject.get("kind").asString
        }
        return TargetBindingCompiler.compile(
            profileText = profile,
            requirements = requirementsFor(kinds),
            requireProjectionSha256 = requireProjectionSha256,
            requireExternalLinkage = requireExternalLinkage,
        )
    }

    private fun authoritativeRequirements(): TargetBindingRequirements =
        TargetBindingRequirements.fromProductCatalog(
            Files.readString(PRODUCT_CATALOG_PATH, StandardCharsets.UTF_8),
        )

    private fun requirementsFor(kinds: Map<String, String>): TargetBindingRequirements {
        val requiredBindings = kinds.keys.sorted().joinToString(",") { key -> "\"$key\"" }
        val bindingContracts = kinds.toSortedMap().entries.joinToString(",") { (key, kind) ->
            "\"$key\":\"$kind\""
        }
        return TargetBindingRequirements.fromProductCatalog(
            """{"format":"gboard-port-product-catalog.v1","binding_contracts":{$bindingContracts},"features":[{"contributions":[{"required_bindings":[$requiredBindings]}]}]}""",
        )
    }

    private companion object {
        const val AUTHORITATIVE_SOURCE_SHA256 =
            "68e6d76bf428f2adcdf6388198faf3a54b3b446e7947de414404536afb56c7ea"
        val BINDINGS_PATH = Path.of("src/main/resources/gboard/gboard-version-bindings.json")
        val BINDINGS_SCHEMA_PATH =
            Path.of("src/main/resources/gboard/gboard-version-bindings.schema.json")
        val PRODUCT_CATALOG_PATH =
            Path.of("src/main/resources/gboard/gboard-port-product-catalog.json")
        val GENERATED_BINDINGS_PATH = Path.of(
            "build/generated/sources/versionBindings/kotlin/main/dev/jason/" +
                "gboardpatches/patches/gboard/shared/generated/GboardVersionBindings.kt",
        )
    }
}
