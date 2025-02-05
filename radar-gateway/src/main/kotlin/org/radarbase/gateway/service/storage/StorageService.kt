package org.radarbase.gateway.service.storage

import java.io.InputStream

interface StorageService {
    fun store(
        fileInputStream: InputStream?,
        filename: String?,
        projectId: String?,
        subjectId: String?,
        topicId: String?
    ): String
}
