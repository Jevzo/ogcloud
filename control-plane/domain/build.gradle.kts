plugins {
    id("ogcloud.kotlin-library")
}

// Committed output of `mise run gen` (contracts/runtime-profiles.yaml), checked for drift.
sourceSets {
    main {
        kotlin.srcDir("src/generated/kotlin")
    }
}
