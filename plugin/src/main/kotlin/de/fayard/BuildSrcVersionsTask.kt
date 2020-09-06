package de.fayard

import de.fayard.VersionsOnlyMode.GRADLE_PROPERTIES
import de.fayard.VersionsOnlyMode.KOTLIN_OBJECT
import de.fayard.internal.BuildSrcVersionsExtensionImpl
import de.fayard.internal.Dependency
import de.fayard.internal.DependencyGraph
import de.fayard.internal.EditorConfig
import de.fayard.internal.KotlinPoetry
import de.fayard.internal.OutputFile
import de.fayard.internal.PluginConfig
import de.fayard.internal.UpdateGradleProperties
import de.fayard.internal.UpdateVersionsOnly.regenerateBuildFile
import de.fayard.internal.kotlinpoet
import de.fayard.internal.parseGraph
import de.fayard.internal.sortedBeautifullyBy
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.kotlin.dsl.getByType
import java.io.File

@Suppress("UnstableApiUsage")
open class BuildSrcVersionsTask : DefaultTask() {

    @Input
    @Option(description = "Update all versions, I will check git diff afterwards")
    var update: Boolean = false

    @Input
    @Optional
    @Option(description = "Tabs or Spaces?")
    var indent: String? = null

    @TaskAction
    fun taskActionInitializeBuildSrc() {
        val extension: BuildSrcVersionsExtensionImpl = extension()
        if (extension.shouldInitializeBuildSrc().not()) return

        project.file(OutputFile.OUTPUTDIR.path).also {
            if (it.isDirectory.not()) it.mkdirs()
        }
        for (output in OutputFile.values()) {
            output.existed = output.fileExists(project)
        }
        val initializationMap = mapOf(
            OutputFile.BUILD to PluginConfig.INITIAL_BUILD_GRADLE_KTS,
            OutputFile.GIT_IGNORE to PluginConfig.INITIAL_GITIGNORE
        )
        for ((outputFile, initialContent) in initializationMap) {
            if (outputFile.existed.not()) {
                project.file(outputFile.path).writeText(initialContent)
                OutputFile.logFileWasModified(outputFile.path, outputFile.existed)
            }
        }
    }


    @TaskAction
    fun taskActionUpdateBuildSrc() {
        val extension: BuildSrcVersionsExtensionImpl = extension()
        val outputDir = project.file(OutputFile.OUTPUTDIR.path)
        val shouldGenerateLibsKt = when(extension.versionsOnlyMode) {
            null -> true
            KOTLIN_OBJECT -> false
            else -> return
        }
        val versions = unsortedParsedDependencies.sortedBeautifullyBy(extension.orderBy) { it.versionName }

        val kotlinPoetry: KotlinPoetry = kotlinpoet(versions, dependencyGraph.gradle, extension, computeIndent())

        if (shouldGenerateLibsKt) {
            kotlinPoetry.Libs.writeTo(outputDir)
            OutputFile.logFileWasModified(OutputFile.LIBS.path, OutputFile.LIBS.existed)
        }

        if (PluginConfig.useRefreshVersions.not()) {
            kotlinPoetry.Versions.writeTo(outputDir)
            OutputFile.logFileWasModified(OutputFile.VERSIONS.path, OutputFile.VERSIONS.existed)
        }

        val renamedVersionsKt: File? = when(extension.versionsOnlyMode to extension.versionsOnlyFile) {
            null to null -> null
            KOTLIN_OBJECT to null -> null
            else -> project.file(extension.versionsOnlyFile!!)
        }

        if (renamedVersionsKt != null) {
            project.file(OutputFile.VERSIONS.path).renameTo(renamedVersionsKt)
            OutputFile.logFileWasModified(renamedVersionsKt.relativeTo(project.projectDir).path, existed = true)
        }
    }


    @TaskAction
    fun taskActionGradleProperties() {
        val extension: BuildSrcVersionsExtensionImpl = extension()
        val updateGradleProperties = UpdateGradleProperties(extension)

        val specialDependencies =
            listOf(PluginConfig.gradleVersionsPlugin, PluginConfig.buildSrcVersionsPlugin, PluginConfig.gradleLatestVersion(dependencyGraph))

        val versionsOnlyMode = when(val mode = extension.versionsOnlyMode) {
            null, KOTLIN_OBJECT -> return
            else -> mode
        }

        val dependencies = (unsortedParsedDependencies + specialDependencies)
            .sortedBeautifullyBy(extension.orderBy) { it.versionProperty }
            .distinctBy { it.versionProperty }

        if (versionsOnlyMode == GRADLE_PROPERTIES) {
            updateGradleProperties.generateVersionProperties(project.file("gradle.properties"), dependencies)
            OutputFile.GRADLE_PROPERTIES.logFileWasModified()

        } else {
            val file = extension.versionsOnlyFile?.let { project.file(it) }
            val projectUseKotlin = project.file("build.gradle.kts").exists()
            regenerateBuildFile(file, versionsOnlyMode, dependencies, projectUseKotlin)
            if (file != null) OutputFile.logFileWasModified(file.relativeTo(project.projectDir).path, existed = true)
        }
    }

    @TaskAction
    fun sayHello() {
        logger.warn("Hello World!")
    }

    private val dependencyGraph: DependencyGraph by lazy {
        val extension: BuildSrcVersionsExtensionImpl = extension()

        val message = with(PluginConfig) {
            """
                |Running plugins.id("$PLUGIN_ID").version("$PLUGIN_VERSION") with useRefreshVersions=${useRefreshVersions} and extension: $extension
                |See documentation at $issue53PluginConfiguration
                |
            """.trimMargin()

        }
        println(message)
        OutputFile.configure(extension)

        val jsonInput = project.file(PluginConfig.BENMANES_REPORT_PATH)

        return@lazy PluginConfig.readGraphFromJsonFile(jsonInput)
    }

    private val unsortedParsedDependencies: List<Dependency> by lazy {
        parseGraph(dependencyGraph, extension().useFqqnFor)
            .map { d -> d.maybeUpdate(update || extension().alwaysUpdateVersions) }
    }

    @Input @Optional @Transient
    private lateinit var _extension: BuildSrcVersionsExtensionImpl

    fun configure(action: Action<BuildSrcVersionsExtension>) {
        val projectExtension = project.extensions.getByType<BuildSrcVersionsExtension>() as BuildSrcVersionsExtensionImpl
        this._extension = projectExtension.defensiveCopy()
        action.execute(this._extension)
        PluginConfig.useRefreshVersions = project.hasProperty("plugin.de.fayard.buildSrcVersions")
    }

    private fun computeIndent(): String {
        val fromEditorConfig = EditorConfig.findIndentForKotlin(project.file("buildSrc/src/main/kotlin"))
        val computedIndent = indent ?: extension().indent ?: fromEditorConfig ?: PluginConfig.DEFAULT_INDENT
        return if (computedIndent.isBlank()) computedIndent else PluginConfig.DEFAULT_INDENT
    }

    private fun extension(): BuildSrcVersionsExtensionImpl = _extension

    fun BuildSrcVersionsExtension.shouldInitializeBuildSrc() = when(versionsOnlyMode) {
        null -> true
        KOTLIN_OBJECT -> false
        else -> false
    }

}
