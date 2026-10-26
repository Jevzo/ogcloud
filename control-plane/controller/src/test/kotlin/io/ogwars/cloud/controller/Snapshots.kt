package io.ogwars.cloud.controller

import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

/** Golden-file assertions for rendered manifests; rewrite them with `-PupdateSnapshots=true` and review the diff. */
object Snapshots {
    private val directory = Path.of("src/test/resources/snapshots")
    private val update = System.getProperty("ogcloud.updateSnapshots").toBoolean()

    fun assertMatches(
        name: String,
        actual: String,
    ) {
        val file = directory.resolve("$name.yaml")
        if (update) {
            Files.createDirectories(directory)
            Files.writeString(file, actual)
            return
        }
        check(
            Files.exists(file),
        ) { "Snapshot $file is missing; run the tests with -PupdateSnapshots=true and review it" }
        actual shouldBe Files.readString(file)
    }
}
