package org.radarbase.gateway.config

import java.nio.file.Files
import java.nio.file.Path

data class KeyStoreConfig(
    /** Path to the p12 key store. */
    val path: Path? = null,
    /** Key alias in the key store. */
    val alias: String? = null,
    /** Password of the key store. */
    val password: String? = null,
) {
    fun validate() {
        if (path != null) {
            check(Files.exists(path)) { "KeyStore configured in auth.keyStore.path does not exist" }
            checkNotNull(alias) { "KeyStore is configured without auth.keyStore.alias" }
            checkNotNull(password) { "KeyStore is configured without auth.keyStore.password" }
        }
    }

    val isConfigured: Boolean = path != null
}
