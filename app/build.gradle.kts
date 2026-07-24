import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

group = "com.amigo_wangwx.curlvisualizer"
version = providers.gradleProperty("app.version").get()

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.10.0-alpha05")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

compose.desktop {
    application {
        mainClass = "com.amigo_wangwx.curlvisualizer.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "CurlVisualizer"
            packageVersion = project.version.toString()
            description = "A desktop curl response visualizer."
            vendor = "amigo_wangwx"

            macOS {
                bundleID = "com.amigo_wangwx.curlvisualizer"
                appCategory = "public.app-category.developer-tools"
            }

            windows {
                menuGroup = "Curl Visualizer"
                upgradeUuid = "6D9E477B-58DF-4C9F-96E8-BB31F39B6136"
            }
        }
    }
}
