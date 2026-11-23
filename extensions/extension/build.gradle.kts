import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.Properties
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import groovy.json.JsonSlurper
import org.w3c.dom.Element
import org.w3c.dom.Node

val extensionNamespace = "dev.jason.gboardpatches.extension"

// ---------------------------------------------------------------------------------------------
// Settings text. src/main/settings-text/values/gboard_settings_strings.xml is the English
// authority; sibling values-*/gboard_settings_strings.xml files hold per-locale translations
// (standard Android resource XML, partial translations allowed with automatic English fallback).
// Both Android string resources and the in-code catalog are generated from them.
// ---------------------------------------------------------------------------------------------

private data class SettingsStringEntry(
    val name: String,
    val formatted: Boolean,
    val english: String,
)

private data class SettingsPluralEntry(
    val name: String,
    val englishQuantities: Map<String, String>,
)

private data class ParsedSettingsLocale(
    val qualifier: String,
    val languageTag: String,
    val strings: Map<String, String>,
    val plurals: Map<String, Map<String, String>>,
)

private fun Element.childElements(name: String? = null): List<Element> =
    (0 until childNodes.length)
        .map { childNodes.item(it) }
        .filter { node ->
            node.nodeType == Node.ELEMENT_NODE && (name == null || node.nodeName == name)
        }
        .map { it as Element }

private fun canonicalizePrimaryLanguage(code: String): String = when (code.lowercase()) {
    "in" -> "id"
    "iw" -> "he"
    "ji" -> "yi"
    else -> code.lowercase()
}

private fun qualifierToLanguageTag(qualifier: String): String {
    if (qualifier == "values") {
        return "en"
    }
    require(qualifier.startsWith("values-")) {
        "Unsupported settings-text resource directory: $qualifier"
    }
    val suffix = qualifier.removePrefix("values-")
    if (suffix.startsWith("b+")) {
        val parts = suffix.removePrefix("b+").split("+").filter { it.isNotBlank() }
        require(parts.isNotEmpty()) { "Invalid BCP-47 resource directory: $qualifier" }
        val primary = canonicalizePrimaryLanguage(parts[0])
        return (listOf(primary) + parts.drop(1)).joinToString("-")
    }
    val regionMatch = Regex("^([a-zA-Z]{2,3})-r([a-zA-Z0-9]{2,3})$").matchEntire(suffix)
    if (regionMatch != null) {
        val lang = canonicalizePrimaryLanguage(regionMatch.groupValues[1])
        val region = regionMatch.groupValues[2].uppercase()
        return "$lang-$region"
    }
    val langMatch = Regex("^([a-zA-Z]{2,3})$").matchEntire(suffix)
    if (langMatch != null) {
        return canonicalizePrimaryLanguage(langMatch.groupValues[1])
    }
    throw IllegalArgumentException("Unsupported settings-text locale qualifier: $qualifier")
}

private val formatPlaceholderRegex =
    Regex("""%(?:(\d+)\$)?([-+0#]*)(\d+)?(?:\.(\d+))?([sdfoxXeEgGcCbBhH%])""")

private fun extractFormatPlaceholders(value: String): Set<Pair<Int, String>> {
    val result = linkedSetOf<Pair<Int, String>>()
    var implicitIndex = 1
    for (match in formatPlaceholderRegex.findAll(value)) {
        val conversion = match.groupValues[5]
        if (conversion == "%") {
            continue
        }
        val explicit = match.groupValues[1]
        val index = if (explicit.isNotEmpty()) explicit.toInt() else implicitIndex++
        result.add(index to conversion.lowercase())
    }
    return result
}

private fun escapeXmlText(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

private fun decodeAndroidEscapes(value: String): String = value
    .replace("\\n", "\n")
    .replace("\\'", "'")
    .replace("\\\"", "\"")
    .replace("\\?", "?")

private fun escapeJavaString(value: String): String = decodeAndroidEscapes(value)
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\r", "\\r")
    .replace("\n", "\\n")

private fun localeMethodSuffix(languageTag: String): String =
    languageTag.replace(Regex("[^A-Za-z0-9]"), "_")

extension {
    name = "extensions/gboard-patches.rve"
}

val quickJsPayloadSourceDir = layout.projectDirectory.dir("native-payload/arm64-v8a")
val generatedQuickJsPayloadDir = layout.buildDirectory.dir(
    "generated/source/quickJsPayload/java"
)
val generatedSpacebarImageDir = layout.buildDirectory.dir("generated/source/spacebarImage/java")
val settingsTextDir = layout.projectDirectory.dir("src/main/settings-text")
val generatedSettingsTextResDir = layout.buildDirectory.dir(
    "generated/settingsText/res"
)
val generatedSettingsTextJavaDir = layout.buildDirectory.dir(
    "generated/source/settingsText/java"
)
val generatedLanFtpMessagesJavaDir = layout.buildDirectory.dir(
    "generated/source/lanFtpMessages/java"
)
val generatedCustomThemeBindingsJavaDir = layout.buildDirectory.dir(
    "generated/source/customThemeBindings/java"
)
val customThemeBindingsOverride = providers.gradleProperty("gboardReviewedBindingsFile")
val customThemeBindingsAuthority = objects.fileProperty().apply {
    set(rootProject.layout.projectDirectory.file(
        "patches/src/main/resources/gboard/gboard-version-bindings.json"
    ))
    if (customThemeBindingsOverride.isPresent) {
        set(layout.file(customThemeBindingsOverride.map { path -> file(path) }))
    }
}

android {
    namespace = "dev.jason.gboardpatches.extension"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "PATCH_AUTHOR", "\"jasonwu1994\"")
        buildConfigField("String", "PATCH_AUTHOR_URL", "\"https://github.com/jasonwu1994\"")
        buildConfigField(
            "String",
            "PATCH_REPOSITORY_URL",
            "\"https://github.com/jasonwu1994/Gboard-patches\""
        )
        buildConfigField("String", "PATCH_VERSION", "\"${project.version}\"")
    }

    sourceSets.named("main") {
        java.directories.add(generatedQuickJsPayloadDir.get().asFile.absolutePath)
        java.directories.add(generatedSpacebarImageDir.get().asFile.absolutePath)
        java.directories.add(generatedSettingsTextJavaDir.get().asFile.absolutePath)
        java.directories.add(generatedLanFtpMessagesJavaDir.get().asFile.absolutePath)
        java.directories.add(generatedCustomThemeBindingsJavaDir.get().asFile.absolutePath)
        res.directories.add(generatedSettingsTextResDir.get().asFile.absolutePath)
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        checkOnly += "NewApi"
    }
}

dependencies {
    compileOnly("androidx.annotation:annotation:1.9.1")
    implementation(libs.apache.ftpserver.core)
    implementation(libs.apache.mina.core)
    implementation(libs.hidden.api.bypass)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

val generateSpacebarImage = tasks.register("generateSpacebarImage") {
    val imageFile = layout.projectDirectory.file("image-payload/google-g-color.png")
    val outputFile = generatedSpacebarImageDir.map {
        it.file("dev/jason/gboardpatches/extension/spacebarlogo/GboardSpacebarLogoImageData.java")
    }
    inputs.file(imageFile)
    outputs.file(outputFile)
    doLast {
        val bytes = imageFile.asFile.readBytes()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        check(digest == "cfd1b005ed53dc782bf7de9696a057529343ba62eb17cc7ad9fc1266732f03da") {
            "Unexpected Google color image content"
        }
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText("""
            package dev.jason.gboardpatches.extension.spacebarlogo;
            public final class GboardSpacebarLogoImageData {
                private GboardSpacebarLogoImageData() { }
                public static String base64() { return "${Base64.getEncoder().encodeToString(bytes)}"; }
            }
        """.trimIndent())
    }
}

val generateQuickJsNativePayload = tasks.register("generateQuickJsNativePayload") {
    val payloadFile = quickJsPayloadSourceDir.file("libgboardpatches_quickjs.so")
    val outputFile = generatedQuickJsPayloadDir.map { directory ->
        directory.file(
            "dev/jason/gboardpatches/extension/toprowswipe/quickjs/QuickJsNgNativePayload.java"
        )
    }

    inputs.file(payloadFile)
    outputs.file(outputFile)

    doLast {
        val sourceFile = payloadFile.asFile
        if (!sourceFile.exists()) {
            throw GradleException("QuickJS payload not found: $sourceFile")
        }

        val bytes = sourceFile.readBytes()
        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { value -> "%02x".format(value) }
        val base64 = Base64.getEncoder().encodeToString(bytes)
        val chunkSize = 8192
        val chunks = buildList {
            var index = 0
            while (index < base64.length) {
                add(base64.substring(index, minOf(index + chunkSize, base64.length)))
                index += chunkSize
            }
        }

        val renderedChunks = chunks.joinToString(",\n") { chunk ->
            "            \"$chunk\""
        }
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            """
            package dev.jason.gboardpatches.extension.toprowswipe.quickjs;

            public final class QuickJsNgNativePayload {
                private static final String ABI = "arm64-v8a";
                private static final String LIBRARY_NAME = "libgboardpatches_quickjs.so";
                private static final String SHA256 = "$sha256";
                private static final String[] BASE64_CHUNKS = new String[] {
$renderedChunks
                };

                private QuickJsNgNativePayload() {
                }

                public static String abi() {
                    return ABI;
                }

                public static String libraryName() {
                    return LIBRARY_NAME;
                }

                public static String sha256() {
                    return SHA256;
                }

                public static String base64() {
                    StringBuilder builder = new StringBuilder();
                    for (String chunk : BASE64_CHUNKS) {
                        builder.append(chunk);
                    }
                    return builder.toString();
                }
            }
            """.trimIndent()
        )
    }
}

val generateSettingsText = tasks.register("generateSettingsText") {
    inputs.dir(settingsTextDir)
    inputs.property("namespace", extensionNamespace)
    outputs.dir(generatedSettingsTextResDir)
    outputs.dir(generatedSettingsTextJavaDir)

    doLast {
        val baseDir = settingsTextDir.asFile
        val baseFile = baseDir.resolve("values/gboard_settings_strings.xml")
        require(baseFile.isFile) {
            "Missing English settings text authority at ${baseFile.absolutePath}"
        }

        val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isExpandEntityReferences = false
        }

        fun parseResourceXml(file: java.io.File): Element {
            val document = documentBuilderFactory.newDocumentBuilder().parse(file)
            val root = document.documentElement
            require(root.nodeName == "resources") {
                "${file.absolutePath} must use a <resources> root"
            }
            return root
        }

        val baseRoot = parseResourceXml(baseFile)
        val seenNames = linkedSetOf<String>()
        val stringEntries = mutableListOf<SettingsStringEntry>()
        val pluralEntries = mutableListOf<SettingsPluralEntry>()
        for (entry in baseRoot.childElements()) {
            val type = entry.nodeName
            require(type == "string" || type == "plurals") {
                "Unsupported settings text type in ${baseFile.name}: $type"
            }
            val name = entry.getAttribute("name")
            require(name.isNotBlank() && seenNames.add(name)) {
                "Settings text names must be non-blank and unique: $name"
            }
            if (type == "string") {
                stringEntries.add(
                    SettingsStringEntry(
                        name = name,
                        formatted = entry.getAttribute("formatted") != "false",
                        english = entry.textContent,
                    )
                )
            } else {
                val quantities = linkedMapOf<String, String>()
                for (item in entry.childElements("item")) {
                    val quantity = item.getAttribute("quantity")
                    require(quantity.isNotBlank() && !quantities.containsKey(quantity)) {
                        "$name must define non-blank unique plural quantities"
                    }
                    quantities[quantity] = item.textContent
                }
                require(quantities.isNotEmpty() && quantities.containsKey("other")) {
                    "$name must define at least an 'other' plural quantity"
                }
                pluralEntries.add(SettingsPluralEntry(name = name, englishQuantities = quantities))
            }
        }
        require(stringEntries.isNotEmpty()) { "English settings text authority must not be empty" }

        val stringEntryByName = stringEntries.associateBy { it.name }
        val pluralEntryByName = pluralEntries.associateBy { it.name }

        val translatedLocales = mutableListOf<ParsedSettingsLocale>()
        val seenLanguageTags = linkedSetOf("en")
        val localeDirs = (baseDir.listFiles() ?: emptyArray())
            .filter { it.isDirectory && it.name.startsWith("values-") }
            .sortedBy { it.name }

        for (dir in localeDirs) {
            val file = dir.resolve("gboard_settings_strings.xml")
            if (!file.isFile) {
                continue
            }
            val languageTag = qualifierToLanguageTag(dir.name)
            require(seenLanguageTags.add(languageTag)) {
                "Duplicate settings text language tag '$languageTag' from ${dir.name}"
            }
            val root = parseResourceXml(file)
            val strings = linkedMapOf<String, String>()
            val plurals = linkedMapOf<String, Map<String, String>>()
            val seenLocalNames = linkedSetOf<String>()
            for (entry in root.childElements()) {
                val type = entry.nodeName
                if (type != "string" && type != "plurals") {
                    continue
                }
                val name = entry.getAttribute("name")
                require(name.isNotBlank() && seenLocalNames.add(name)) {
                    "${dir.name}/${file.name} has blank or duplicate name: $name"
                }
                if (type == "string") {
                    val baseEntry = stringEntryByName[name] ?: continue
                    val value = entry.textContent
                    if (baseEntry.formatted) {
                        val expectedPlaceholders = extractFormatPlaceholders(baseEntry.english)
                        val actualPlaceholders = extractFormatPlaceholders(value)
                        require(expectedPlaceholders == actualPlaceholders) {
                            "${dir.name}/${file.name} '$name' format placeholders mismatch: " +
                                "expected $expectedPlaceholders, got $actualPlaceholders"
                        }
                    }
                    strings[name] = value
                } else {
                    val basePlural = pluralEntryByName[name] ?: continue
                    val expectedPlaceholders = extractFormatPlaceholders(
                        basePlural.englishQuantities.getValue("other")
                    )
                    val quantities = linkedMapOf<String, String>()
                    for (item in entry.childElements("item")) {
                        val quantity = item.getAttribute("quantity")
                        require(quantity.isNotBlank() && !quantities.containsKey(quantity)) {
                            "${dir.name}/${file.name} '$name' has invalid/duplicate plural quantity: $quantity"
                        }
                        val value = item.textContent
                        val actualPlaceholders = extractFormatPlaceholders(value)
                        require(expectedPlaceholders == actualPlaceholders) {
                            "${dir.name}/${file.name} '$name[$quantity]' format placeholders mismatch: " +
                                "expected $expectedPlaceholders, got $actualPlaceholders"
                        }
                        quantities[quantity] = value
                    }
                    if (quantities.isNotEmpty()) {
                        plurals[name] = quantities
                    }
                }
            }
            translatedLocales.add(
                ParsedSettingsLocale(
                    qualifier = dir.name,
                    languageTag = languageTag,
                    strings = strings,
                    plurals = plurals,
                )
            )
        }

        val resRoot = generatedSettingsTextResDir.get().asFile
        if (resRoot.exists()) {
            resRoot.deleteRecursively()
        }
        // Write English base resource file.
        val englishOut = resRoot.resolve("values/gboard_settings_strings.xml")
        englishOut.parentFile.mkdirs()
        englishOut.writeText(buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            appendLine("<resources>")
            stringEntries.forEach { entry ->
                append("    <string name=\"")
                append(entry.name)
                if (!entry.formatted) {
                    append("\" formatted=\"false")
                }
                append("\">")
                append(escapeXmlText(entry.english))
                appendLine("</string>")
            }
            pluralEntries.forEach { entry ->
                append("    <plurals name=\"")
                append(entry.name)
                appendLine("\">")
                entry.englishQuantities.forEach { (quantity, text) ->
                    append("        <item quantity=\"")
                    append(quantity)
                    append("\">")
                    append(escapeXmlText(text))
                    appendLine("</item>")
                }
                appendLine("    </plurals>")
            }
            appendLine("</resources>")
        }, StandardCharsets.UTF_8)

        // Write per-locale translated resource files (partial translations rely on Android fallback).
        translatedLocales.forEach { locale ->
            val out = resRoot.resolve("${locale.qualifier}/gboard_settings_strings.xml")
            out.parentFile.mkdirs()
            out.writeText(buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                appendLine("<resources>")
                stringEntries.forEach { entry ->
                    val translated = locale.strings[entry.name] ?: return@forEach
                    append("    <string name=\"")
                    append(entry.name)
                    if (!entry.formatted) {
                        append("\" formatted=\"false")
                    }
                    append("\">")
                    append(escapeXmlText(translated))
                    appendLine("</string>")
                }
                pluralEntries.forEach { entry ->
                    val quantities = locale.plurals[entry.name] ?: return@forEach
                    append("    <plurals name=\"")
                    append(entry.name)
                    appendLine("\">")
                    quantities.forEach { (quantity, text) ->
                        append("        <item quantity=\"")
                        append(quantity)
                        append("\">")
                        append(escapeXmlText(text))
                        appendLine("</item>")
                    }
                    appendLine("    </plurals>")
                }
                appendLine("</resources>")
            }, StandardCharsets.UTF_8)
        }

        val allLanguageTags = listOf("en") + translatedLocales.map { it.languageTag }
        val javaOutput = generatedSettingsTextJavaDir.get().asFile.resolve(
            "${extensionNamespace.replace('.', '/')}/settings/GboardSettingsTextCatalog.java"
        )
        javaOutput.parentFile.mkdirs()
        javaOutput.writeText(buildString {
            appendLine("package $extensionNamespace.settings;")
            appendLine()
            appendLine("import java.util.Collections;")
            appendLine("import java.util.HashMap;")
            appendLine("import java.util.Map;")
            appendLine("import java.util.concurrent.ConcurrentHashMap;")
            appendLine()
            appendLine("import $extensionNamespace.R;")
            appendLine()
            appendLine("final class GboardSettingsTextCatalog {")
            append("    private static final String[] SUPPORTED_LANGUAGE_TAGS = {")
            append(allLanguageTags.joinToString(", ") { "\"${escapeJavaString(it)}\"" })
            appendLine("};")
            appendLine("    private static final Map<Integer, String> ENGLISH = createLocale_en();")
            appendLine("    private static final Map<String, Map<Integer, String>> LOCALE_CACHE =")
            appendLine("            new ConcurrentHashMap<String, Map<Integer, String>>();")
            appendLine()
            appendLine("    private GboardSettingsTextCatalog() {")
            appendLine("    }")
            appendLine()
            appendLine("    static String[] supportedLanguageTags() {")
            appendLine("        return SUPPORTED_LANGUAGE_TAGS.clone();")
            appendLine("    }")
            appendLine()
            appendLine("    static boolean isSupportedLanguageTag(String languageTag) {")
            appendLine("        if (languageTag == null) {")
            appendLine("            return false;")
            appendLine("        }")
            appendLine("        for (String tag : SUPPORTED_LANGUAGE_TAGS) {")
            appendLine("            if (tag.equals(languageTag)) {")
            appendLine("                return true;")
            appendLine("            }")
            appendLine("        }")
            appendLine("        return false;")
            appendLine("    }")
            appendLine()
            appendLine("    static String template(int resId, String languageTag) {")
            appendLine("        if (languageTag != null && !\"en\".equals(languageTag)) {")
            appendLine("            Map<Integer, String> localized = templatesFor(languageTag);")
            appendLine("            String value = localized.get(resId);")
            appendLine("            if (value != null) {")
            appendLine("                return value;")
            appendLine("            }")
            appendLine("        }")
            appendLine("        return ENGLISH.get(resId);")
            appendLine("    }")
            appendLine()
            appendLine("    private static Map<Integer, String> templatesFor(String languageTag) {")
            appendLine("        Map<Integer, String> cached = LOCALE_CACHE.get(languageTag);")
            appendLine("        if (cached != null) {")
            appendLine("            return cached;")
            appendLine("        }")
            appendLine("        Map<Integer, String> created;")
            appendLine("        switch (languageTag) {")
            translatedLocales.forEach { locale ->
                val suffix = localeMethodSuffix(locale.languageTag)
                appendLine("            case \"${escapeJavaString(locale.languageTag)}\":")
                appendLine("                created = createLocale_$suffix();")
                appendLine("                break;")
            }
            appendLine("            default:")
            appendLine("                created = Collections.emptyMap();")
            appendLine("                break;")
            appendLine("        }")
            appendLine("        Map<Integer, String> existing = LOCALE_CACHE.putIfAbsent(languageTag, created);")
            appendLine("        return existing != null ? existing : created;")
            appendLine("    }")
            appendLine()
            appendLine("    static String quantityTemplate(int resId, String languageTag, int quantity) {")
            appendLine("        String category = pluralCategory(languageTag, quantity);")
            pluralEntries.forEach { entry ->
                append("        if (resId == R.plurals.")
                append(entry.name)
                appendLine(") {")
                if (translatedLocales.any { it.plurals.containsKey(entry.name) }) {
                    appendLine("            if (languageTag != null && !\"en\".equals(languageTag)) {")
                    appendLine("                switch (languageTag) {")
                    translatedLocales.forEach { locale ->
                        val quantities = locale.plurals[entry.name] ?: return@forEach
                        appendLine("                    case \"${escapeJavaString(locale.languageTag)}\":")
                        quantities.forEach { (qtyKey, text) ->
                            if (qtyKey != "other") {
                                appendLine("                        if (\"$qtyKey\".equals(category)) {")
                                appendLine("                            return \"${escapeJavaString(text)}\";")
                                appendLine("                        }")
                            }
                        }
                        val fallbackOther = quantities["other"] ?: quantities.values.first()
                        appendLine("                        return \"${escapeJavaString(fallbackOther)}\";")
                    }
                    appendLine("                    default:")
                    appendLine("                        break;")
                    appendLine("                }")
                    appendLine("            }")
                }
                val enOne = entry.englishQuantities["one"] ?: entry.englishQuantities.getValue("other")
                val enOther = entry.englishQuantities.getValue("other")
                append("            return quantity == 1 ? \"")
                append(escapeJavaString(enOne))
                append("\" : \"")
                append(escapeJavaString(enOther))
                appendLine("\";")
                appendLine("        }")
            }
            appendLine("        return null;")
            appendLine("    }")
            appendLine()
            appendLine("    private static String pluralCategory(String languageTag, int quantity) {")
            appendLine("        int abs = quantity == Integer.MIN_VALUE ? 0 : Math.abs(quantity);")
            appendLine("        if (languageTag == null) {")
            appendLine("            return abs == 1 ? \"one\" : \"other\";")
            appendLine("        }")
            appendLine("        String primary = languageTag;")
            appendLine("        int dash = primary.indexOf('-');")
            appendLine("        if (dash >= 0) {")
            appendLine("            primary = primary.substring(0, dash);")
            appendLine("        }")
            appendLine("        if (\"zh\".equals(primary) || \"ja\".equals(primary) || \"ko\".equals(primary)")
            appendLine("                || \"vi\".equals(primary) || \"id\".equals(primary) || \"th\".equals(primary)) {")
            appendLine("            return \"other\";")
            appendLine("        }")
            appendLine("        if (\"fr\".equals(primary) || \"pt\".equals(primary)) {")
            appendLine("            return (abs == 0 || abs == 1) ? \"one\" : \"other\";")
            appendLine("        }")
            appendLine("        if (\"ru\".equals(primary) || \"uk\".equals(primary)) {")
            appendLine("            int mod10 = abs % 10;")
            appendLine("            int mod100 = abs % 100;")
            appendLine("            if (mod10 == 1 && mod100 != 11) {")
            appendLine("                return \"one\";")
            appendLine("            }")
            appendLine("            if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {")
            appendLine("                return \"few\";")
            appendLine("            }")
            appendLine("            return \"many\";")
            appendLine("        }")
            appendLine("        if (\"pl\".equals(primary)) {")
            appendLine("            if (abs == 1) {")
            appendLine("                return \"one\";")
            appendLine("            }")
            appendLine("            int mod10 = abs % 10;")
            appendLine("            int mod100 = abs % 100;")
            appendLine("            if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {")
            appendLine("                return \"few\";")
            appendLine("            }")
            appendLine("            return \"many\";")
            appendLine("        }")
            appendLine("        return abs == 1 ? \"one\" : \"other\";")
            appendLine("    }")
            appendLine()
            appendLine("    private static Map<Integer, String> createLocale_en() {")
            appendLine("        Map<Integer, String> templates = new HashMap<Integer, String>(${stringEntries.size * 2});")
            stringEntries.forEach { entry ->
                append("        templates.put(R.string.")
                append(entry.name)
                append(", \"")
                append(escapeJavaString(entry.english))
                appendLine("\");")
            }
            appendLine("        return templates;")
            appendLine("    }")
            translatedLocales.forEach { locale ->
                val suffix = localeMethodSuffix(locale.languageTag)
                appendLine()
                appendLine("    private static Map<Integer, String> createLocale_$suffix() {")
                appendLine("        Map<Integer, String> templates = new HashMap<Integer, String>(${(locale.strings.size.coerceAtLeast(1)) * 2});")
                stringEntries.forEach { entry ->
                    val translated = locale.strings[entry.name] ?: return@forEach
                    append("        templates.put(R.string.")
                    append(entry.name)
                    append(", \"")
                    append(escapeJavaString(translated))
                    appendLine("\");")
                }
                appendLine("        return templates;")
                appendLine("    }")
            }
            appendLine("}")
        }, StandardCharsets.UTF_8)
    }
}

// Settings assertions use English unless a test explicitly selects another locale.
// -PupdateSettingsSearchIndex=true lets the snapshot test rewrite the packaged search index.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    maxHeapSize = "2g"
    // Independent test classes run in parallel JVMs; each JVM is recycled to bound the native
    // graphics workers that Robolectric accumulates.
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 4).coerceIn(1, 4)
    forkEvery = 150
    jvmArgs("--add-opens=java.base/java.net=ALL-UNNAMED")
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
    systemProperty(
        "gboard.updateSettingsSearchIndex",
        providers.gradleProperty("updateSettingsSearchIndex").getOrElse("false")
    )
}

val generateLanFtpMessageResource = tasks.register("generateLanFtpMessageResource") {
    val runtimeClasspath = configurations.named("releaseRuntimeClasspath")
    val outputFile = generatedLanFtpMessagesJavaDir.map { directory ->
        directory.file(
            "dev/jason/gboardpatches/extension/lanftp/runtime/LanFtpMessageResource.java"
        )
    }
    inputs.files(runtimeClasspath)
    outputs.file(outputFile)

    doLast {
        val ftpServerJar = runtimeClasspath.get().files.singleOrNull { file ->
            file.name.startsWith("ftpserver-core-") && file.extension == "jar"
        } ?: throw GradleException("Apache FTPServer core dependency was not resolved exactly once")
        val messages = Properties()
        ZipFile(ftpServerJar).use { archive ->
            val entry = archive.getEntry("org/apache/ftpserver/message/FtpStatus.properties")
                ?: throw GradleException("Apache FTPServer status messages are missing")
            archive.getInputStream(entry).use(messages::load)
        }
        val renderedMessages = messages.stringPropertyNames().sorted().joinToString("\n") { key ->
            val value = messages.getProperty(key)
            "        messages.put(\"${escapeJavaString(key)}\", " +
                "\"${escapeJavaString(value)}\");"
        }
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            """
            package dev.jason.gboardpatches.extension.lanftp.runtime;

            import java.util.Collections;
            import java.util.HashMap;
            import java.util.List;
            import java.util.Map;

            import org.apache.ftpserver.message.MessageResource;

            /** Message table embedded in dex because Morphe extensions carry classes, not JAR resources. */
            public final class LanFtpMessageResource implements MessageResource {
                private static final Map<String, String> MESSAGES = createMessages();

                private LanFtpMessageResource() {
                }

                public static MessageResource create() {
                    return new LanFtpMessageResource();
                }

                @Override
                public List<String> getAvailableLanguages() {
                    return Collections.emptyList();
                }

                @Override
                public String getMessage(int code, String subId, String language) {
                    String key = Integer.toString(code);
                    if (subId != null) {
                        String specific = MESSAGES.get(key + "." + subId);
                        if (specific != null) {
                            return specific;
                        }
                    }
                    return MESSAGES.get(key);
                }

                @Override
                public Map<String, String> getMessages(String language) {
                    return MESSAGES;
                }

                private static Map<String, String> createMessages() {
                    Map<String, String> messages = new HashMap<String, String>();
$renderedMessages
                    return Collections.unmodifiableMap(messages);
                }
            }
            """.trimIndent()
        )
    }
}

val generateCustomThemeVersionBindings = tasks.register("generateCustomThemeVersionBindings") {
    val outputFile = generatedCustomThemeBindingsJavaDir.map { directory ->
        directory.file(
            "dev/jason/gboardpatches/extension/customtheme/" +
                "GboardCustomThemeVersionBindings.java"
        )
    }
    inputs.file(customThemeBindingsAuthority)
    outputs.file(outputFile)

    doLast {
        val authority = customThemeBindingsAuthority.get().asFile
        @Suppress("UNCHECKED_CAST")
        val profile = JsonSlurper().parse(authority) as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val bindings = profile.getValue("bindings") as Map<String, Map<String, Any>>

        fun binding(name: String, kind: String): Map<String, Any> =
            bindings[name]?.also { value ->
                require(value["kind"] == kind) { "$name must be a $kind binding" }
            } ?: throw GradleException("Missing Custom Theme binding: $name")

        fun binaryClass(binding: Map<String, Any>): String {
            val descriptor = binding.getValue("class_type") as String
            require(descriptor.startsWith("L") && descriptor.endsWith(";")) {
                "Invalid Custom Theme class descriptor: $descriptor"
            }
            return descriptor.substring(1, descriptor.length - 1).replace('/', '.')
        }

        fun javaString(value: String): String = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")

        val plus = binding("custom_theme_plus_handler", "method")
        @Suppress("UNCHECKED_CAST")
        val plusParameters = plus.getValue("parameter_types") as List<String>
        fun binaryDescriptor(descriptor: String): String {
            require(descriptor.startsWith("L") && descriptor.endsWith(";")) {
                "Invalid Custom Theme type descriptor: $descriptor"
            }
            return descriptor.substring(1, descriptor.length - 1).replace('/', '.')
        }
        val result = binding("custom_theme_result_handler", "method")
        val fragmentPeer = binding("custom_theme_fragment_peer_field", "field")
        val peerContext = binding("custom_theme_peer_context_field", "field")
        val peerLauncher = binding("custom_theme_peer_launcher_field", "field")
        val peerResult = binding("custom_theme_peer_result_handler", "method")
        val launcherLaunch = binding("custom_theme_launcher_launch", "method")
        val validator = binding("custom_theme_validator", "method")
        fun parameters(binding: Map<String, Any>): List<String> {
            @Suppress("UNCHECKED_CAST")
            return binding.getValue("parameter_types") as List<String>
        }
        require(plusParameters.size == 3 && plusParameters[2] == "I" &&
            plus["return_type"] == "V") {
            "Custom Theme plus binding has an unexpected method shape"
        }
        val peerDescriptor = plusParameters[0]
        require(fragmentPeer["class_type"] == result["class_type"] &&
            fragmentPeer["field_type"] == peerDescriptor) {
            "Custom Theme fragment peer binding is inconsistent with the patched handlers"
        }
        require(listOf(peerContext, peerLauncher, peerResult).all {
            it["class_type"] == peerDescriptor
        }) {
            "Custom Theme peer bindings must share the plus-handler peer type"
        }
        require(peerContext["field_type"] == "Landroid/content/Context;" &&
            peerLauncher["field_type"] == launcherLaunch["class_type"] &&
            parameters(peerResult) == listOf("Landroid/content/Intent;") &&
            peerResult["return_type"] == "V") {
            "Custom Theme peer runtime binding shape is inconsistent"
        }
        require(parameters(result) == listOf("I", "I", "Landroid/content/Intent;") &&
            result["return_type"] == "V" &&
            parameters(launcherLaunch) == listOf(
                "Landroid/content/Intent;", "I", "Landroid/os/Bundle;"
            ) && launcherLaunch["return_type"] == "V") {
            "Custom Theme result or launcher binding has an unexpected method shape"
        }
        require(parameters(validator) == listOf("Ljava/io/File;") &&
            validator["return_type"] == "Z") {
            "Custom Theme validator binding has an unexpected method shape"
        }
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            """
            package dev.jason.gboardpatches.extension.customtheme;

            /** Generated from the reviewed Gboard target binding profile. */
            final class GboardCustomThemeVersionBindings {
                static final String TARGET_VERSION = "${javaString(profile.getValue("target_version") as String)}";
                static final String PLUS_CLASS = "${javaString(binaryClass(plus))}";
                static final String PLUS_METHOD = "${javaString(plus.getValue("method_name") as String)}";
                static final String ADAPTER_CLASS = "${javaString(binaryDescriptor(plusParameters[1]))}";
                static final String RESULT_CLASS = "${javaString(binaryClass(result))}";
                static final String RESULT_METHOD = "${javaString(result.getValue("method_name") as String)}";
                static final String FRAGMENT_PEER_FIELD = "${javaString(fragmentPeer.getValue("field_name") as String)}";
                static final String PEER_CLASS = "${javaString(binaryClass(peerContext))}";
                static final String PEER_CONTEXT_FIELD = "${javaString(peerContext.getValue("field_name") as String)}";
                static final String PEER_LAUNCHER_FIELD = "${javaString(peerLauncher.getValue("field_name") as String)}";
                static final String PEER_RESULT_METHOD = "${javaString(peerResult.getValue("method_name") as String)}";
                static final String LAUNCHER_CLASS = "${javaString(binaryClass(launcherLaunch))}";
                static final String LAUNCHER_METHOD = "${javaString(launcherLaunch.getValue("method_name") as String)}";
                static final String VALIDATOR_CLASS = "${javaString(binaryClass(validator))}";
                static final String VALIDATOR_METHOD = "${javaString(validator.getValue("method_name") as String)}";

                private GboardCustomThemeVersionBindings() {
                }
            }
            """.trimIndent()
        )
    }
}

tasks.named("preBuild") {
    dependsOn(generateSpacebarImage)
    dependsOn(generateQuickJsNativePayload)
    dependsOn(generateSettingsText)
    dependsOn(generateLanFtpMessageResource)
    dependsOn(generateCustomThemeVersionBindings)
}

tasks.named("syncExtension") {
    dependsOn("lintDebug")
}

tasks.named("syncExtension") {
    doLast {
        val extensionDex = layout.buildDirectory.file(
            "morphe/extensions/gboard-patches.rve"
        ).get().asFile
        check(extensionDex.isFile) {
            "Morphe extension dex was not generated: $extensionDex"
        }
        val dexText = String(extensionDex.readBytes(), StandardCharsets.ISO_8859_1)
        val requiredLanFtpTypes = listOf(
            "Ldev/jason/gboardpatches/extension/lanftp/android/LanFtpServerConfigSnapshot;",
            "Ldev/jason/gboardpatches/extension/lanftp/android/LanFtpServerState;",
            "Ldev/jason/gboardpatches/extension/lanftp/runtime/LanFtpServerConfig;",
            "Ldev/jason/gboardpatches/extension/lanftp/runtime/LanFtpSessionInfo;",
        )
        requiredLanFtpTypes.forEach { descriptor ->
            check(dexText.contains(descriptor)) {
                "Morphe extension dex is missing required LAN FTP type: $descriptor"
            }
        }
        check(!dexText.contains("Lcom/android/tools/r8/RecordTag;")) {
            "Morphe extension dex contains record classes without the required D8 global synthetic"
        }
        val ftpServerJar = configurations.getByName("releaseRuntimeClasspath").files
            .singleOrNull { file ->
                file.name.startsWith("ftpserver-core-") && file.extension == "jar"
            } ?: throw GradleException(
                "Apache FTPServer core dependency was not resolved exactly once"
            )
        val messageOutput = layout.buildDirectory.file(
            "morphe/lan-ftp-res/FtpStatus.properties"
        ).get().asFile
        messageOutput.parentFile.mkdirs()
        ZipFile(ftpServerJar).use { archive ->
            val entry = archive.getEntry("org/apache/ftpserver/message/FtpStatus.properties")
                ?: throw GradleException("Apache FTPServer status messages are missing")
            archive.getInputStream(entry).use { input ->
                messageOutput.outputStream().use(input::copyTo)
            }
        }
    }
}
