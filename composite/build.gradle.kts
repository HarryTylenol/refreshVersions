plugins {
    `build-scan`
    idea
}

group = "de.fayard"
version = "0.5.0"
defaultTasks("run")

val PLUGIN: IncludedBuild = gradle.includedBuild("plugin")
val SAMPLE_KOTLIN: IncludedBuild = gradle.includedBuild("sample-kotlin")
val SAMPLE_GROOVY: IncludedBuild = gradle.includedBuild("sample-groovy")
val SAMPLE_VERSIONS_ONLY: IncludedBuild = gradle.includedBuild("sample-versionsOnlyMode")
val REFRESH_VERSIONS = ":refreshVersions"
val BUILD_SRC_VERSIONS = ":buildSrcVersions"
val CUSTOM = "custom"

tasks.register("publishLocally") {
    group = CUSTOM
    description = "Publish the plugin locally"
    dependsOn(":checkAll")
    dependsOn(PLUGIN.task(":publishToMavenLocal"))
}

tasks.register("publishPlugins") {
    group = CUSTOM
    description = "Publishes this plugin to the Gradle Plugin portal."
    dependsOn(":publishLocally")
    dependsOn(":checkAll")
    dependsOn(PLUGIN.task(":publishPlugins"))
}

tasks.register<DefaultTask>("hello") {
    group = CUSTOM
    description = "Minimal task that do nothing. Useful to debug a failing build"
}

tasks.register("bsv") {
    group = CUSTOM
    description = "Run plugin unit tests"
    dependsOn(PLUGIN.task(":publishToMavenLocal"))
    dependsOn(SAMPLE_KOTLIN.task(BUILD_SRC_VERSIONS))
    dependsOn(SAMPLE_GROOVY.task(BUILD_SRC_VERSIONS))
}


tasks.register("pluginTests") {
    group = CUSTOM
    description = "Run plugin unit tests"
    dependsOn(PLUGIN.task(":check"))
}

tasks.register("checkAll") {
    group = CUSTOM
    description = "Run all checks"
    dependsOn(SAMPLE_VERSIONS_ONLY.task(REFRESH_VERSIONS))
    dependsOn(SAMPLE_KOTLIN.task(REFRESH_VERSIONS))
    dependsOn(SAMPLE_GROOVY.task(REFRESH_VERSIONS))
    dependsOn(SAMPLE_KOTLIN.task(BUILD_SRC_VERSIONS))
    dependsOn(SAMPLE_GROOVY.task(BUILD_SRC_VERSIONS))
    dependsOn(PLUGIN.task(":validateTaskProperties"))
    dependsOn(PLUGIN.task(":check"))
    dependsOn(SAMPLE_VERSIONS_ONLY.task(":checkAll"))
}


tasks.register("updateGradle") {
    group = CUSTOM
    description = "Update Gradle in all modules"
    dependsOn(":wrapper")
    dependsOn(PLUGIN.task(":wrapper"))
    dependsOn(SAMPLE_KOTLIN.task(":wrapper"))
    dependsOn(SAMPLE_GROOVY.task(":wrapper"))
    dependsOn(SAMPLE_VERSIONS_ONLY.task(":wrapper"))
}

tasks.withType<Wrapper> {
    group = CUSTOM
    description = "Update Gradle with ./gradlew wrapper"
    gradleVersion = System.getenv("GRADLE_VERSION") ?: "5.6.1"
    distributionType = Wrapper.DistributionType.ALL
}

buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"
    publishAlways()
}
