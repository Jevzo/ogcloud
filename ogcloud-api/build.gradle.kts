import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(project(":ogcloud-common"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.minio:minio:8.6.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:2.8.15")

    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

val openApiPort = 18080
val openApiUrl = "http://127.0.0.1:$openApiPort/v3/api-docs.yaml"
val openApiOutputFile = layout.buildDirectory.file("openapi/ogcloud-api.yaml")
val openApiJwtSecret = "0123456789abcdef0123456789abcdef"

tasks.register("generateOpenApiSpec") {
    group = "documentation"
    description = "Generates a local OpenAPI YAML file for ogcloud-api."
    dependsOn(tasks.named<BootJar>("bootJar"))
    outputs.file(openApiOutputFile)

    doLast {
        val bootJarTask = tasks.named<BootJar>("bootJar").get()
        val bootJar = bootJarTask.archiveFile.get().asFile
        val outputFile = openApiOutputFile.get().asFile
        outputFile.parentFile.mkdirs()

        val javaLauncher =
            javaToolchains
                .launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(21))
                }.get()

        val command =
            listOf(
                javaLauncher.executablePath.asFile.absolutePath,
                "-jar",
                bootJar.absolutePath,
                "--server.port=$openApiPort",
                "--spring.main.lazy-initialization=true",
                "--springdoc.api-docs.enabled=true",
                "--ogcloud.auth.jwt-secret=$openApiJwtSecret",
            )

        val process =
            ProcessBuilder(command)
                .directory(rootProject.projectDir)
                .redirectErrorStream(true)
                .start()

        val logThread =
            thread(isDaemon = true, name = "ogcloud-api-openapi-generator-log") {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        logger.info("[generateOpenApiSpec] $line")
                    }
                }
            }

        val client =
            HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build()

        val deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos()
        var generated = false
        var lastErrorMessage: String? = null

        try {
            while (System.nanoTime() < deadline) {
                if (!process.isAlive) {
                    throw GradleException(
                        "ogcloud-api exited before the OpenAPI spec became available. Check the generateOpenApiSpec logs.",
                    )
                }

                try {
                    val request =
                        HttpRequest
                            .newBuilder(URI.create(openApiUrl))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build()
                    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                    if (response.statusCode() == 200) {
                        outputFile.writeText(response.body())
                        generated = true
                        break
                    }
                    lastErrorMessage = "Received HTTP ${response.statusCode()} from $openApiUrl"
                } catch (error: Exception) {
                    lastErrorMessage = error.message
                }

                Thread.sleep(1_000)
            }

            if (!generated) {
                throw GradleException(
                    "Timed out waiting for $openApiUrl. Last error: ${lastErrorMessage ?: "unknown"}",
                )
            }
        } finally {
            process.destroy()
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
            logThread.join(1_000)
        }

        logger.lifecycle("OpenAPI spec written to ${outputFile.absolutePath}")
    }
}
