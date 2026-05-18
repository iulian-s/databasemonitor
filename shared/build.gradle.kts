import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }


    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.compose.material.icons)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.postgresql)
                implementation(libs.oshi.core)
                implementation(libs.kotlinx.serialization.json)
                implementation("org.slf4j:slf4j-simple:2.0.9")
            }
        }
        jsMain.dependencies {
            implementation(libs.wrappers.browser)
        }
    }
}