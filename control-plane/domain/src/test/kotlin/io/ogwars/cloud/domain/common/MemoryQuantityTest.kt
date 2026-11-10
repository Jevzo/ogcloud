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
        "1e9, 1000000000",
        "15E8, 1500000000",
        "1Pi, 1125899906842624",
        "2P, 2000000000000000",
        "1Ei, 1152921504606846976",
    )
    fun `Kubernetes memory quantities are read in bytes`(
        quantity: String,
        bytes: Long,
    ) {
        MemoryQuantity.bytes(quantity) shouldBe bytes
    }

    @ParameterizedTest
    // 8Ei and 1e19 are past a Long's bytes; 1e-3 and 1Gie3 are no whole quantity of bytes.
    @CsvSource("lots", "1.5", "-1Gi", "2gi", "500m", "''", "8Ei", "1e19", "1e-3", "1Gie3")
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
