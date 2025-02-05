package org.radarbase.gateway.config

import org.radarbase.gateway.utils.Env.COLLECT_PER_DAY
import org.radarbase.gateway.utils.Env.S3_PATH_PREFIX

data class S3StoragePathConfig(
    var prefix: String? = null,
    var collectPerDay: Boolean? = null,
) {
    fun checkEnvironmentVars() {
        prefix ?: run {
            prefix = System.getenv(S3_PATH_PREFIX)
        }
        collectPerDay ?: run {
            collectPerDay = System.getenv(COLLECT_PER_DAY)?.toBoolean() == true
        }
    }
}
