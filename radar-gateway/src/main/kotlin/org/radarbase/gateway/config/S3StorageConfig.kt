package org.radarbase.gateway.config

import org.radarbase.gateway.utils.Env.AWS_ACCESS_KEY_ID
import org.radarbase.gateway.utils.Env.AWS_DEFAULT_REGION
import org.radarbase.gateway.utils.Env.AWS_ENDPOINT_URL_S3
import org.radarbase.gateway.utils.Env.AWS_S3_BUCKET_NAME
import org.radarbase.gateway.utils.Env.AWS_SECRET_ACCESS_KEY
import org.radarbase.jersey.config.ConfigLoader.copyEnv
import org.radarbase.jersey.config.ConfigLoader.copyOnChange

data class S3StorageConfig(
    var url: String? = null,
    var accessKey: String? = null,
    var secretKey: String? = null,
    var bucketName: String? = null,
    var region: String? = null,
    var path: S3StoragePathConfig = S3StoragePathConfig(),
) {
    fun withEnv(): S3StorageConfig = this
        .copyEnv(AWS_ENDPOINT_URL_S3) {
            copy(url = it)
        }
        .copyEnv(AWS_ACCESS_KEY_ID) {
            copy(accessKey = it)
        }
        .copyEnv(AWS_SECRET_ACCESS_KEY) {
            copy(secretKey = it)
        }
        .copyEnv(AWS_S3_BUCKET_NAME) {
            copy(bucketName = it)
        }
        .copyEnv(AWS_DEFAULT_REGION) {
            copy(region = it)
        }
        .copyOnChange(
            path,
            {
                it.withEnv()
            },
            {
                copy(path = it)
            },
        )
}
