plugins {
    id("ogcloud.kotlin-library")
    `java-test-fixtures`
}

dependencies {
    // In-game texts are checked against the tag format before they are written; refusals carry its errors (D-0205).
    api(project(":contracts:text"))
}

// Committed output of `mise run gen` (contracts/runtime-profiles.yaml), checked for drift.
sourceSets {
    main {
        kotlin.srcDir("src/generated/kotlin")
    }
}
