plugins {
    id("ogcloud.kotlin-library")
    `java-test-fixtures`
}

// Committed output of `mise run gen` (contracts/runtime-profiles.yaml), checked for drift.
sourceSets {
    main {
        kotlin.srcDir("src/generated/kotlin")
    }
}
