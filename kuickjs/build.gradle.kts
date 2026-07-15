@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.dshatz.kni.bundlesNatives
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.net.URI
import java.util.zip.ZipInputStream

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kni)
}

group = "io.github.peacefulprogram"
version = "0.1.0"

kni {
    autoWire {
        kspDependency.set(libs.kni.processor)
    }
}


kotlin {

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    tvosX64()
    tvosArm64()
    tvosSimulatorArm64()

    watchosArm32()
    watchosArm64()
    watchosSimulatorArm64()
    watchosX64()
    watchosDeviceArm64()

    val desktopTargets = listOf(
        linuxX64(),
        linuxArm64(),
        macosX64(),
        macosArm64(),
        mingwX64()
    )

    val androidNativeTargets = listOf(
        androidNativeX64(),
        androidNativeArm32(),
        androidNativeArm64(),
        androidNativeX86(),
    )

    desktopTargets.forEach { it.binaries.sharedLib() }
    androidNativeTargets.forEach { it.binaries.sharedLib() }

    jvm() bundlesNatives desktopTargets

    androidLibrary {
        namespace = "io.github.peacefulprogram.kuickjs"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withJava() // enable java compilation support
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        bundlesNatives(androidNativeTargets)
    }

    targets.withType(KotlinNativeTarget::class.java).forEach { target ->
        target.compilations.getByName("main") {
            cinterops.create("quickjs") {
                definitionFile.set(file("src/cinterop/quickjs.def"))
                includeDirs(file("headers"))
            }
        }
    }

    applyDefaultHierarchyTemplate {
        group("native32") {
            withAndroidNativeX86()
            withAndroidNativeArm32()
            withWatchosArm32()
            withWatchosArm64() // pointer length is 32bit
        }

        group("native64") {
            withAndroidNativeArm64()
            withAndroidNativeX64()
            withIos()
            withTvos()
            withWatchosDeviceArm64()
            withWatchosX64()
            withWatchosSimulatorArm64()
            withMacos()
            withMingw()
            withLinux()
        }
    }

    sourceSets {
        val nativeMain by getting
        val native32Main by getting {
            dependsOn(nativeMain)
        }
        val native64Main by getting {
            dependsOn(nativeMain)
        }

        val nativeTest by getting
        val native32Test by getting {
            dependsOn(nativeTest)
        }
        val native64Test by getting {
            dependsOn(nativeTest)
        }
        commonMain.dependencies {
            //put your multiplatform dependencies here
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kni.annotations)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        val jniJvmMain by getting
        val jniNativeMain by getting
        jniJvmMain.dependencies {
            api(libs.kni)
        }
        jniNativeMain.dependencies {
            api(libs.kni)
        }
    }

    sourceSets.all {
        languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
    }
}

mavenPublishing {

    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    coordinates("io.github.peacefulprogram", "kuickjs", version.toString())

    pom {
        name = "kuickjs"
        description = "A Kotlin Multiplatform library for embedding the QuickJS-ng JavaScript engine."
        inceptionYear = "2024"
        url = "https://github.com/peacefulprogram/kuickjs"
        licenses {
            license {
                name = "Apache License 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "peacefulprogram"
                name = "peacefulprogram"
                email = "jw4273@qq.com"
                url = "https://github.com/peacefulprogram"
            }
        }
        issueManagement {
            system = "GitHub Issues"
            url = "https://github.com/peacefulprogram/kuickjs/issues"
        }
        scm {
            url = "https://github.com/peacefulprogram/kuickjs"
            connection = "scm:git:git://github.com/peacefulprogram/kuickjs.git"
            developerConnection = "scm:git:ssh://git@github.com/peacefulprogram/kuickjs.git"
        }
    }
}

val downloadQuickJsLib by tasks.registering {

    description = "download quickjs static library + headers"

    val libDir = layout.projectDirectory.dir("lib").asFile
    val headersDir = layout.projectDirectory.dir("headers").asFile

    val quickJsZip = File(libDir, "quickjs.zip")
    val quickJsHeader = File(headersDir, "quickjs.h")

    outputs.dir(libDir)
    outputs.dir(headersDir)

    doLast {
        val token = System.getenv("GITHUB_TOKEN")
        libDir.mkdirs()
        headersDir.mkdirs()

        val sevenDays = 7L * 24 * 60 * 60 * 1000

        // -------------------------
        // 1. download quickjs.zip
        // -------------------------
        val needDownloadZip =
            !quickJsZip.exists() ||
                    System.currentTimeMillis() - quickJsZip.lastModified() > sevenDays

        if (needDownloadZip) {
            logger.lifecycle("Fetching latest quickjs-build release (zip)...")

            val apiUrl =
                "https://api.github.com/repos/peacefulprogram/quickjs-build/releases/latest"
            val connection = URI(apiUrl)
                .toURL()
                .openConnection()
            if (token?.isNotEmpty() == true) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            val releaseJson = connection
                .getInputStream()
                .bufferedReader()
                .use { it.readText() }

            val zipUrl = Regex(
                """"browser_download_url"\s*:\s*"([^"]*quickjs\.zip)""""
            )
                .find(releaseJson)
                ?.groupValues
                ?.get(1)
                ?: error("quickjs.zip not found in latest release")

            logger.lifecycle("Downloading zip: $zipUrl")

            URI(zipUrl).toURL().openStream().use { input ->
                quickJsZip.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            logger.lifecycle("Using cached quickjs.zip")
        }

        // -------------------------
        // 2. extract zip
        // -------------------------
        logger.lifecycle("Extracting quickjs.zip")

        ZipInputStream(quickJsZip.inputStream()).use { zip ->
            var entry = zip.nextEntry

            while (entry != null) {
                val target = File(libDir, entry.name)

                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile.mkdirs()
                    target.outputStream().use {
                        zip.copyTo(it)
                    }
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        // -------------------------
        // 3. download quickjs.h
        // -------------------------
        val needDownloadHeader =
            !quickJsHeader.exists() ||
                    System.currentTimeMillis() - quickJsHeader.lastModified() > sevenDays

        if (needDownloadHeader) {
            logger.lifecycle("Downloading quickjs.h ...")

            val headerUrl =
                "https://raw.githubusercontent.com/quickjs-ng/quickjs/master/quickjs.h"

            URI(headerUrl).toURL().openStream().use { input ->
                quickJsHeader.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            logger.lifecycle("Using cached quickjs.h")
        }
    }
}

tasks.forEach { task ->
    if (task.name.startsWith("cinterop")) {
        task.dependsOn(downloadQuickJsLib)
    }
}
