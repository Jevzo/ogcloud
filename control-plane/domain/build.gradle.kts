plugins {
    id("ogcloud.kotlin-library")
    `java-test-fixtures`
}

dependencies {
    // Stored in-game texts are validated against the tag format (D-0200).
    implementation(project(":contracts:text"))
}

// Committed output of `mise run gen` (contracts/runtime-profiles.yaml), checked for drift.
sourceSets {
    main {
        kotlin.srcDir("src/generated/kotlin")
    }
}
