package de.fayard.refreshVersions.internal

import de.fayard.refreshVersions.core.internal.*
import de.fayard.refreshVersions.core.internal.cli.AnsiColor
import de.fayard.refreshVersions.core.internal.cli.CliGenericUi
import kotlinx.coroutines.*
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalDependency
import kotlin.coroutines.coroutineContext

internal fun Configuration.shouldBeIgnored(): Boolean {
    return name.startsWith(prefix = "_internal") // Real-life example: _internal_aapt2_binary (introduced by AGP)
        || name in ignoredConfigurationNames || name.startsWith('-')
    //TODO: If unwanted configurations still get through, we can filter to known ones here, like
    // implementation, api, compileOnly, runtimeOnly, kapt, plus test, MPP and MPP test variants.
}

private val ignoredConfigurationNames = listOf(
    "kotlinCompilerPluginClasspath",
    "kotlinKaptWorkerDependencies",
    "lintClassPath"
)

//TODO: Ignore the following dependency: org.jetbrains.kotlin:kotlin-android-extensions-runtime

internal fun Configuration.countDependenciesWithHardcodedVersions(
    versionsProperties: Map<String, String>,
    versionKeyReader: ArtifactVersionKeyReader
): Int = dependencies.count { dependency ->
    dependency is ExternalDependency && dependency.hasHardcodedVersion(versionsProperties, versionKeyReader)
}

internal fun Project.countDependenciesWithHardcodedVersions(versionsProperties: Map<String, String>): Int {
    val versionKeyReader = RefreshVersionsConfigHolder.versionKeyReader
    return configurations.sumBy { configuration ->
        if (configuration.shouldBeIgnored()) 0 else {
            configuration.countDependenciesWithHardcodedVersions(versionsProperties, versionKeyReader)
        }
    }
}


fun configurationsWithHardcodedDepdencies(rootProject: Project) {
    require(rootProject == rootProject.rootProject) { "Expected a rootProject but got $rootProject" }
    val versionsProperties = RefreshVersionsConfigHolder.readVersionProperties()
    val versionKeyReader = RefreshVersionsConfigHolder.versionKeyReader

    val projectsWithHardcodedDependenciesVersions: List<Project> = rootProject.allprojects.filter {
        it.countDependenciesWithHardcodedVersions(versionsProperties) > 0
    }

    val configurationsWithHardcodedDependencies = projectsWithHardcodedDependenciesVersions.flatMap { project ->
        project.configurations.filterNot { configuration ->
            configuration.shouldBeIgnored() || 0 == configuration.countDependenciesWithHardcodedVersions(versionsProperties, versionKeyReader)
        }.map { configuration -> project to configuration }
    }

    val keysAndVersions = configurationsWithHardcodedDependencies.flatMap { (project, configuration) ->
        configuration.dependencies
            .filterIsInstance<ExternalDependency>()
            .filter { it.hasHardcodedVersion(versionsProperties, versionKeyReader) && it.version != null }
            .map { dependency: ExternalDependency ->
                val versionKey = getVersionPropertyName(dependency.module, versionKeyReader)
                versionKey to dependency.version!!
            }
    }
    val newEntries = keysAndVersions
        .groupBy({it.first}, {it.second})
        .mapValues { entry -> entry.value.max()!! }
        .toList()
        .sortedBy { it.first }
    newEntries.forEach { println(it) }
    writeWithNewEntries(RefreshVersionsConfigHolder.versionsPropertiesFile, newEntries)
    Thread.sleep(1000)

}


internal fun promptProjectSelection(rootProject: Project): Project? {
    require(rootProject == rootProject.rootProject) { "Expected a rootProject but got $rootProject" }
    val versionsProperties = RefreshVersionsConfigHolder.readVersionProperties()

    val projectsWithHardcodedDependenciesVersions: List<Pair<Project, Int>> = rootProject.allprojects.mapNotNull {
        val hardcodedDependenciesVersionsCount = it.countDependenciesWithHardcodedVersions(versionsProperties)
        if (hardcodedDependenciesVersionsCount > 0) {
            it to hardcodedDependenciesVersionsCount
        } else null
    }
    val cliGenericUi = CliGenericUi()
    val index = cliGenericUi.showMenuAndGetIndexOfChoice(
        header = "All the following modules have hardcoded dependencies versions",
        footer = "Type the number of the Gradle module you want to migrate first:",
        numberedEntries = projectsWithHardcodedDependenciesVersions.map { (project, hardcodedVersionsCount) ->
            "${project.path} ($hardcodedVersionsCount)"
        } + "Exit"
    )
    return projectsWithHardcodedDependenciesVersions.getOrNull(index.value)?.first
}

internal suspend fun runInteractiveMigrationToDependenciesConstants(project: Project) {
    val versionsProperties = RefreshVersionsConfigHolder.readVersionProperties()
    while (coroutineContext.isActive) {
        val selectedConfiguration = project.promptConfigurationSelection(versionsProperties) ?: return
        runConfigurationDependenciesMigration(
            project,
            versionsProperties,
            selectedConfiguration
        )
    }
}

private fun Project.promptConfigurationSelection(versionsProperties: Map<String, String>): Configuration? {
    @Suppress("UnstableApiUsage")
    val versionKeyReader = RefreshVersionsConfigHolder.versionKeyReader
    val configurationsWithHardcodedDependenciesVersions = configurations.mapNotNull { configuration ->
        if (configuration.shouldBeIgnored()) return@mapNotNull null
        val count = configuration.countDependenciesWithHardcodedVersions(versionsProperties, versionKeyReader)
        return@mapNotNull if (count == 0) null else configuration to count
    }
    val cliGenericUi = CliGenericUi()
    val index = cliGenericUi.showMenuAndGetIndexOfChoice(
        header = "${AnsiColor.WHITE.boldHighIntensity}${AnsiColor.GREEN.background}" +
            "You selected project $path" +
            "${AnsiColor.RESET}\n" +
            "The following configurations have dependencies with hardcoded versions",
        footer = "Type the number of the configuration you want to migrate first:",
        numberedEntries = configurationsWithHardcodedDependenciesVersions.map { (configuration, count) ->
            "${configuration.name} ($count)"
        } + "Back"
    )
    return configurationsWithHardcodedDependenciesVersions.getOrNull(index.value)?.first
}
