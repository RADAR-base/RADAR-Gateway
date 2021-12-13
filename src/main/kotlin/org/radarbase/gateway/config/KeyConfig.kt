package org.radarbase.gateway.config

data class KeyConfig(
    /** List of ECDSA public key signatures in PEM format. */
    val ecdsa: List<String>? = null,
    /** List of RSA public key signatures in PEM format. */
    val rsa: List<String>? = null,
) {
    val isConfigured: Boolean = !ecdsa.isNullOrEmpty() || !rsa.isNullOrEmpty()
}
