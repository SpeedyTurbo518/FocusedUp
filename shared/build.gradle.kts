import java.util.Properties
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(17)

    androidTarget()
    jvm("desktop")
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(compose.materialIconsExtended)
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        }
    }
}

android {
    namespace = "com.focusedup.shared"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// ---- App config (Notion login keys, share link) from focusedup.properties (not committed) ----
val appConfigDir = layout.buildDirectory.dir("generated/appconfig/kotlin")
val generateAppConfig by tasks.registering {
    val propsFile = rootProject.file("focusedup.properties")
    inputs.property("config", if (propsFile.exists()) propsFile.readText() else "")
    outputs.dir(appConfigDir)
    doLast {
        val p = Properties()
        if (propsFile.exists()) propsFile.inputStream().use { p.load(it) }
        fun v(key: String, def: String = "") = (p.getProperty(key) ?: def).trim().replace("\\", "\\\\").replace("\"", "\\\"")
        val out = appConfigDir.get().file("com/focusedup/shared/AppConfig.kt").asFile
        out.parentFile.mkdirs()
        out.writeText(
            """
            |package com.focusedup.shared
            |
            |/** Generated from focusedup.properties at build time. */
            |object AppConfig {
            |    const val NOTION_CLIENT_ID = "${v("notion.clientId")}"
            |    const val NOTION_CLIENT_SECRET = "${v("notion.clientSecret")}"
            |    const val SHARE_BASE_URL = "${v("share.baseUrl", "https://speedyturbo518.github.io/FocusedUp/share/")}"
            |}
            |""".trimMargin()
        )
    }
}
kotlin.sourceSets.commonMain { kotlin.srcDir(generateAppConfig) }
