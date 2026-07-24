plugins {
    kotlin("jvm") version "2.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0" apply false
    id("org.jetbrains.compose") version "1.10.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0" apply false
}

group = "com.amigo_wangwx.curlvisualizer"
version = providers.gradleProperty("app.version").get()

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

subprojects {
    // 通用配置
    val configuredBuildDir = providers.gradleProperty("build.dir").orNull
    val modulePath = project.path.replace(':', '/').trim('/')

    // 统一子模块构建输出，避免每个模块目录下生成分散的 build 文件夹。
    if (!configuredBuildDir.isNullOrBlank()) {
        layout.buildDirectory.set(file("$configuredBuildDir/${rootProject.name}/build/$modulePath"))
    } else {
        layout.buildDirectory.set(rootProject.layout.buildDirectory.dir(modulePath))
    }
}
