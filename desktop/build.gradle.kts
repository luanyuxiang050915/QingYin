import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
    kotlin("plugin.compose") version "2.0.21"
}

group = "com.example"
version = "0.2.0"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

kotlin {
    sourceSets {
        // 独立源集收录 Android 工程里的纯 JVM 共享代码（用 include 白名单，避免误伤桌面版文件）
        val sharedMain by creating {
            kotlin.srcDir("../android/app/src/main/java")
            kotlin.include(
                "**/model/VideoInfo.kt",
                "**/net/Http.kt",
                "**/download/VideoDownloader.kt",
                "**/parser/BilibiliParser.kt",
                "**/parser/KuaishouParser.kt",
                "**/parser/XParser.kt",
                "**/parser/XiaohongshuParser.kt",
                "**/parser/WeiboParser.kt",
            )
        }
        main {
            dependsOn(sharedMain)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.example.videosaver.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "QingYin"
            packageVersion = "0.2.0"
            description = "清印 · 视频下载去水印（桌面版）"
            vendor = "QingYin"
        }
    }
}

// ---- 手动 jpackage 辅助任务（绕过 createRuntimeImage 目录缺失问题）----
tasks.register<Copy>("copyJpackageInput") {
    dependsOn("jar")
    into(layout.buildDirectory.dir("jpackage-input"))
    from(tasks.named("jar")) {
        rename { "app.jar" }
    }
    from(configurations.runtimeClasspath)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
