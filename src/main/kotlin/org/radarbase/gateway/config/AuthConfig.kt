package org.radarbase.gateway.config

data class AuthConfig(
    /** OAuth 2.0 resource name. */
    val resourceName: String = "res_gateway",
    /**
     * Whether to check that the user that submits data has the reported source ID registered
     * in the ManagementPortal.
     */
    val checkSourceId: Boolean = true,
    /** OAuth 2.0 token issuer. If null, this is not checked. */
    val issuer: String? = null,
    /**
     * ManagementPortal URL. If available, this is used to read the public key from
     * ManagementPortal directly. This is the recommended method of getting public key.
     */
    val managementPortalUrl: String? = null,
    /** Key store for checking the digital signature of OAuth 2.0 JWTs. */
    val keyStore: KeyStoreConfig = KeyStoreConfig(),
    /** Public keys for checking the digital signature of OAuth 2.0 JWTs. */
    val publicKeys: KeyConfig = KeyConfig(),
) {
    fun validate() {
        keyStore.validate()
        check(managementPortalUrl != null || keyStore.isConfigured || publicKeys.isConfigured) {
            "At least one of auth.keyStore, auth.publicKeys or auth.managementPortalUrl must be configured"
        }
    }
}
