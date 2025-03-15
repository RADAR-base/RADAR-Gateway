package org.radarbase.gateway.service.storage.path

import org.radarbase.gateway.utils.requireNotNullAndBlank
import java.util.*

/**
 * Represents a path on Object Storage for uploaded files.
 *
 * The storage path is constructed using required parameters (`projectId`, `subjectId`, `topicId`, and `filename`)
 * and optional parameters (`prefix`, `collectPerDay`, `folderPattern`, `filePattern`).
 *
 * The path follows the format:
 * ```
 * prefix/projectId/subjectId/topicId/[day folder]/timestamp_filename.extension
 * ```
 *
 * - The **day folder** is included if `collectPerDay` is `true`.
 * - The filename is automatically assigned a **timestamp and UUID** to prevent overwriting.
 *
 * ### Usage Example:
 * ```kotlin
 * val path = StoragePath(
 *     filename = "example.txt",
 *     projectId = "project1",
 *     subjectId = "subjectA",
 *     topicId = "topicX",
 *     prefix = "uploads",
 *     collectPerDay = true
 * )
 *
 * println(path.fullPath)
 * // Output: uploads/project1/subjectA/topicX/20250205/20250205_<UUID>.txt
 *
 * println(path.pathInTopicDirectory)
 * // Output: 20250205/20250205_<UUID>.txt
 * ```
 *
 * ### Parameters:
 * @property filename The name of the uploaded file (required).
 * @property projectId The project ID associated with the file.
 * @property subjectId The subject ID associated with the file.
 * @property topicId The topic name under which the file is stored.
 * @property prefix Directory prefix for the storage path.
 * @property collectPerDay If `true`, stores files under a daily folder.
 * @property folderPattern The pattern for daily folder naming.
 * @property filePattern The pattern for filename timestamps.
 * @property directorySeparator The separator used in the generated path.
 *
 * @property fullPath The complete storage path including project, subject, topic, and timestamped filename.
 * @property pathInTopicDirectory The relative path inside the topic directory, including the timestamped filename.
 */
data class StoragePath(
    val filename: String = "",
    val projectId: String = "",
    val subjectId: String = "",
    val topicId: String = "",
    private val prefix: String = "",
) {

    fun verifyPath() {
        requireNotNullAndBlank(filename) { "File name should be set" }
        requireNotNullAndBlank(projectId) { "Project Id should be set" }
        requireNotNullAndBlank(subjectId) { "Subject Id should be set" }
        requireNotNullAndBlank(topicId) { "Topic Id should be set" }
    }

    fun getFileExtension(originalFileName: String): String {
        val lastDot = originalFileName.lastIndexOf('.')
        return if (lastDot >= 0) {
            originalFileName.substring(lastDot).lowercase(Locale.ENGLISH)
        } else {
            ""
        }
    }
}
