package org.radarbase.gateway.service.storage

import org.radarbase.gateway.service.storage.path.StoragePath
import java.io.InputStream
import java.time.Instant

interface StorageService {
    suspend fun store(
        fileInputStream: InputStream?,
        path: StoragePath,
        time: Instant = Instant.now(),
    ): String
}
