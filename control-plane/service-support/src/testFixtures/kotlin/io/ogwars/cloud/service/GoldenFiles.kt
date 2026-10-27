package io.ogwars.cloud.service

import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

/**
 * Golden-file assertions for rendered output (manifests, the OpenAPI spec). Run the tests with
 * `-PupdateSnapshots=true` to rewrite the files, then review the diff.
 */
object GoldenFiles {
    private val update = System.getProperty("ogcloud.updateSnapshots").toBoolean()

    fun assertMatches(
        file: Path,
        actual: String,
    ) {
        if (update) {
            Files.createDirectories(file.parent)
            Files.writeString(file, actual)
            return
        }
        check(
            Files.exists(file),
        ) { "Golden file $file is missing; run the tests with -PupdateSnapshots=true and review it" }
        actual shouldBe Files.readString(file)
    }
}
