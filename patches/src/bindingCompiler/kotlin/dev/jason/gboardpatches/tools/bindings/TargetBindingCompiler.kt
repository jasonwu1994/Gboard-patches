package dev.jason.gboardpatches.tools.bindings

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class VerifiedProjection(
    val targetVersion: String,
    val profileVersion: String,
    val projectionSha256: String?,
    val profileSha256: String?,
    val portBundleSha256: String?,
    val sourceSha256: String,
    val kotlinSource: String,
)

class TargetBindingRequirements private constructor(
    val keys: Set<String>,
    val bindingKinds: Map<String, String>,
) {
    companion object {
        fun fromProductCatalog(catalogText: String): TargetBindingRequirements {
            val catalog = try {
                JsonParser.parseString(catalogText).asJsonObject
            } catch (error: RuntimeException) {
                throw IllegalArgumentException("Product catalog must be a JSON object", error)
            }
            require(catalog.get("format")?.asString == "gboard-port-product-catalog.v1") {
                "catalog.format must be gboard-port-product-catalog.v1"
            }
            val bindingContracts = catalog.get("binding_contracts")
            require(bindingContracts != null && bindingContracts.isJsonObject) {
                "catalog.binding_contracts must be an object"
            }
            val bindingKinds = bindingContracts.asJsonObject.entrySet().associate { (key, value) ->
                require(BINDING_NAME.matches(key)) {
                    "Catalog binding contract $key must use snake_case"
                }
                require(value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                    "Catalog binding contract $key must declare a string kind"
                }
                val kind = value.asString
                require(kind in BINDING_KINDS) {
                    "Catalog binding contract $key must be method, field, or type"
                }
                key to kind
            }.toSortedMap()
            val features = catalog.get("features")
            require(features != null && features.isJsonArray && features.asJsonArray.size() > 0) {
                "catalog.features must be a non-empty array"
            }
            val requiredKeys = linkedSetOf<String>()
            features.asJsonArray.forEachIndexed { featureIndex, featureValue ->
                require(featureValue.isJsonObject) {
                    "catalog.features[$featureIndex] must be an object"
                }
                val contributions = featureValue.asJsonObject.get("contributions")
                require(contributions != null && contributions.isJsonArray) {
                    "catalog.features[$featureIndex].contributions must be an array"
                }
                contributions.asJsonArray.forEachIndexed { contributionIndex, contributionValue ->
                    require(contributionValue.isJsonObject) {
                        "catalog.features[$featureIndex].contributions[$contributionIndex] " +
                            "must be an object"
                    }
                    val requiredBindings = contributionValue.asJsonObject.get("required_bindings")
                    require(requiredBindings != null && requiredBindings.isJsonArray) {
                        "catalog.features[$featureIndex].contributions[$contributionIndex]." +
                            "required_bindings must be an array"
                    }
                    requiredBindings.asJsonArray.forEachIndexed { bindingIndex, bindingValue ->
                        require(bindingValue.isJsonPrimitive && bindingValue.asJsonPrimitive.isString) {
                            "catalog.features[$featureIndex].contributions[$contributionIndex]." +
                                "required_bindings[$bindingIndex] must be a string"
                        }
                        val binding = bindingValue.asString
                        require(BINDING_NAME.matches(binding)) {
                            "Catalog required binding $binding must use snake_case"
                        }
                        requiredKeys += binding
                    }
                }
            }
            require(requiredKeys.isNotEmpty()) { "Catalog required binding union must not be empty" }
            require(bindingKinds.keys == requiredKeys) {
                "Catalog binding contracts must exactly match the complete required binding union"
            }
            return TargetBindingRequirements(requiredKeys.toSortedSet(), bindingKinds)
        }

        fun fromAdmission(
            requiredKeys: Set<String>,
            bindingKinds: Map<String, String>,
        ): TargetBindingRequirements = TargetBindingRequirements(
            requiredKeys.toSortedSet(),
            bindingKinds.toSortedMap(),
        )
    }
}

object TargetBindingCompiler {
    fun compile(
        profileText: String,
        requirements: TargetBindingRequirements,
        requireProjectionSha256: Boolean = false,
        requireExternalLinkage: Boolean = false,
    ): VerifiedProjection {
        val profile = try {
            JsonParser.parseString(profileText).asJsonObject
        } catch (error: RuntimeException) {
            throw IllegalArgumentException("Target binding profile must be a JSON object", error)
        }
        val projectionSha256 = profile.get("projection_sha256")?.let { value ->
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                "profile.projection_sha256 must be a string"
            }
            value.asString.also { digest ->
                require(SHA256.matches(digest)) {
                    "profile.projection_sha256 must be a lowercase SHA-256"
                }
            }
        }
        require(!requireProjectionSha256 || projectionSha256 != null) {
            "External binding export must declare profile.projection_sha256"
        }
        val profileSha256 = profile.optionalSha256("profile_sha256")
        val portBundleSha256 = profile.optionalSha256("port_bundle_sha256")
        require((profileSha256 == null) == (portBundleSha256 == null)) {
            "profile.profile_sha256 and profile.port_bundle_sha256 must be declared together"
        }
        require(!requireExternalLinkage || profileSha256 != null) {
            "External binding export must declare profile.profile_sha256 and " +
                "profile.port_bundle_sha256"
        }
        profile.requireKeys(
            path = "profile",
            expected = PROFILE_KEYS + buildSet {
                if (projectionSha256 != null) add("projection_sha256")
                if (profileSha256 != null) addAll(EXTERNAL_LINKAGE_KEYS)
            },
        )
        require(profile.string("format") == "gboard-version-bindings.v1") {
            "profile.format must be gboard-version-bindings.v1"
        }
        val targetVersion = profile.string("target_version")
        val profileVersion = profile.string("profile_version")
        require(VERSION.matches(targetVersion)) { "profile.target_version must be a dotted version" }
        require(VERSION.matches(profileVersion)) { "profile.profile_version must be semantic version" }
        val bindingObject = profile.objectValue("bindings")
        val missingBindings = requirements.keys - bindingObject.keySet()
        val unexpectedBindings = bindingObject.keySet() - requirements.keys
        require(missingBindings.isEmpty() && unexpectedBindings.isEmpty()) {
            "profile.bindings must exactly match the product catalog; " +
                "missing=${missingBindings.sorted()}, unexpected=${unexpectedBindings.sorted()}"
        }
        val bindings = bindingObject.entrySet()
            .sortedBy { (name, _) -> name }
        val propertyNames = mutableSetOf<String>()
        bindings.forEach { (name, value) ->
            require(BINDING_NAME.matches(name)) { "bindings.$name must use snake_case" }
            val propertyName = name.toCamelCase()
            require(propertyNames.add(propertyName)) {
                "bindings.$name collides after Kotlin name mapping"
            }
            val binding = try {
                value.asJsonObject
            } catch (error: RuntimeException) {
                throw IllegalArgumentException("bindings.$name must be a JSON object", error)
            }
            validateBinding(name, binding)
            require(requirements.bindingKinds[name] == binding.string("kind")) {
                "bindings.$name kind must match catalog binding contract " +
                    requirements.bindingKinds[name]
            }
        }
        val resolvedTypes = resolveTypeBindings(bindings)
        val canonicalSource = buildString {
            append(profile.string("format"))
            append('\n').append(targetVersion)
            append('\n').append(profileVersion)
            projectionSha256?.let { append('\n').append(it) }
            profileSha256?.let { append('\n').append(it) }
            portBundleSha256?.let { append('\n').append(it) }
            bindings.forEach { (name, value) ->
                val binding = value.asJsonObject
                append('\n').append(name)
                val kind = binding.string("kind")
                append('|').append(kind)
                if (kind != "type") append('|').append(binding.string("class_type"))
                when (kind) {
                    "method" -> {
                        append('|').append(binding.string("method_name"))
                        append('|').append(
                            binding.getAsJsonArray("parameter_types")
                                .joinToString(",") { it.asString }
                        )
                        append('|').append(binding.string("return_type"))
                    }
                    "field" -> {
                        append('|').append(binding.string("field_name"))
                        append('|').append(binding.string("field_type"))
                    }
                    "type" -> {
                        val source = binding.getAsJsonObject("source")
                        append('|').append(source.string("binding"))
                        append('|').append(source.string("component"))
                        if (source.has("index")) append('|').append(source.get("index").asInt)
                    }
                }
                append('|').append(
                    binding.get("confidence").asBigDecimal.stripTrailingZeros().toPlainString()
                )
            }
            append('\n')
        }
        val sourceSha256 = MessageDigest.getInstance("SHA-256")
            .digest(canonicalSource.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        val kotlinSource = renderProjection(
            targetVersion = targetVersion,
            profileVersion = profileVersion,
            projectionSha256 = projectionSha256,
            sourceSha256 = sourceSha256,
            bindings = bindings.map { (name, value) -> name to value.asJsonObject },
            bindingKinds = requirements.bindingKinds,
            resolvedTypes = resolvedTypes,
        )
        return VerifiedProjection(
            targetVersion,
            profileVersion,
            projectionSha256,
            profileSha256,
            portBundleSha256,
            sourceSha256,
            kotlinSource,
        )
    }

    private fun renderProjection(
        targetVersion: String,
        profileVersion: String,
        projectionSha256: String?,
        sourceSha256: String,
        bindings: List<Pair<String, JsonObject>>,
        bindingKinds: Map<String, String>,
        resolvedTypes: Map<String, String>,
    ): String = buildString {
        append("package dev.jason.gboardpatches.patches.gboard.shared.generated\n\n")
        append("import dev.jason.gboardpatches.patches.gboard.shared.GboardFieldTarget\n")
        append("import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget\n")
        append("import dev.jason.gboardpatches.patches.gboard.shared.GboardTypeTarget\n\n")
        append("internal object GboardVersionBindings {\n")
        append("    const val targetVersion = ").append(targetVersion.kotlinLiteral()).append("\n")
        append("    const val profileVersion = ").append(profileVersion.kotlinLiteral()).append("\n")
        projectionSha256?.let { digest ->
            append("    const val projectionSha256 = ").append(digest.kotlinLiteral()).append("\n")
        }
        append("    const val sourceSha256 = ").append(sourceSha256.kotlinLiteral()).append("\n")
        val bindingsByName = bindings.toMap()
        bindingKinds.forEach { (name, expectedKind) ->
            val binding = bindingsByName[name]
            if (binding == null) {
                append("\n    val `").append(name.toCamelCase()).append("`: ")
                append(
                    when (expectedKind) {
                        "method" -> "GboardMethodTarget"
                        "field" -> "GboardFieldTarget"
                        "type" -> "GboardTypeTarget"
                        else -> error("Validated binding contract kind")
                    },
                )
                append("\n        get() = error(")
                append("Binding $name is unavailable for this admitted target".kotlinLiteral())
                append(")\n")
                return@forEach
            }
            append("\n    val `").append(name.toCamelCase()).append("` = ")
            when (binding.string("kind")) {
                "method" -> {
                    append("GboardMethodTarget(\n")
                    append("        classType = ").append(binding.string("class_type").kotlinLiteral()).append(",\n")
                    append("        name = ").append(binding.string("method_name").kotlinLiteral()).append(",\n")
                    append("        parameterTypes = ")
                    val parameters = binding.getAsJsonArray("parameter_types").map { it.asString }
                    if (parameters.isEmpty()) {
                        append("emptyList(),\n")
                    } else {
                        append("listOf(")
                        append(parameters.joinToString(", ") { it.kotlinLiteral() })
                        append("),\n")
                    }
                    append("        returnType = ").append(binding.string("return_type").kotlinLiteral()).append(",\n")
                }
                "field" -> {
                    append("GboardFieldTarget(\n")
                    append("        classType = ").append(binding.string("class_type").kotlinLiteral()).append(",\n")
                    append("        name = ").append(binding.string("field_name").kotlinLiteral()).append(",\n")
                    append("        type = ").append(binding.string("field_type").kotlinLiteral()).append(",\n")
                }
                "type" -> {
                    append("GboardTypeTarget(")
                    append(resolvedTypes.getValue(name).kotlinLiteral())
                    append(")\n")
                }
            }
            if (binding.string("kind") != "type") append("    )\n")
        }
        append("}\n")
    }

    private fun JsonObject.string(name: String): String = get(name).asString

    private fun JsonObject.optionalSha256(name: String): String? = get(name)?.let { value ->
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            "profile.$name must be a string"
        }
        value.asString.also { digest ->
            require(SHA256.matches(digest)) { "profile.$name must be a lowercase SHA-256" }
        }
    }

    private fun validateBinding(name: String, binding: JsonObject) {
        val path = "bindings.$name"
        val kind = binding.stringValue("kind", path)
        val expectedKeys = when (kind) {
            "method" -> METHOD_KEYS
            "field" -> FIELD_KEYS
            "type" -> TYPE_KEYS
            else -> throw IllegalArgumentException("$path.kind must be method, field, or type")
        }
        binding.requireKeys(path, expectedKeys)
        if (kind != "type") {
            require(CLASS_DESCRIPTOR.matches(binding.stringValue("class_type", path))) {
                "$path.class_type must be an object descriptor"
            }
        }
        val confidence = binding.get("confidence")
        require(confidence != null && confidence.isJsonPrimitive && confidence.asJsonPrimitive.isNumber) {
            "$path.confidence must be numeric"
        }
        require(confidence.asDouble.isFinite() && confidence.asDouble in 0.0..1.0) {
            "$path.confidence must be in [0,1]"
        }
        when (kind) {
            "method" -> {
                require(MEMBER_NAME.matches(binding.stringValue("method_name", path))) {
                    "$path.method_name must be a valid dex member name"
                }
                val parameters = binding.get("parameter_types")
                require(parameters != null && parameters.isJsonArray) {
                    "$path.parameter_types must be an array"
                }
                parameters.asJsonArray.forEachIndexed { index, value ->
                    require(value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                        "$path.parameter_types[$index] must be a string"
                    }
                    require(TYPE_DESCRIPTOR.matches(value.asString) && value.asString != "V") {
                        "$path.parameter_types[$index] must be a non-void type descriptor"
                    }
                }
                require(TYPE_DESCRIPTOR.matches(binding.stringValue("return_type", path))) {
                    "$path.return_type must be a type descriptor"
                }
            }
            "field" -> {
                require(MEMBER_NAME.matches(binding.stringValue("field_name", path))) {
                    "$path.field_name must be a valid dex member name"
                }
                val type = binding.stringValue("field_type", path)
                require(TYPE_DESCRIPTOR.matches(type) && type != "V") {
                    "$path.field_type must be a non-void type descriptor"
                }
            }
            "type" -> {
                val source = binding.objectValue("source", path)
                val component = source.stringValue("component", "$path.source")
                val expectedSourceKeys = if (component == "parameter") {
                    setOf("binding", "component", "index")
                } else {
                    setOf("binding", "component")
                }
                source.requireKeys("$path.source", expectedSourceKeys)
                source.stringValue("binding", "$path.source")
                require(component in TYPE_SOURCE_COMPONENTS) {
                    "$path.source.component must be owner, parameter, return, or field_type"
                }
                if (component == "parameter") {
                    val index = source.get("index")
                    require(index != null && index.isJsonPrimitive && index.asJsonPrimitive.isNumber &&
                        index.asDouble % 1.0 == 0.0 && index.asInt >= 0) {
                        "$path.source.index must be a non-negative integer"
                    }
                }
            }
        }
    }

    private fun resolveTypeBindings(
        bindings: List<Map.Entry<String, com.google.gson.JsonElement>>,
    ): Map<String, String> {
        val byName = bindings.associate { (name, value) -> name to value.asJsonObject }
        return bindings.mapNotNull { (name, value) ->
            val binding = value.asJsonObject
            if (binding.string("kind") != "type") return@mapNotNull null
            val source = binding.getAsJsonObject("source")
            val sourceName = source.string("binding")
            val referenced = byName[sourceName]
                ?: throw IllegalArgumentException("bindings.$name.source.binding references missing $sourceName")
            val referencedKind = referenced.string("kind")
            val descriptor = when (source.string("component")) {
                "owner" -> {
                    require(referencedKind == "method" || referencedKind == "field") {
                        "bindings.$name owner source must reference a method or field"
                    }
                    referenced.string("class_type")
                }
                "parameter" -> {
                    require(referencedKind == "method") {
                        "bindings.$name parameter source must reference a method"
                    }
                    val index = source.get("index").asInt
                    val parameters = referenced.getAsJsonArray("parameter_types")
                    require(index < parameters.size()) {
                        "bindings.$name source parameter index $index is out of range"
                    }
                    parameters[index].asString
                }
                "return" -> {
                    require(referencedKind == "method") {
                        "bindings.$name return source must reference a method"
                    }
                    referenced.string("return_type")
                }
                "field_type" -> {
                    require(referencedKind == "field") {
                        "bindings.$name field_type source must reference a field"
                    }
                    referenced.string("field_type")
                }
                else -> error("Validated type source component")
            }
            require(descriptor != "V") { "bindings.$name resolves to void" }
            name to descriptor
        }.toMap()
    }

    private fun JsonObject.requireKeys(path: String, expected: Set<String>) {
        require(keySet() == expected) {
            "$path properties must be exactly ${expected.sorted()}"
        }
    }

    private fun JsonObject.stringValue(name: String, path: String): String {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            "$path.$name must be a string"
        }
        return value.asString
    }

    private fun JsonObject.objectValue(name: String): JsonObject {
        val value = get(name)
        require(value != null && value.isJsonObject) { "profile.$name must be a JSON object" }
        return value.asJsonObject
    }

    private fun JsonObject.objectValue(name: String, path: String): JsonObject {
        val value = get(name)
        require(value != null && value.isJsonObject) { "$path.$name must be a JSON object" }
        return value.asJsonObject
    }

    private fun String.kotlinLiteral(): String = buildString {
        append('"')
        this@kotlinLiteral.forEach { character ->
            append(
                when (character) {
                    '\\' -> "\\\\"
                    '"' -> "\\\""
                    '$' -> "\\\$"
                    '\n' -> "\\n"
                    '\r' -> "\\r"
                    '\t' -> "\\t"
                    else -> character
                }
            )
        }
        append('"')
    }

    private fun String.toCamelCase(): String = split('_').let { words ->
        words.first() + words.drop(1).joinToString("") { word ->
            word.replaceFirstChar { char -> char.uppercaseChar() }
        }
    }

    private val VERSION = Regex("[0-9]+(?:\\.[0-9]+){2}")
    private val SHA256 = Regex("[0-9a-f]{64}")
    private val MEMBER_NAME = Regex("(?:<init>|<clinit>|[A-Za-z_$][A-Za-z0-9_$]*)")
    private val CLASS_DESCRIPTOR = Regex("L[^.;\\[]+(?:/[^.;\\[]+)*;")
    private val TYPE_DESCRIPTOR = Regex("(?:[VZBSCIJFD]|L[^.;\\[]+(?:/[^.;\\[]+)*;|\\[+(?:[ZBSCIJFD]|L[^.;\\[]+(?:/[^.;\\[]+)*;))")
    private val METHOD_KEYS = setOf(
        "kind", "class_type", "method_name", "parameter_types", "return_type", "confidence",
    )
    private val FIELD_KEYS = setOf(
        "kind", "class_type", "field_name", "field_type", "confidence",
    )
    private val TYPE_KEYS = setOf("kind", "source", "confidence")
    private val TYPE_SOURCE_COMPONENTS = setOf("owner", "parameter", "return", "field_type")
    private val PROFILE_KEYS = setOf("format", "target_version", "profile_version", "bindings")
    private val EXTERNAL_LINKAGE_KEYS = setOf("profile_sha256", "port_bundle_sha256")
}

private val BINDING_NAME = Regex("[a-z][a-z0-9]*(?:_[a-z0-9]+)*")
private val BINDING_KINDS = setOf("method", "field", "type")
