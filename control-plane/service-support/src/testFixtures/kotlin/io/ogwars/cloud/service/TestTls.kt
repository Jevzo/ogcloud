package io.ogwars.cloud.service

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.PrivateKey
import java.util.Base64
import java.util.concurrent.TimeUnit

/** A throwaway server certificate for localhost, made with the JDK's keytool; the cluster uses cert-manager (D-0041). */
object TestTls {
    const val PASSWORD = "test-only"
    private const val ALIAS = "server"
    private const val KEYTOOL_TIMEOUT_SECONDS = 30L
    private const val PEM_LINE_LENGTH = 64

    private val directory: Path = Files.createTempDirectory("ogcloud-tls")
    val keystore: Path = directory.resolve("server.p12")
    val certificate: Path = directory.resolve("server.crt")

    /** The certificate's key as PKCS #8 PEM, for servers such as Valkey that read no keystores. */
    val privateKey: Path = directory.resolve("server.key")

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
            "SAN=dns:localhost,ip:127.0.0.1",
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
        Files.writeString(privateKey, pem(loadKey()))
    }

    private fun loadKey(): PrivateKey {
        val store = KeyStore.getInstance("PKCS12")
        Files.newInputStream(keystore).use { store.load(it, PASSWORD.toCharArray()) }
        return store.getKey(ALIAS, PASSWORD.toCharArray()) as PrivateKey
    }

    private fun pem(key: PrivateKey): String {
        val body = Base64.getMimeEncoder(PEM_LINE_LENGTH, "\n".toByteArray()).encodeToString(key.encoded)
        return "-----BEGIN PRIVATE KEY-----\n$body\n-----END PRIVATE KEY-----\n"
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
