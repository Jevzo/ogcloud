package io.ogwars.cloud.domain.common

import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class MemoryQuantityTest {
    @ParameterizedTest
    @CsvSource(
        "1536Mi, 1610612736",
        "2Gi, 2147483648",
        "1.5Gi, 1610612736",
        "2G, 2000000000",
        "512M, 512000000",
        "64Ki, 65536",
        "1000, 1000",
    )
    fun `Kubernetes memory quantities are read in bytes`(
        quantity: String,
        bytes: Long,
    ) {
        MemoryQuantity.bytes(quantity) shouldBe bytes
    }

    @ParameterizedTest
    @CsvSource("lots", "1.5", "-1Gi", "2gi", "500m", "''")
    fun `anything else is not a memory quantity`(quantity: String) {
        MemoryQuantity.bytes(quantity) shouldBe null
    }

    @ParameterizedTest
    @CsvSource("1610612736, 1536Mi", "2147483648, 2Gi", "1000, 1000")
    fun `byte counts are shown in the largest whole binary unit`(
        bytes: Long,
        shown: String,
    ) {
        MemoryQuantity.format(bytes) shouldBe shown
    }
}
