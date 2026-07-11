plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm()
    listOf(
        mingwX64(),
        linuxX64(),
        macosArm64()
    ).forEach {
        it.binaries {
            executable()
        }
    }

    applyDefaultHierarchyTemplate()
    sourceSets {
        commonMain.dependencies {
            implementation(project(":kuickjs"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
