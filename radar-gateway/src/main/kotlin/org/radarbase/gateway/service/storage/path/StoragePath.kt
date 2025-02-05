package org.radarbase.gateway.service.storage.path

import org.radarbase.gateway.utils.requireNotNullAndBlank
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Represents a path on Object Storage for uploaded files.
 *
 * The storage path is constructed to include required arguments (projectId, subjectId, topicId, and filename)
 * and optional arguments (prefix, collectPerDay, folderTimestampPattern, fileTimestampPattern).
 *
 * The path will follow the format: `prefix/projectId/subjectId/topicId/[day folder]/timestamp_filename.extension`.
 *
 * - The day folder is included if `collectPerDay` is set to `true`.
 * - File extensions are converted to lowercase.
 *
 * ### Usage Example:
 * ```kotlin
 * val path = StoragePath.builder()
 *     .prefix("uploads")
 *     .projectId("project1")
 *     .subjectId("subjectA")
 *     .topicId("topicX")
 *     .collectPerDay(true)
 *     .fileName("example.txt")
 *     .build()
 *
 * println(path.fullPath)
 * // Output: uploads/project1/subjectA/topicX/20250205/20250205_example.txt
 *
 * println(path.pathInTopicDirectory)
 * // Output: 20250205/20250205_example.txt
 * ```
 */
@Suppress("unused")
class StoragePath(
    val fullPath: String,
    val pathInTopicDirectory: String,
) {

    class Builder {
        private var pathPrefix: String = ""
        private var file: String = ""
        private var doCollectPerDay: Boolean = false
        private var project: String = ""
        private var subject: String = ""
        private var topic: String = ""
        private var folderPattern = "yyyyMMdd"
        private var filePattern = "yyyyMMddHHmmss"
        private var directorySeparator: String = "/"

        fun filename(filename: String) = apply {
            file = filename
        }

        fun prefix(prefix: String) = apply {
            this.pathPrefix = prefix
        }

        fun collectPerDay(collectPerDay: Boolean) = apply {
            this.doCollectPerDay = collectPerDay
        }

        fun projectId(projectId: String) = apply {
            this.project = projectId
        }

        fun subjectId(subjectId: String) = apply {
            this.subject = subjectId
        }

        fun topicId(topicId: String) = apply {
            this.topic = topicId
        }

        fun dayFolderPattern(folderPattern: String) = apply {
            this.folderPattern = folderPattern
        }

        fun fileTimeStampPattern(fileTimeStampPattern: String) = apply {
            this.filePattern = fileTimeStampPattern
        }

        fun build(): StoragePath {
            requireNotNullAndBlank(file) { "File name should be set" }
            requireNotNullAndBlank(project) { "Project Id should be set" }
            requireNotNullAndBlank(subject) { "Subject Id should be set" }
            requireNotNullAndBlank(topic) { "Topic Id should be set" }

            val pathInTopicDir = buildPathInTopicDir()

            val fullPath = listOf(
                pathPrefix,
                project,
                subject,
                topic,
                pathInTopicDir,
            ).filter {
                it.isNotBlank()
            }.joinToString(directorySeparator)

            return StoragePath(fullPath, pathInTopicDir)
        }

        /**
         * Storing files under their original filename is a security risk, as it can be used to
         * overwrite existing files. We generate a random filename server-side to mitigate this risk.
         *
         * See https://owasp.org/www-community/vulnerabilities/Unrestricted_File_Upload
         */
        private fun buildPathInTopicDir(): String {
            return listOfNotNull(
                if (doCollectPerDay) getDayFolder() else null,
                generateRandomFilename(file),
            ).filter { it.isNotBlank() }
                .joinToString(directorySeparator)
        }

        private fun generateRandomFilename(originalFileName: String): String {
            val timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern(filePattern))
            return "${timeStamp}_${UUID.randomUUID()}${getFileExtension(originalFileName)}"
        }

        private fun getDayFolder(): String {
            return LocalDate.now().format(DateTimeFormatter.ofPattern(folderPattern))
        }

        private fun getFileExtension(originalFileName: String): String {
            val lastDot = originalFileName.lastIndexOf('.')
            return if (lastDot >= 0) {
                originalFileName.substring(lastDot).lowercase(Locale.ENGLISH)
            } else {
                ""
            }
        }
    }

    companion object {
        fun builder(): Builder = Builder()
    }
}
