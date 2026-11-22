package dev.jason.gboardpatches.patches.gboard.features.webclipboard

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

class GboardWebClipboardRuntimePreservationTest {
    private val repositoryRoot = repositoryRoot()

    @Test
    fun reviewed1853RuntimePermissionFixAndUnchangedPortalAssetsRemainPinned() {
        assertEquals(
            RUNTIME_JAVA_SHA256,
            treeDigest("extensions/extension/src/main/java/dev/jason/gboardpatches/extension/webclipboard"),
        )
        assertEquals(
            PORTAL_ASSETS_SHA256,
            treeDigest("patches/src/main/resources/web-clipboard-assets"),
        )
        assertEquals(
            RESOURCE_SHA256,
            treeDigest("patches/src/main/resources/web-clipboard-res"),
        )
        assertEquals(
            NON_CAPTURE_PATCHES_SHA256,
            filesDigest(
                listOf(
                    "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/shared/" +
                        "GboardManifestXmlUtils.kt",
                    "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/" +
                        "webclipboard/GboardWebClipboardAssetsPatch.kt",
                    "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/" +
                        "webclipboard/GboardWebClipboardFeatureMarkerPatch.kt",
                    "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/" +
                        "webclipboard/GboardWebClipboardManifestPatch.kt",
                ),
            ),
        )
        assertEquals(
            SETTINGS_RUNTIME_SHA256,
            filesDigest(
                listOf(
                    "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/" +
                        "clipboard/settings/GboardWebClipboardSettingsFeature.java",
                ),
            ),
        )
    }

    private fun treeDigest(relativeRoot: String): String {
        val root = repositoryRoot.resolve(relativeRoot)
        val files = Files.walk(root).use { paths ->
            paths.filter(Files::isRegularFile)
                .map { repositoryRoot.relativize(it).toString().replace('\\', '/') }
                .sorted()
                .toList()
        }
        return filesDigest(files)
    }

    private fun filesDigest(relativePaths: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        relativePaths.sorted().forEach { relativePath ->
            digest.update(relativePath.replace('\\', '/').toByteArray(StandardCharsets.UTF_8))
            digest.update(byteArrayOf(0))
            digest.update(normalizedContent(repositoryRoot.resolve(relativePath)))
            digest.update(byteArrayOf(0))
        }
        return digest.digest().joinToString("") { value -> "%02X".format(value) }
    }

    private fun normalizedContent(path: Path): ByteArray {
        val bytes = Files.readAllBytes(path)
        if (path.fileName.toString().substringAfterLast('.', "") !in TEXT_EXTENSIONS) {
            return bytes
        }
        return String(bytes, StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .toByteArray(StandardCharsets.UTF_8)
    }

    private fun repositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { it.parent }
            .firstOrNull { candidate ->
                Files.isRegularFile(candidate.resolve("settings.gradle.kts")) &&
                    Files.isDirectory(candidate.resolve("extensions"))
            }
            ?: error("Could not locate repository root from $workingDirectory")
    }

    private companion object {
        const val RUNTIME_JAVA_SHA256 =
            "48B77FA31D66C7DC12BCC14EECDED780D8D234973741086394CC647EFB2041E5"
        const val PORTAL_ASSETS_SHA256 =
            "DE38DBE50AD0F82EE9D2357B0EADA6680F270845A36CAB6B9A719576DD40E3AC"
        const val RESOURCE_SHA256 =
            "DE0AEBDC3D5D36AC06CEA66334E5528CAF8319122680F59761FAC4423E3C49F2"
        const val NON_CAPTURE_PATCHES_SHA256 =
            "74CA9C67297441225781568E372D9E89B494EB6EB05B351C01C9205FAFD0E7A8"
        // STRONG #4 intentionally narrows settings features from Host to FeatureHost.
        const val SETTINGS_RUNTIME_SHA256 =
            "4406B8F10478D995223CC5F92EAE4195EC04BC90FFA051BBEC867C44B63167D0"
        val TEXT_EXTENSIONS = setOf(
            "css", "html", "java", "js", "json", "kt", "svg", "txt", "xml",
        )
    }
}
