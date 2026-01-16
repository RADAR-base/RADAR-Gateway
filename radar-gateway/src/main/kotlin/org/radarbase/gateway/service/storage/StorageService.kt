package org.radarbase.gateway.service.storage

import org.radarbase.gateway.service.storage.path.StoragePath
import java.io.InputStream

interface StorageService {
    fun store(
        fileInputStream: InputStream?,
        path: StoragePath,
    ): String
}
