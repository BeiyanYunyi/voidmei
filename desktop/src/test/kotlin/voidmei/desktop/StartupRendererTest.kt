package voidmei.desktop

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.*
import org.jetbrains.skiko.SkikoProperties

class StartupRendererTest {
    @Test fun freshJvmAppliesPreferenceBeforeSkikoAndRespectsExplicitOverrides() {
        for ((enabled, environment, property, expected) in listOf(
            listOf("true", "", "", "SOFTWARE_FAST"),
            listOf("true", "OPENGL", "", "OPENGL"),
            listOf("true", "", "OPENGL", "OPENGL"),
            listOf("false", "", "SOFTWARE_FAST", "SOFTWARE_FAST"),
        )) {
            val command = mutableListOf(Path.of(System.getProperty("java.home"), "bin", "java").toString())
            if (property.isNotEmpty()) command += "-Dskiko.renderApi=$property"
            command += listOf("-cp", System.getProperty("java.class.path"), RendererProbe::class.java.name, enabled)
            val builder = ProcessBuilder(command).redirectErrorStream(true)
            builder.environment().remove("SKIKO_RENDER_API")
            builder.environment().remove("JAVA_TOOL_OPTIONS")
            builder.environment().remove("_JAVA_OPTIONS")
            builder.environment().remove("JDK_JAVA_OPTIONS")
            if (environment.isNotEmpty()) builder.environment()["SKIKO_RENDER_API"] = environment
            val process = builder.start()
            try {
                assertTrue(process.waitFor(15, TimeUnit.SECONDS))
                val output = process.inputStream.bufferedReader().readText()
                assertEquals(0, process.exitValue(), output)
                assertEquals(expected, output.trim())
            } finally { process.destroyForcibly() }
        }
    }
}

object RendererProbe {
    @JvmStatic fun main(args: Array<String>) {
        configureStartupRenderer(args.single().toBooleanStrict())
        println(SkikoProperties.renderApi.name)
    }
}
