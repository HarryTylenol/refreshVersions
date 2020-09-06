//package de.fayard
//
//import de.fayard.internal.AvailableDependency
//import de.fayard.internal.Dependencies
//import de.fayard.internal.Dependency
//import de.fayard.internal.PluginConfig
//import de.fayard.internal.gradleNotation
//import de.fayard.internal.orderDependencies
//import de.fayard.internal.versionInformation
//import io.kotlintest.inspectors.forAll
//import io.kotlintest.matchers.string.contain
//import io.kotlintest.should
//import io.kotlintest.shouldBe
//import io.kotlintest.specs.FreeSpec
//
//class UnitTests: FreeSpec({
//    val DEPS = listOf(
//        "android.arch.persistence.room:compiler:1.1.1",
//        "android.arch.persistence.room:runtime:1.1.1",
//        "android.arch.persistence.room:rxjava2:1.1.1",
//        "com.android.tools.build:gradle:3.2.1",
//        "com.github.bumptech.glide:compiler:4.8.0",
//        "com.squareup.okio:okio:2.0.0",
//        "de.mpicbg.scicomp:krangl:0.10.3",
//        "io.fabric.tools:gradle:1.26.1",
//        "io.reactivex.rxjava2:rxjava:2.2.0",
//        "org.jetbrains.kotlin:konan-utils:0.9.0-native",
//        "org.jetbrains.kotlin:kotlin-test:1.3.0-rc-190"
//    )
//    val dependencies = Dependencies(DEPS.map {
//        val (group, name, version) = it.split(":")
//        Dependency(group = group, version = version, name = name, escapedName = name)
//    }, count = DEPS.size)
//
//
//    "Ordering dependencies" {
//        val shuffleAndReorder = dependencies
//            .shuffled()
//            .orderDependencies()
//            .map { it.gradleNotation() }
//        shuffleAndReorder shouldBe DEPS
//
//    }
//
//    "Version information" - {
//        "Available" {
//            Dependency(available = AvailableDependency(release = "1.0.0")).versionInformation() should contain("1.0.0")
//        }
//        "Up-to-date" {
//            Dependency(latest = "", reason = "", available = null).versionInformation() shouldBe ""
//        }
//        "Exceeded" {
//            Dependency(latest = "2.0.0").versionInformation() shouldBe ""
//        }
//
//        "Reason" {
//            Dependency(reason = "Could not find any matches").versionInformation() shouldBe ""
//        }
//    }
//
//
//    "Stable versions" {
//        listOf("0.1.2", "v1.2", "1.0.0-RELEASE", "0.1-GA", "1-1").forAll {
//            PluginConfig.isNonStable(it) shouldBe false
//        }
//    }
//
//    "Unstable versions" {
//        listOf("0.1-SNAPSHOT", "2.0.9pr1", "0.1.beta").forEach {
//            PluginConfig.isNonStable(it) shouldBe true
//        }
//    }
//
//
//})
