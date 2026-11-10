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
    fun `mc-plugin targets Java 25 and relocates Kotlin`() {
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
            majorVersion shouldBe JAVA_25_CLASS_FILE_VERSION
        }
    }

    @Test
    fun `spring-service applies Spring Boot`() {
        project("ogcloud.spring-service")

        val result = run("help", "--task", "bootJar")

        result.output shouldContain "bootJar"
    }

    @Test
    fun `published-library writes Central's POM at the release's version`() {
        project("ogcloud.published-library")
        write("build.gradle.kts", "plugins { id(\"ogcloud.published-library\") }\ndescription = \"A fixture\"\n")

        run("generatePomFileForMavenPublication", "-PogcloudVersion=2.0.0")

        val pom = File(projectDir, "build/publications/maven/pom-default.xml").readText()
        pom shouldContain "<artifactId>ogcloud-fixture</artifactId>"
        pom shouldContain "<version>2.0.0</version>"
        pom shouldContain "<description>A fixture</description>"
        pom shouldContain "MIT License"
        pom shouldContain "<developerConnection>scm:git:ssh://git@github.com/Jevzo/ogcloud.git</developerConnection>"
    }

    @Test
    fun `published-library refuses a POM without a description`() {
        project("ogcloud.published-library")

        val result = runner("generatePomFileForMavenPublication").buildAndFail()

        result.output shouldContain "needs a description for its POM"
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
        const val JAVA_25_CLASS_FILE_VERSION = 69
    }
}
