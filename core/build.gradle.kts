plugins { kotlin("multiplatform") }

kotlin {
    jvm()
    js(IR) { nodejs() }
    jvmToolchain(21)
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
    }
}

tasks.named<ProcessResources>("jvmTestProcessResources") {
    from(rootProject.file("script")) {
        include("mock_data.json", "mock_scenarios/snapshots/*.json")
    }
}
