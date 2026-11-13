import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    // MP3 decoding for prayer music
    implementation("javazoom:jlayer:1.0.1")
}

compose.desktop {
    application {
        mainClass = "com.focusedup.desktop.MainKt"

        nativeDistributions {
            // Msi/Exe build on Windows, Dmg builds on a Mac.
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg)
            packageName = "FocusedUp"
            packageVersion = "2.2.0"
            vendor = "STG Studios"
            description = "Focused Up: focus timer, daily tasks and calendar"
            copyright = "© 2026 STG Studios"
            modules("java.instrument", "jdk.unsupported")

            windows {
                menuGroup = "STG Studios"
                shortcut = true
                menu = true
                perUserInstall = true
                dirChooser = false
                upgradeUuid = "4F6C2B1E-8A3D-4C7B-9E21-5D0F3A6B8C12"
                iconFile.set(project.file("icons/FocusedUp.ico"))
            }
            macOS {
                bundleID = "com.stgstudios.focusedup"
                dockName = "Focused Up"
                // Add icons/FocusedUp.icns here when building on a Mac (see README).
            }
        }
    }
}
