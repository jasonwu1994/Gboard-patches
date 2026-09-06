import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.process.ExecOperations

@CacheableTask
abstract class GenerateTargetBindingsTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val profileFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productCatalogFile: RegularFileProperty

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val portBundleFile: RegularFileProperty

    @get:Classpath
    abstract val compilerClasspath: ConfigurableFileCollection

    @get:OutputFile
    abstract val bindingsOutputFile: RegularFileProperty

    @get:OutputFile
    abstract val admissionOutputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        execOperations.javaexec {
            classpath(compilerClasspath)
            mainClass.set("dev.jason.gboardpatches.tools.bindings.TargetBindingGenerator")
            args(
                profileFile.get().asFile.absolutePath,
                productCatalogFile.get().asFile.absolutePath,
                portBundleFile.orNull?.asFile?.absolutePath ?: "-",
                bindingsOutputFile.get().asFile.absolutePath,
                admissionOutputFile.get().asFile.absolutePath,
            )
        }
    }
}

group = "dev.jason.gboardpatches"

val generatedPatchInfoDir = layout.buildDirectory.dir("generated/sources/patchBuildInfo/kotlin/main")
val generatedVersionBindingsDir = layout.buildDirectory.dir("generated/sources/versionBindings/kotlin/main")
val generatedPreviewAssetsResourcesDir = layout.buildDirectory.dir("generated/resources/previewAssets/main")
val bindingCompilerSourceSet = sourceSets.create("bindingCompiler") {
    java.srcDir("src/bindingCompiler/kotlin")
}
val patchMetadataSourceSet = sourceSets.create("patchMetadata") {
    java.srcDir("src/patchMetadata/kotlin")
}
patchMetadataSourceSet.compileClasspath += sourceSets.main.get().output
patchMetadataSourceSet.runtimeClasspath += sourceSets.main.get().output
val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
val previewAssetsSourceDir = layout.projectDirectory.dir("src/main/resources/settings-previews")
val portBundleOverride = providers.gradleProperty("gboardPortBundleFile")
val versionBindingsOverride = providers.gradleProperty("gboardReviewedBindingsFile")
if (portBundleOverride.isPresent != versionBindingsOverride.isPresent) {
    throw GradleException(
        "gboardPortBundleFile and gboardReviewedBindingsFile must be provided together"
    )
}
val versionBindingsProfile = objects.fileProperty().apply {
    set(layout.projectDirectory.file("src/main/resources/gboard/gboard-version-bindings.json"))
    if (versionBindingsOverride.isPresent) {
        set(layout.file(versionBindingsOverride.map { path -> file(path) }))
    }
}
val gboardProductCatalog = layout.projectDirectory.file(
    "src/main/resources/gboard/gboard-port-product-catalog.json"
)
val syncExtensionTasks = listOf(
    project(":extensions:extension").tasks.named("syncExtension"),
    project(":extensions:calculator").tasks.named("syncExtension"),
)
val runtimeAbiOutputDirectories = syncExtensionTasks.map { task ->
    task.map { it.outputs.files.singleFile }
}
val compiledPatchClasses = layout.buildDirectory.dir("classes/kotlin/main")

configurations.named(patchMetadataSourceSet.implementationConfigurationName) {
    extendsFrom(configurations["implementation"])
}

sourceSets.test {
    compileClasspath += bindingCompilerSourceSet.output
    runtimeClasspath += bindingCompilerSourceSet.output
}

val generatePatchBuildInfo = tasks.register("generatePatchBuildInfo") {
    val outputDir = generatedPatchInfoDir
    val patchVersion = project.version.toString()

    inputs.property("patchVersion", patchVersion)
    outputs.dir(outputDir)

    doLast {
        val packageDir = outputDir.get().file("dev/jason/gboardpatches/patches/shared").asFile
        packageDir.mkdirs()

        packageDir.resolve("PatchBuildInfo.kt").writeText(
            """
            package dev.jason.gboardpatches.patches.shared

            internal object PatchBuildInfo {
                const val VERSION = "$patchVersion"
            }
            """.trimIndent()
        )
    }
}

val generatePreviewAssetsIndex = tasks.register("generatePreviewAssetsIndex") {
    val sourceDir = previewAssetsSourceDir
    val outputFile = generatedPreviewAssetsResourcesDir.map { directory ->
        directory.file("settings-previews/index.txt")
    }

    inputs.dir(sourceDir)
    outputs.file(outputFile)

    doLast {
        val sourceRoot = sourceDir.asFile
        if (!sourceRoot.exists()) {
            throw GradleException("Preview assets directory not found: $sourceRoot")
        }

        val indexedAssets = sourceRoot.walkTopDown()
            .filter { file -> file.isFile && file.name != "index.txt" }
            .map { file -> file.relativeTo(sourceRoot).invariantSeparatorsPath }
            .sorted()
            .toList()

        if (indexedAssets.isEmpty()) {
            throw GradleException("No preview assets found under $sourceRoot")
        }

        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            indexedAssets.joinToString(
                separator = System.lineSeparator(),
                postfix = System.lineSeparator()
            ),
            Charsets.UTF_8
        )
    }
}

val generateGboardVersionBindings = tasks.register<GenerateTargetBindingsTask>(
    "generateGboardVersionBindings"
) {
    val outputFile = generatedVersionBindingsDir.map { directory ->
        directory.file(
            "dev/jason/gboardpatches/patches/gboard/shared/generated/GboardVersionBindings.kt"
        )
    }
    val admissionOutputFile = generatedVersionBindingsDir.map { directory ->
        directory.file(
            "dev/jason/gboardpatches/patches/gboard/shared/generated/GboardTargetAdmission.kt"
        )
    }

    dependsOn(bindingCompilerSourceSet.classesTaskName)
    profileFile.set(versionBindingsProfile)
    productCatalogFile.set(gboardProductCatalog)
    if (portBundleOverride.isPresent) {
        portBundleFile.set(layout.file(portBundleOverride.map { path -> file(path) }))
    }
    compilerClasspath.from(bindingCompilerSourceSet.runtimeClasspath)
    bindingsOutputFile.set(outputFile)
    this.admissionOutputFile.set(admissionOutputFile)
}

patches {
    about {
        name = "Gboard Patches"
        description = "Morphe patches for Gboard."
        source = "https://github.com/jasonwu1994/gboard-patches"
        author = "Jason Wu"
        contact = "https://github.com/jasonwu1994/gboard-patches/issues"
        website = "https://github.com/jasonwu1994/gboard-patches"
        license = "GPLv3"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

sourceSets.named("main") {
    java.srcDir(generatedPatchInfoDir)
    java.srcDir(generatedVersionBindingsDir)
    resources.srcDir(generatedPreviewAssetsResourcesDir)
}

dependencies {
    implementation(libs.gson)
    add(bindingCompilerSourceSet.implementationConfigurationName, libs.gson)
    add(patchMetadataSourceSet.implementationConfigurationName, libs.gson)
    testImplementation(libs.gson)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.ow2.asm:asm-analysis:9.7.1")
    testImplementation("org.ow2.asm:asm-tree:9.7.1")
}

tasks {
    val verifyGboardCapabilityWiring = register<JavaExec>("verifyGboardCapabilityWiring") {
        description = "Fails packaging when admitted Gboard contributions are not wired."
        dependsOn("compileKotlin")
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set(
            "dev.jason.gboardpatches.patches.gboard.registry.GboardCapabilityWiringVerifier"
        )
        args(gboardProductCatalog.asFile.absolutePath)
    }

    val mppJar = named<Jar>("jar") {
        dependsOn(verifyGboardCapabilityWiring)
    }

    named<Test>("test") {
        val builtMpp = mppJar.flatMap { task -> task.archiveFile }
        dependsOn(mppJar)
        dependsOn(syncExtensionTasks)
        inputs.file(builtMpp)
        runtimeAbiOutputDirectories.forEach { directory ->
            inputs.dir(directory).withPathSensitivity(PathSensitivity.RELATIVE)
        }
        inputs.dir(compiledPatchClasses)
            .withPathSensitivity(PathSensitivity.RELATIVE)
        doFirst {
            systemProperty("gboard.test.mpp", builtMpp.get().asFile.absolutePath)
            systemProperty(
                "gboard.runtimeAbiOutputDirectories",
                runtimeAbiOutputDirectories.joinToString(File.pathSeparator) { directory ->
                    directory.get().relativeTo(projectDir).invariantSeparatorsPath
                },
            )
            systemProperty(
                "gboard.compiledPatchClasses",
                compiledPatchClasses.get().asFile
                    .relativeTo(projectDir)
                    .invariantSeparatorsPath,
            )
        }
    }

    named("compileKotlin") {
        dependsOn(generatePatchBuildInfo, generateGboardVersionBindings)
    }

    named("processResources") {
        dependsOn(generatePreviewAssetsIndex)
    }

    named("sourcesJar") {
        dependsOn(
            generatePatchBuildInfo,
            generateGboardVersionBindings,
            generatePreviewAssetsIndex
        )
    }

    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build, patchMetadataSourceSet.classesTaskName)

        classpath = patchMetadataSourceSet.runtimeClasspath
        mainClass.set("dev.jason.gboardpatches.util.PatchListGeneratorKt")
    }

    register("normalizePatchMetadataEncoding") {
        description = "Ensures generated patch metadata JSON files are encoded as UTF-8 without BOM."

        doLast {
            listOf(
                rootProject.file("patches-bundle.json"),
                rootProject.file("patches-list.json"),
            ).forEach { jsonFile ->
                if (!jsonFile.exists()) {
                    return@forEach
                }

                val bytes = jsonFile.readBytes()
                val hasUtf8Bom =
                    bytes.size >= utf8Bom.size &&
                        utf8Bom.indices.all { index -> bytes[index] == utf8Bom[index] }

                if (hasUtf8Bom) {
                    jsonFile.writeBytes(bytes.copyOfRange(utf8Bom.size, bytes.size))
                }
            }
        }
    }

    named("generatePatchesList") {
        finalizedBy("normalizePatchMetadataEncoding")
    }
    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
