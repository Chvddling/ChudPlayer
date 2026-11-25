import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.compose") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
}

// App version: set by the GitHub build (-PappVersion=1.0.<build number>), "1.0.0" for local builds.
val appVersion = (findProperty("appVersion") as String?) ?: "1.0.0"

// NewPipeExtractor version: kept in extractor.version, bumped automatically every day by GitHub Actions.
val extractorVersion = (findProperty("extractorVersion") as String?)
    ?: file("extractor.version").readText().trim()

group = "chudplayer"
version = appVersion

kotlin {
    jvmToolchain(21)
    // Themes shared with the phone remote
    sourceSets.named("main") { kotlin.srcDir("shared/src") }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // The same extraction library the Android NewPipe app uses.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:$extractorVersion")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("net.java.dev.jna:jna:5.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // Reads tags and cover art from local music files (mp3, flac, ogg, m4a, wav, wma, aiff…)
    implementation("net.jthink:jaudiotagger:3.0.1")

    // QR code for pairing the phone remote
    implementation("com.google.zxing:core:3.5.3")
}

// Bake the version numbers into the app so it can check for updates.
val genAppInfo by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/appinfo")
    val v = appVersion
    val ex = extractorVersion
    inputs.property("version", v)
    inputs.property("extractor", ex)
    outputs.dir(outDir)
    doLast {
        val f = outDir.get().file("chudplayer/appinfo.properties").asFile
        f.parentFile.mkdirs()
        f.writeText("version=$v\nextractor=$ex\n")
    }
}

sourceSets {
    main {
        resources.srcDir(genAppInfo)
    }
}

compose.desktop {
    application {
        mainClass = "newpipe.desktop.MainKt"
        jvmArgs += listOf(
            "-Dfile.encoding=UTF-8", "-Djna.encoding=UTF-8",
            // Keep the Java heap small and hand unused memory back to Windows while idle
            "-Xms64m", "-Xmx768m", "-Xss512k",
            "-XX:+UseG1GC", "-XX:G1PeriodicGCInterval=15000",
            "-XX:MinHeapFreeRatio=10", "-XX:MaxHeapFreeRatio=25",
            "-XX:+UseStringDeduplication", "-XX:ReservedCodeCacheSize=96m",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "ChudPlayer"
            packageVersion = appVersion
            description = "ChudPlayer - NewPipe-style YouTube and SoundCloud player"
            vendor = "Chuddling"
            includeAllModules = true

            // libmpv-2.dll goes in native-resources/windows/ and is shipped next to the app.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("native-resources"))

            windows {
                menuGroup = "ChudPlayer"
                perUserInstall = true
                shortcut = true
                dirChooser = true
                upgradeUuid = "5b0c8f7e-3f7a-4d0e-9a51-6d2a1e8c4b17"
            }
        }
    }
}
