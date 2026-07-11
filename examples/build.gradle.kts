plugins{
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm()
    mingwX64()
    linuxX64()
    macosArm64()

    applyDefaultHierarchyTemplate()
    sourceSets {
        commonMain.dependencies {
            implementation(project(":kuickjs"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}