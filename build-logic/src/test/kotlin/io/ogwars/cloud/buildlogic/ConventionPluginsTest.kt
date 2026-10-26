package io.ogwars.cloud.buildlogic

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.DataInputStream
import java.io.File
import java.util.zip.ZipFile

class ConventionPluginsTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `kotlin-library compiles and runs JUnit tests`() {
        project("ogcloud.kotlin-library")
        write("src/main/kotlin/Greeter.kt", "fun greet(name: String): String = \"hi \$name\"\n")
        write(
            "src/test/kotlin/GreeterTest.kt",
            """
            import io.kotest.matchers.shouldBe
            import org.junit.jupiter.api.Test

            class GreeterTest {
                @Test
                fun greets() {
                    greet("x") shouldBe "hi x"
                }
            }
            """.trimIndent(),
        )

        val result = run("test")

        result.task(":test")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    @Test
    fun `kotlin-library fails the build on compiler warnings`() {
        project("ogcloud.kotlin-library")
        write("src/main/kotlin/Warn.kt", "fun same(value: String): String = value!!\n")

        val result = runner("compileKotlin").buildAndFail()

        result.output shouldContain "Unnecessary non-null assertion"
    }

    @Test
    fun `mc-plugin targets the Minecraft JVM and relocates Kotlin`() {
        project("ogcloud.mc-plugin")
        write("src/main/kotlin/Plugin.kt", "fun names(): List<String> = listOf(\"a\")\n")

        run("shadowJar")

        val jar = File(projectDir, "build/libs").listFiles().orEmpty().single { it.name.endsWith("-all.jar") }
        ZipFile(jar).use { zip ->
            val entries = zip.entries().toList().map { it.name }
            entries.any { it.startsWith("io/ogwars/cloud/shaded/kotlin/") } shouldBe true
            entries.any { it.startsWith("kotlin/") } shouldBe false
            val majorVersion =
                DataInputStream(zip.getInputStream(zip.getEntry("PluginKt.class"))).use {
                    it.readInt()
                    it.readUnsignedShort()
                    it.readUnsignedShort()
                }
            majorVersion shouldBe JAVA_21_CLASS_FILE_VERSION
        }
    }

    @Test
    fun `spring-service applies Spring Boot`() {
        project("ogcloud.spring-service")

        val result = run("help", "--task", "bootJar")

        result.output shouldContain "bootJar"
    }

    @Test
    fun `published-library declares the MIT license`() {
        project("ogcloud.published-library")

        run("generatePomFileForMavenPublication")

        File(projectDir, "build/publications/maven/pom-default.xml").readText() shouldContain "MIT License"
    }

    private fun project(pluginId: String) {
        val catalog = System.getProperty("ogcloud.catalog").replace("\\", "/")
        write(
            "settings.gradle.kts",
            """
            dependencyResolutionManagement {
                repositories { mavenCentral() }
                versionCatalogs { create("libs") { from(files("$catalog")) } }
            }
            rootProject.name = "fixture"
            """.trimIndent(),
        )
        write("build.gradle.kts", "plugins { id(\"$pluginId\") }\n")
    }

    private fun write(
        path: String,
        content: String,
    ) {
        File(projectDir, path).apply { parentFile.mkdirs() }.writeText(content)
    }

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withTestKitDir(File(System.getProperty("ogcloud.testkit")))
            .withPluginClasspath()
            .withArguments(*args, "--stacktrace")

    private fun run(vararg args: String): BuildResult = runner(*args).build()

    private companion object {
        const val JAVA_21_CLASS_FILE_VERSION = 65
    }
}
