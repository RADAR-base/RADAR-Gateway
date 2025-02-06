package org.radarbase.gateway.config

import org.radarbase.gateway.utils.Env.S3_PATH_PREFIX

data class S3StoragePathConfig(
    var prefix: String? = null,
    var collectPerDay: Boolean = true,
) {
    fun checkEnvironmentVars() {
        prefix ?: run {
            prefix = System.getenv(S3_PATH_PREFIX)
        }
    }
}
