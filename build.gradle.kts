import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
    id("org.jetbrains.compose") version "1.10.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
}

group = "com.wang.curlvisualizer"
version = "1.0.0"

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
        mainClass = "com.wang.curlvisualizer.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "CurlVisualizer"
            packageVersion = "1.0.0"
            description = "A desktop curl response visualizer."
            vendor = "wangwx"

            macOS {
                bundleID = "com.wang.curlvisualizer"
                appCategory = "public.app-category.developer-tools"
            }

            windows {
                menuGroup = "Curl Visualizer"
                upgradeUuid = "6D9E477B-58DF-4C9F-96E8-BB31F39B6136"
            }
        }
    }
}
