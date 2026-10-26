package io.ogwars.cloud.controller

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** A throwaway server certificate for localhost, made with the JDK's keytool; the cluster uses cert-manager (D-0041). */
object TestTls {
    const val PASSWORD = "test-only"
    private const val ALIAS = "grpc"
    private const val KEYTOOL_TIMEOUT_SECONDS = 30L

    private val directory: Path = Files.createTempDirectory("ogcloud-tls")
    val keystore: Path = directory.resolve("grpc.p12")
    val certificate: Path = directory.resolve("grpc.crt")

    init {
        keytool(
            "-genkeypair",
            "-alias",
            ALIAS,
            "-keyalg",
            "EC",
            "-groupname",
            "secp256r1",
            "-dname",
            "CN=localhost",
            "-ext",
            "SAN=dns:localhost",
            "-validity",
            "1",
            "-storetype",
            "PKCS12",
            "-keystore",
            keystore.toString(),
            "-storepass",
            PASSWORD,
        )
        keytool(
            "-exportcert",
            "-rfc",
            "-alias",
            ALIAS,
            "-keystore",
            keystore.toString(),
            "-storepass",
            PASSWORD,
            "-file",
            certificate.toString(),
        )
    }

    private fun keytool(vararg args: String) {
        val keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString()
        val process = ProcessBuilder(keytool, *args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(KEYTOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS) && process.exitValue() == 0) {
            "keytool failed: $output"
        }
    }
}
