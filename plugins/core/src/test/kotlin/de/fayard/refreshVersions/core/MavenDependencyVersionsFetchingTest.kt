package de.fayard.refreshVersions.core

import testutils.getVersionCandidates
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import testutils.disabledBecauseIsAnExperiment

class MavenDependencyVersionsFetchingTest {

    @Test
    @Disabled(disabledBecauseIsAnExperiment)
    fun testKotlin() {
        runBlocking {
            val versions = getVersionCandidates(
                moduleId = ModuleId(
                    group = "org.jetbrains.kotlin",
                    name = "kotlin-stdlib"
                ),
                currentVersion = Version("1.3.50"),
                repoUrls = listOf(
                    "https://dl.bintray.com/kotlin/kotlin-eap/",
                    "https://jcenter.bintray.com/"
                )
            ).joinToString(separator = "\n") { it.value }
            println(versions)
        }
    }

    private suspend fun getVersionCandidates(
        moduleId: ModuleId,
        currentVersion: Version,
        repoUrls: List<String>
    ): List<Version> = getVersionCandidates(
        httpClient = defaultHttpClient,
        moduleId = moduleId,
        currentVersion = currentVersion,
        repoUrls = repoUrls
    )

    private val defaultHttpClient by lazy { createTestHttpClient() }

    private fun createTestHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor())
            .build()
    }
}
