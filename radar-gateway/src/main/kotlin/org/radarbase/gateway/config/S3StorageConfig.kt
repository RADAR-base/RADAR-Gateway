package org.radarbase.gateway.config

import org.radarbase.gateway.utils.Env.S3_ACCESS_KEY
import org.radarbase.gateway.utils.Env.S3_BUCKET_NAME
import org.radarbase.gateway.utils.Env.S3_REGION
import org.radarbase.gateway.utils.Env.S3_SECRET_KEY
import org.radarbase.gateway.utils.Env.S3_SERVICE_URL

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
            url = System.getenv(S3_SERVICE_URL)
        }
        accessKey ?: run {
            accessKey = System.getenv(S3_ACCESS_KEY)
        }
        secretKey ?: run {
            secretKey = System.getenv(S3_SECRET_KEY)
        }
        bucketName ?: run {
            bucketName = System.getenv(S3_BUCKET_NAME)
        }
        region ?: run {
            region = System.getenv(S3_REGION)
        }
        path.checkEnvironmentVars()
    }
}
