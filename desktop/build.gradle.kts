import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin { jvmToolchain(21) }
tasks.processResources {
    from(rootProject.file("voice")) { include("*.wav"); into("voice") }
}
dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("com.github.kwhat:jnativehook:2.2.2")
    implementation("net.java.dev.jna:jna:5.17.0")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation("org.jetbrains.compose.ui:ui-test-junit4-desktop:1.9.0")
}
tasks.test { exclude("**/NativeTrayTest.class", "**/*GuiTest.class", "**/NativeHotkeyTest.class", "**/NativeHudPointerTest.class", "**/NativeHudTransparencyTest.class", "**/ExternalFlightModelTest.class") }
tasks.register<Test>("externalFmTest") {
    description = "Validates separately downloaded, hash-pinned public FM samples (VOIDMEI_FM_SAMPLES)."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/ExternalFlightModelTest.class")
    outputs.upToDateWhen { false }
}
tasks.register<Test>("nativeHudTransparencyTest") {
    description = "Checks actual HUD alpha compositing on an isolated X11 display with a compositor."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/NativeHudTransparencyTest.class")
    // Driver, compositor, DISPLAY and renderer selection are external runtime state.
    outputs.upToDateWhen { false }
    systemProperty("java.awt.headless", "false")
}
tasks.test { exclude("**/HudScenePerformanceTest.class") }
tasks.register<Test>("nativeHudScenePerformanceTest") {
    description = "Measures compatible full-display HUD updates on an isolated composited display."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/HudScenePerformanceTest.class")
    outputs.upToDateWhen { false }
    systemProperty("java.awt.headless", "false")
}
// JNI libraries inside JARs are invisible to jpackage's ELF dependency scanner.
tasks.withType<AbstractJPackageTask>().configureEach {
    if (targetFormat == TargetFormat.Deb) {
        freeArgs.addAll("--linux-package-deps",
            "libx11-6,libxext6,libxtst6,libxkbcommon-x11-0,libxkbcommon0,libxcb1,libxt6,libxinerama1")
    }
}
tasks.register<Test>("nativeHotkeyTest") {
    description = "Tests native hotkey registration and input on an isolated X11 display."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/NativeHotkeyTest.class")
    systemProperty("java.awt.headless", "false")
}
tasks.register<Test>("guiTest") {
    description = "Runs Compose interaction and rendering tests (requires a display)."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/*GuiTest.class")
    // DISPLAY, compositor, fonts and native focus support are external runtime state.
    outputs.upToDateWhen { false }
    systemProperty("java.awt.headless", "false")
}
tasks.register<Test>("nativeHudPointerTest") {
    description = "Tests HUD mouse input on an isolated X11 display or interactive Windows test desktop."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/NativeHudPointerTest.class")
    systemProperty("java.awt.headless", "false")
}
tasks.register<Test>("packagedHudPointerTest") {
    description = "Tests HUD input and visibility using application JARs from an extracted standalone package."
    group = "verification"
    val appDirectory = providers.gradleProperty("voidmei.testAppDirectory")
    val packagedJars = appDirectory.map { fileTree("$it/lib/app") { include("*.jar") } }
    testClassesDirs = sourceSets.test.get().output.classesDirs
    // Only test support comes from Gradle; all production code/dependencies come from the package.
    classpath = files(sourceSets.test.get().output,
        configurations.testRuntimeClasspath.get() - configurations.runtimeClasspath.get(), packagedJars)
    include("**/NativeHudPointerTest.class", "**/HudWindowGuiTest.class")
    systemProperty("java.awt.headless", "false")
    outputs.upToDateWhen { false }
    doFirst {
        val os = System.getProperty("os.name", "").lowercase()
        require((os.contains("linux") && System.getenv("VOIDMEI_TEST_ISOLATED_X11") == "1") ||
            (os.startsWith("windows") && System.getenv("VOIDMEI_TEST_ISOLATED_WINDOWS") == "1")) {
            "Packaged HUD tests require a dedicated X11 or interactive Windows test desktop"
        }
        val directory = file(requireNotNull(appDirectory.orNull) {
            "Set -Pvoidmei.testAppDirectory to the extracted application directory"
        }).canonicalFile
        require(directory.resolve("lib/app").isDirectory && packagedJars.get().files.isNotEmpty()) {
            "Application JARs are missing from $directory/lib/app"
        }
        systemProperty("voidmei.testPackagedApp", directory.path)
        // Compose's packaged launcher sets this because native Skiko files live beside the JARs.
        systemProperty("skiko.library.path", directory.resolve("lib/app").path)
        systemProperty("compose.application.resources.dir", directory.resolve("lib/app/resources").path)
    }
}
compose.desktop {
    application {
        mainClass = "voidmei.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "VoidMei"
            packageVersion = "2.0.0"
            modules("java.net.http", "jdk.charsets", "java.instrument")
        }
    }
}

tasks.register<Test>("nativeTrayTest") {
    description = "Checks tray manager loss and recovery on an isolated X11 desktop."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/NativeTrayTest.class")
    systemProperty("java.awt.headless", "false")
    outputs.upToDateWhen { false }
    doFirst {
        require(System.getenv("VOIDMEI_TEST_ISOLATED_X11") == "1" && System.getenv("VOIDMEI_TRAY_MANAGER") != null) {
            "Set VOIDMEI_TRAY_MANAGER and use a dedicated X11 desktop"
        }
    }
}
