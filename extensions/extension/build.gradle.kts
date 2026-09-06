import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.Properties
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

private data class LocalizedSettingsText(
    val type: String,
    val name: String,
    val formatted: Boolean,
    val translations: Map<String, String>,
    val quantities: List<Pair<String, Map<String, String>>>,
)

private fun Element.childElements(name: String? = null): List<Element> =
    (0 until childNodes.length)
        .map { childNodes.item(it) }
        .filter { node ->
            node.nodeType == Node.ELEMENT_NODE && (name == null || node.nodeName == name)
        }
        .map { it as Element }

private fun Element.requiredTranslations(
    entryName: String,
    requiredLocales: Set<String>,
): Map<String, String> {
    val translations = childElements("translation").associate { translation ->
        translation.getAttribute("locale") to translation.textContent
    }
    require(translations.keys == requiredLocales) {
        "$entryName must define exactly ${requiredLocales.joinToString()}"
    }
    return translations
}

private fun escapeXmlText(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

private fun decodeAndroidEscapes(value: String): String = value
    .replace("\\n", "\n")
    .replace("\\'", "'")
    .replace("\\?", "?")

private fun escapeJavaString(value: String): String = decodeAndroidEscapes(value)
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\r", "\\r")
    .replace("\n", "\\n")

extension {
    name = "extensions/gboard-patches.rve"
}

val quickJsPayloadSourceDir = layout.projectDirectory.dir("native-payload/arm64-v8a")
val generatedQuickJsPayloadDir = layout.buildDirectory.dir(
    "generated/source/quickJsPayload/java"
)
val settingsTextAuthority = layout.projectDirectory.file(
    "src/main/settings-text/gboard_settings_text.xml"
)
val generatedSettingsTextResDir = layout.buildDirectory.dir(
    "generated/settingsText/res"
)
val generatedSettingsTextJavaDir = layout.buildDirectory.dir(
    "generated/source/settingsText/java"
)
val generatedLanFtpMessagesJavaDir = layout.buildDirectory.dir(
    "generated/source/lanFtpMessages/java"
)

android {
    namespace = "dev.jason.gboardpatches.extension"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
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
        java.directories.add(generatedSettingsTextJavaDir.get().asFile.absolutePath)
        java.directories.add(generatedLanFtpMessagesJavaDir.get().asFile.absolutePath)
        res.directories.add(generatedSettingsTextResDir.get().asFile.absolutePath)
    }
}

dependencies {
    implementation(libs.apache.ftpserver.core)
    implementation(libs.apache.mina.core)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
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
    inputs.file(settingsTextAuthority)
    outputs.dir(generatedSettingsTextResDir)
    outputs.dir(generatedSettingsTextJavaDir)

    doLast {
        val requiredLocales = linkedSetOf("en", "zh-Hant")
        val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isExpandEntityReferences = false
        }
        val document = documentBuilderFactory.newDocumentBuilder()
            .parse(settingsTextAuthority.asFile)
        val root = document.documentElement
        require(root.nodeName == "settings-text") {
            "Settings text authority must use a <settings-text> root"
        }
        require(root.getAttribute("locales").split(Regex("\\s+")).toSet() == requiredLocales) {
            "Settings text authority must declare exactly ${requiredLocales.joinToString()}"
        }

        val seenNames = linkedSetOf<String>()
        val entries = root.childElements().map { entry ->
            val type = entry.nodeName
            require(type == "string" || type == "plurals") {
                "Unsupported settings text type: $type"
            }
            val name = entry.getAttribute("name")
            require(name.isNotBlank() && seenNames.add(name)) {
                "Settings text names must be non-blank and unique: $name"
            }
            if (type == "string") {
                LocalizedSettingsText(
                    type = type,
                    name = name,
                    formatted = entry.getAttribute("formatted") != "false",
                    translations = entry.requiredTranslations(name, requiredLocales),
                    quantities = emptyList(),
                )
            } else {
                val quantities = entry.childElements("item").map { item ->
                    val quantity = item.getAttribute("quantity")
                    require(quantity.isNotBlank()) { "$name has a plural item without quantity" }
                    quantity to item.requiredTranslations("$name[$quantity]", requiredLocales)
                }
                require(quantities.isNotEmpty() && quantities.map { it.first }.distinct().size == quantities.size) {
                    "$name must define unique plural quantities"
                }
                LocalizedSettingsText(
                    type = type,
                    name = name,
                    formatted = true,
                    translations = emptyMap(),
                    quantities = quantities,
                )
            }
        }
        require(entries.isNotEmpty()) { "Settings text authority must not be empty" }

        val resRoot = generatedSettingsTextResDir.get().asFile
        mapOf("en" to "values", "zh-Hant" to "values-b+zh+Hant").forEach { (locale, qualifier) ->
            val output = resRoot.resolve("$qualifier/gboard_settings_strings.xml")
            output.parentFile.mkdirs()
            output.writeText(buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                appendLine("<resources>")
                entries.forEach { entry ->
                    if (entry.type == "string") {
                        append("    <string name=\"")
                        append(entry.name)
                        if (!entry.formatted) {
                            append("\" formatted=\"false")
                        }
                        append("\">")
                        append(escapeXmlText(entry.translations.getValue(locale)))
                        appendLine("</string>")
                    } else {
                        append("    <plurals name=\"")
                        append(entry.name)
                        appendLine("\">")
                        entry.quantities.forEach { (quantity, translations) ->
                            append("        <item quantity=\"")
                            append(quantity)
                            append("\">")
                            append(escapeXmlText(translations.getValue(locale)))
                            appendLine("</item>")
                        }
                        appendLine("    </plurals>")
                    }
                }
                appendLine("</resources>")
            })
        }

        val stringEntries = entries.filter { it.type == "string" }
        val pluralEntries = entries.filter { it.type == "plurals" }
        val javaOutput = generatedSettingsTextJavaDir.get().asFile.resolve(
            "dev/jason/gboardpatches/extension/settings/GboardSettingsTextCatalog.java"
        )
        javaOutput.parentFile.mkdirs()
        javaOutput.writeText(buildString {
            appendLine("package dev.jason.gboardpatches.extension.settings;")
            appendLine()
            appendLine("import java.util.HashMap;")
            appendLine("import java.util.Map;")
            appendLine()
            appendLine("import dev.jason.gboardpatches.extension.R;")
            appendLine()
            appendLine("final class GboardSettingsTextCatalog {")
            appendLine("    private static final Map<Integer, String> ENGLISH = createEnglish();")
            appendLine("    private static final Map<Integer, String> TRADITIONAL_CHINESE =")
            appendLine("            createTraditionalChinese();")
            appendLine()
            appendLine("    private GboardSettingsTextCatalog() {")
            appendLine("    }")
            appendLine()
            appendLine("    static String template(int resId, String languageTag) {")
            appendLine("        return \"zh-Hant\".equals(languageTag)")
            appendLine("                ? TRADITIONAL_CHINESE.get(resId)")
            appendLine("                : ENGLISH.get(resId);")
            appendLine("    }")
            appendLine()
            appendLine("    static String quantityTemplate(int resId, String languageTag, int quantity) {")
            pluralEntries.forEach { entry ->
                val one = entry.quantities.firstOrNull { it.first == "one" }?.second
                    ?: entry.quantities.first().second
                val other = entry.quantities.firstOrNull { it.first == "other" }?.second
                    ?: entry.quantities.last().second
                append("        if (resId == R.plurals.")
                append(entry.name)
                appendLine(") {")
                append("            return \"zh-Hant\".equals(languageTag) ? \"")
                append(escapeJavaString(other.getValue("zh-Hant")))
                append("\" : quantity == 1 ? \"")
                append(escapeJavaString(one.getValue("en")))
                append("\" : \"")
                append(escapeJavaString(other.getValue("en")))
                appendLine("\";")
                appendLine("        }")
            }
            appendLine("        return null;")
            appendLine("    }")
            listOf(
                "English" to "en",
                "TraditionalChinese" to "zh-Hant",
            ).forEach { (methodSuffix, locale) ->
                appendLine()
                append("    private static Map<Integer, String> create")
                append(methodSuffix)
                appendLine("() {")
                appendLine("        Map<Integer, String> templates = new HashMap<Integer, String>();")
                stringEntries.forEach { entry ->
                    append("        templates.put(R.string.")
                    append(entry.name)
                    append(", \"")
                    append(escapeJavaString(entry.translations.getValue(locale)))
                    appendLine("\");")
                }
                appendLine("        return templates;")
                appendLine("    }")
            }
            appendLine("}")
        })
    }
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

tasks.named("preBuild") {
    dependsOn(generateQuickJsNativePayload)
    dependsOn(generateSettingsText)
    dependsOn(generateLanFtpMessageResource)
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
