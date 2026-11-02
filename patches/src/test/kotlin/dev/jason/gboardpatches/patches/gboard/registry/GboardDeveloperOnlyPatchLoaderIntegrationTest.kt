package dev.jason.gboardpatches.patches.gboard.registry

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode

class GboardDeveloperOnlyPatchLoaderIntegrationTest {
    @Test
    fun `real PatchLoader and production version matcher expose only external developer options`() {
        val sourceBundle = System.getProperty("gboard.test.mpp")
            ?.let(Path::of)
            ?: throw AssertionError("Gradle did not provide the built MPP path")
        check(Files.isRegularFile(sourceBundle)) { "Built MPP does not exist: $sourceBundle" }
        val temp = Files.createTempDirectory("gboard-developer-only-loader")
        val shadows = compileDeveloperOnlyShadows(temp, sourceBundle)
        val developerOnlyBundle = temp.resolve("developer-only.mpp")
        replaceGeneratedContractClasses(
            sourceBundle,
            developerOnlyBundle,
            shadows.contractClasses,
        )

        val javaExecutable = Path.of(
            System.getProperty("java.home"),
            "bin",
            if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "java.exe" else "java",
        )
        val process = ProcessBuilder(
            javaExecutable.toString(),
            "-cp",
            listOf(
                shadows.probeClasses.toString(),
                developerOnlyBundle.toString(),
                System.getProperty("java.class.path"),
            ).joinToString(java.io.File.pathSeparator),
            "PatchLoaderProbe",
            developerOnlyBundle.toString(),
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val exitCode = process.waitFor()

        assertEquals("PatchLoader subprocess failed: $output", 0, exitCode)
        assertEquals(
            listOf(
                "Developer options",
                EXTERNAL_PACKAGE,
                "APKM",
                EXTERNAL_SIGNATURE,
                EXTERNAL_VERSION,
                "true",
                "false",
            ).joinToString("\n"),
            output,
        )
    }

    private fun compileDeveloperOnlyShadows(
        temp: Path,
        sourceBundle: Path,
    ): DeveloperOnlyShadows {
        val sources = temp.resolve("sources")
        val contractClasses = temp.resolve("contract-classes")
        val probeClasses = temp.resolve("probe-classes")
        val packagePath = sources.resolve(GENERATED_PACKAGE.replace('.', '/'))
        Files.createDirectories(packagePath)
        Files.createDirectories(contractClasses)
        Files.createDirectories(probeClasses)
        Files.writeString(
            packagePath.resolve("GboardTargetAdmission.java"),
            """
            package $GENERATED_PACKAGE;

            import app.morphe.patcher.patch.ApkFileType;
            import java.util.Collections;
            import java.util.List;
            import java.util.Set;

            public final class GboardTargetAdmission {
                public static final GboardTargetAdmission INSTANCE = new GboardTargetAdmission();

                private GboardTargetAdmission() {}

                public String getPackageName() {
                    return "$EXTERNAL_PACKAGE";
                }

                public ApkFileType getApkFileType() {
                    return ApkFileType.APKM;
                }

                public Set<String> getSignatures() {
                    return Collections.singleton("$EXTERNAL_SIGNATURE");
                }

                public List<String> getVersionNames() {
                    return Collections.singletonList("$EXTERNAL_VERSION");
                }

                public Set<String> admittedContributions(String featureId) {
                    if ("developer_options".equals(featureId)) {
                        return Collections.singleton("developer_options.resources");
                    }
                    return Collections.emptySet();
                }
            }
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
        Files.writeString(
            sources.resolve("PatchLoaderProbe.java"),
            """
            import java.io.File;
            import java.lang.reflect.Method;
            import java.util.Collections;
            import java.util.List;
            import java.util.Map;
            import java.util.Set;
            import java.util.TreeSet;

            public final class PatchLoaderProbe {
                public static void main(String[] args) throws Exception {
                    File expectedBundle = new File(args[0]).getCanonicalFile();
                    Class<?> bindings = Class.forName(
                        "$GENERATED_PACKAGE.GboardVersionBindings"
                    );
                    File actualBundle = new File(
                        bindings.getProtectionDomain().getCodeSource().getLocation().toURI()
                    ).getCanonicalFile();
                    if (!expectedBundle.equals(actualBundle)) {
                        throw new AssertionError(
                            "Bindings loaded from " + actualBundle + " instead of " + expectedBundle
                        );
                    }
                    Class<?> patchKt = Class.forName("app.morphe.patcher.patch.PatchKt");
                    Method load = patchKt.getMethod("loadPatchesFromJar", Set.class);
                    Set<?> patches = (Set<?>) load.invoke(
                        null,
                        Collections.singleton(new File(args[0]))
                    );
                    TreeSet<String> names = new TreeSet<>();
                    Object developerOptions = null;
                    for (Object patch : patches) {
                        Object name = patch.getClass().getMethod("getName").invoke(patch);
                        if (name != null) {
                            names.add((String) name);
                            if ("Developer options".equals(name)) developerOptions = patch;
                        }
                    }
                    if (developerOptions == null) throw new AssertionError("Developer options missing");
                    List<?> compatibilities = (List<?>) developerOptions.getClass()
                        .getMethod("getCompatibility").invoke(developerOptions);
                    if (compatibilities.size() != 1) {
                        throw new AssertionError("Expected exactly one compatibility");
                    }
                    Object compatibility = compatibilities.get(0);
                    String packageName = (String) compatibility.getClass()
                        .getMethod("getPackageName").invoke(compatibility);
                    Object apkFileType = compatibility.getClass()
                        .getMethod("getApkFileType").invoke(compatibility);
                    Set<?> signatures = (Set<?>) compatibility.getClass()
                        .getMethod("getSignatures").invoke(compatibility);
                    Class<?> patchUtils = Class.forName("app.morphe.patcher.patch.PatchUtilsKt");
                    Method mostCommonVersions = patchUtils.getMethod(
                        "mostCommonCompatibleVersions",
                        Set.class,
                        Set.class,
                        boolean.class
                    );
                    Map<?, ?> compatibleVersions = (Map<?, ?>) mostCommonVersions.invoke(
                        null,
                        Collections.singleton(developerOptions),
                        Collections.singleton(packageName),
                        false
                    );
                    Map<?, ?> gboardVersions = (Map<?, ?>) compatibleVersions.get(packageName);
                    if (gboardVersions == null) {
                        throw new AssertionError("Production matcher omitted " + packageName);
                    }
                    TreeSet<String> versions = new TreeSet<>();
                    for (Object version : gboardVersions.keySet()) {
                        versions.add((String) version);
                    }
                    boolean exactMatch = gboardVersions.containsKey("$EXTERNAL_VERSION");
                    boolean staleMatch = gboardVersions.containsKey(
                        "18.0.2.950000000-release-arm64-v8a"
                    );
                    System.out.print(String.join("\n",
                        String.join("\n", names),
                        packageName,
                        apkFileType.toString(),
                        String.join(",", new TreeSet<String>((Set<String>) signatures)),
                        String.join(",", versions),
                        Boolean.toString(exactMatch),
                        Boolean.toString(staleMatch)
                    ));
                }
            }
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: throw AssertionError("A JDK compiler is required")
        val contractResult = compiler.run(
            null,
            null,
            null,
            "--release",
            BUNDLE_JAVA_RELEASE,
            "-d",
            contractClasses.toString(),
            "-classpath",
            System.getProperty("java.class.path"),
            packagePath.resolve("GboardTargetAdmission.java").toString(),
        )
        assertEquals("Shadow admission compilation failed", 0, contractResult)
        val probeResult = compiler.run(
            null,
            null,
            null,
            "--release",
            BUNDLE_JAVA_RELEASE,
            "-d",
            probeClasses.toString(),
            sources.resolve("PatchLoaderProbe.java").toString(),
        )
        assertEquals("PatchLoader probe compilation failed", 0, probeResult)
        writeUnavailableBindingsShadow(sourceBundle, contractClasses)
        return DeveloperOnlyShadows(contractClasses, probeClasses)
    }

    private fun writeUnavailableBindingsShadow(sourceBundle: Path, classes: Path) {
        val originalBytes = JarFile(sourceBundle.toFile()).use { bundle ->
            val entry = bundle.getJarEntry(VERSION_BINDINGS_ENTRY)
                ?: throw AssertionError("MPP is missing $VERSION_BINDINGS_ENTRY")
            bundle.getInputStream(entry).use { it.readBytes() }
        }
        val original = ClassNode()
        ClassReader(originalBytes).accept(original, 0)
        val getters = original.methods.filter { method ->
            method.access and Opcodes.ACC_PUBLIC != 0 &&
                method.access and Opcodes.ACC_STATIC == 0 &&
                method.name.startsWith("get") &&
                Type.getArgumentTypes(method.desc).isEmpty()
        }
        check(getters.isNotEmpty()) { "Generated binding ABI has no public getters" }
        val originalGetterAbi = getters.map { getter ->
            Triple(getter.name, getter.desc, getter.access)
        }
        getters.forEach { getter ->
            clearMethodBody(getter)
            getter.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException")
            getter.visitInsn(Opcodes.DUP)
            getter.visitLdcInsn("Binding ${getter.name.removePrefix("get")} is unavailable")
            getter.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/lang/IllegalStateException",
                "<init>",
                "(Ljava/lang/String;)V",
                false,
            )
            getter.visitInsn(Opcodes.ATHROW)
        }
        val classInitializer = original.methods.single { method -> method.name == "<clinit>" }
        clearMethodBody(classInitializer)
        classInitializer.visitTypeInsn(Opcodes.NEW, original.name)
        classInitializer.visitInsn(Opcodes.DUP)
        classInitializer.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            original.name,
            "<init>",
            "()V",
            false,
        )
        classInitializer.visitFieldInsn(
            Opcodes.PUTSTATIC,
            original.name,
            "INSTANCE",
            "L${original.name};",
        )
        classInitializer.visitInsn(Opcodes.RETURN)

        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        original.accept(writer)
        val shadowBytes = writer.toByteArray()
        val shadow = ClassNode()
        ClassReader(shadowBytes).accept(
            shadow,
            ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
        )
        val shadowGetterAbi = shadow.methods.filter { method ->
            method.access and Opcodes.ACC_PUBLIC != 0 &&
                method.access and Opcodes.ACC_STATIC == 0 &&
                method.name.startsWith("get") &&
                Type.getArgumentTypes(method.desc).isEmpty()
        }.map { getter -> Triple(getter.name, getter.desc, getter.access) }
        assertEquals("Unavailable shadow changed the generated getter ABI", originalGetterAbi, shadowGetterAbi)

        val output = classes.resolve(VERSION_BINDINGS_ENTRY)
        Files.createDirectories(output.parent)
        Files.write(output, shadowBytes)
    }

    private fun clearMethodBody(method: org.objectweb.asm.tree.MethodNode) {
        method.instructions.clear()
        method.tryCatchBlocks.clear()
        method.localVariables?.clear()
        method.visibleLocalVariableAnnotations?.clear()
        method.invisibleLocalVariableAnnotations?.clear()
    }

    private fun replaceGeneratedContractClasses(
        sourceBundle: Path,
        outputBundle: Path,
        shadowClasses: Path,
    ) {
        val replacements = REPLACED_CLASSES.associateWith { entryName ->
            Files.readAllBytes(shadowClasses.resolve(entryName))
        }
        JarFile(sourceBundle.toFile()).use { input ->
            JarOutputStream(Files.newOutputStream(outputBundle)).use { output ->
                val entries = input.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name in replacements) continue
                    output.putNextEntry(java.util.jar.JarEntry(entry.name))
                    if (!entry.isDirectory) input.getInputStream(entry).use { it.copyTo(output) }
                    output.closeEntry()
                }
                replacements.forEach { (entryName, bytes) ->
                    output.putNextEntry(java.util.jar.JarEntry(entryName))
                    output.write(bytes)
                    output.closeEntry()
                }
            }
        }
    }

    private companion object {
        const val GENERATED_PACKAGE =
            "dev.jason.gboardpatches.patches.gboard.shared.generated"
        const val EXTERNAL_PACKAGE = "com.google.android.inputmethod.latin"
        const val EXTERNAL_SIGNATURE =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val EXTERNAL_VERSION = "18.0.3.123456789-release-arm64-v8a"

        // Patcher 2.1 rejects bundles with classes newer than Java 17, the Morphe Manager limit,
        // so shadows written into the bundle must not follow the test JDK's default target.
        const val BUNDLE_JAVA_RELEASE = "17"
        val VERSION_BINDINGS_ENTRY =
            GENERATED_PACKAGE.replace('.', '/') + "/GboardVersionBindings.class"
        val REPLACED_CLASSES = setOf(
            GENERATED_PACKAGE.replace('.', '/') + "/GboardTargetAdmission.class",
            VERSION_BINDINGS_ENTRY,
        )
    }

    private data class DeveloperOnlyShadows(
        val contractClasses: Path,
        val probeClasses: Path,
    )
}
