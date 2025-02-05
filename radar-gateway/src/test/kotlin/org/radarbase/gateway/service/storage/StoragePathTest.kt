package org.radarbase.gateway.service.storage

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.radarbase.gateway.service.storage.path.StoragePath

class StoragePathTest {

    companion object {
        private const val PREFIX = "prefix"
        private const val FILENAME = "example.txt"
        private const val PROJECT_ID = "project1"
        private const val SUBJECT_ID = "subjectA"
        private const val TOPIC_ID = "topicX"

        private const val SIMPLE_LOCAL_FILE_PATTERN = "[0-9]+_[a-z0-9-]+\\.txt"
    }

    @Test
    fun minimalValidPath() {
        val path = StoragePath.builder()
            .filename(FILENAME)
            .projectId(PROJECT_ID)
            .subjectId(SUBJECT_ID)
            .topicId(TOPIC_ID)
            .build()

        assertTrue(path.fullPath.matches(Regex("project1/subjectA/topicX/[0-9]+_[a-z0-9-]+\\.txt")))
        assertTrue(path.pathInTopicDirectory.matches(Regex(SIMPLE_LOCAL_FILE_PATTERN)))
    }

    @Test
    fun includeDayFolder() {
        val path = StoragePath.builder()
            .filename(FILENAME)
            .projectId(PROJECT_ID)
            .subjectId(SUBJECT_ID)
            .topicId(TOPIC_ID)
            .collectPerDay(true)
            .build()

        assertTrue(path.fullPath.matches(Regex("project1/subjectA/topicX/[0-9]+/[0-9]+_[a-z0-9-]+\\.txt")))
        assertTrue(path.pathInTopicDirectory.matches(Regex("[0-9]+/[0-9]+_[a-z0-9-]+\\.txt")))
    }

    @Test
    fun includePrefix() {
        val path = StoragePath.builder()
            .prefix(PREFIX)
            .filename(FILENAME)
            .projectId(PROJECT_ID)
            .subjectId(SUBJECT_ID)
            .topicId(TOPIC_ID)
            .build()

        assertTrue(path.fullPath.matches(Regex("prefix/project1/subjectA/topicX/[0-9]+_[a-z0-9-]+\\.txt")))
        assertTrue(path.pathInTopicDirectory.matches(Regex(SIMPLE_LOCAL_FILE_PATTERN)))
    }

    @Test
    fun testLowercaseExtension() {
        val path = StoragePath.builder()
            .filename("example.TXT")
            .projectId(PROJECT_ID)
            .subjectId(SUBJECT_ID)
            .topicId(TOPIC_ID)
            .build()

        assertTrue(path.fullPath.matches(Regex("project1/subjectA/topicX/[0-9]+_[a-z0-9-]+\\.txt")))
        assertTrue(path.pathInTopicDirectory.matches(Regex(SIMPLE_LOCAL_FILE_PATTERN)))
    }

    @Test
    fun testAllCombined() {
        val path = StoragePath.builder()
            .prefix(PREFIX)
            .filename("example.TXT")
            .projectId(PROJECT_ID)
            .subjectId(SUBJECT_ID)
            .topicId(TOPIC_ID)
            .collectPerDay(true)
            .build()

        assertTrue(path.fullPath.matches(Regex("prefix/project1/subjectA/topicX/[0-9]+/[0-9]+_[a-z0-9-]+\\.txt")))
        assertTrue(path.pathInTopicDirectory.matches(Regex("[0-9]+/[0-9]+_[a-z0-9-]+\\.txt")))
    }

    @Test
    fun testDotsInFileName() {
        val path = StoragePath.builder()
            .filename("example.com.txt")
            .projectId(PROJECT_ID)
            .subjectId(SUBJECT_ID)
            .topicId(TOPIC_ID)
            .build()

        assertTrue(path.fullPath.matches(Regex("project1/subjectA/topicX/[0-9]+_[a-z0-9-]+\\.txt")))
        assertTrue(path.pathInTopicDirectory.matches(Regex(SIMPLE_LOCAL_FILE_PATTERN)))
    }

    @Test
    fun testThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            StoragePath.builder()
                .projectId(PROJECT_ID)
                .subjectId(SUBJECT_ID)
                .topicId(TOPIC_ID)
                .build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            StoragePath.builder()
                .filename(FILENAME)
                .subjectId(SUBJECT_ID)
                .topicId(TOPIC_ID)
                .build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            StoragePath.builder()
                .filename(FILENAME)
                .projectId(PROJECT_ID)
                .topicId(TOPIC_ID)
                .build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            StoragePath.builder()
                .filename(FILENAME)
                .projectId(PROJECT_ID)
                .subjectId(SUBJECT_ID)
                .build()
        }
    }
}
