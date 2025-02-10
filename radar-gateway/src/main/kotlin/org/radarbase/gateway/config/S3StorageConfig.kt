package org.radarbase.gateway.config

import org.radarbase.gateway.utils.Env.AWS_ACCESS_KEY_ID
import org.radarbase.gateway.utils.Env.AWS_S3_BUCKET_NAME
import org.radarbase.gateway.utils.Env.AWS_DEFAULT_REGION
import org.radarbase.gateway.utils.Env.AWS_SECRET_ACCESS_KEY
import org.radarbase.gateway.utils.Env.AWS_ENDPOINT_URL_S3

data class S3StorageConfig(
    var url: String? = null,
    var accessKey: String? = null,
    var secretKey: String? = null,
    var bucketName: String? = null,
    var region: String? = null,
    var path: S3StoragePathConfig = S3StoragePathConfig(),
) {
    fun checkEnvironmentVars() {
        url ?: run {
            url = System.getenv(AWS_ENDPOINT_URL_S3)
        }
        accessKey ?: run {
            accessKey = System.getenv(AWS_ACCESS_KEY_ID)
        }
        secretKey ?: run {
            secretKey = System.getenv(AWS_SECRET_ACCESS_KEY)
        }
        bucketName ?: run {
            bucketName = System.getenv(AWS_S3_BUCKET_NAME)
        }
        region ?: run {
            region = System.getenv(AWS_DEFAULT_REGION)
        }
        path.checkEnvironmentVars()
    }
}
