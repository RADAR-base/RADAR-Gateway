package org.radarbase.gateway.config

import org.radarbase.gateway.utils.Env.AWS_S3_BUCKET_NAME
import org.radarbase.gateway.utils.Env.AWS_S3_PATH_PREFIX
import org.radarbase.jersey.config.ConfigLoader.copyEnv

data class S3StoragePathConfig(
    var prefix: String? = null,
    var collectPerDay: Boolean = true,
) {
    fun withEnv(): S3StoragePathConfig = this
        .copyEnv(AWS_S3_BUCKET_NAME) {
            copy(prefix = prefix)
        }
}
