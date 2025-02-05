package org.radarbase.gateway.resource

import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import org.glassfish.jersey.media.multipart.FormDataContentDisposition
import org.glassfish.jersey.media.multipart.FormDataParam
import org.radarbase.gateway.inject.ProcessFileUpload
import org.radarbase.gateway.service.storage.StorageService
import org.radarbase.gateway.utils.Paths.FILE_PATH
import org.radarbase.gateway.utils.Paths.PROJECT_PATH
import org.radarbase.gateway.utils.Paths.TOPIC_PATH
import org.radarbase.gateway.utils.Paths.UPLOAD_PATH
import org.radarbase.gateway.utils.Paths.USER_PATH
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URI

@Path("")
class FileUploadResource(
    @Context private val storageService: StorageService? = null,
) {
    @ProcessFileUpload
    @POST
    @Path("/$PROJECT_PATH/{projectId}/$USER_PATH/{subjectId}/$FILE_PATH/$TOPIC_PATH/{topicId}/$UPLOAD_PATH")
    fun uploadFileForSubject(
        @FormDataParam("file") fileInputStream: InputStream,
        @FormDataParam("file") fileInfo: FormDataContentDisposition,
        @PathParam("projectId") projectId: String,
        @PathParam("subjectId") subjectId: String,
        @PathParam("topicId") topicId: String,
    ): Response {
        if (storageService == null) {
            logger.debug("File uploading is disabled")
            return Response.noContent().build()
        }
        val filePath = storageService.store(fileInputStream, fileInfo.fileName, projectId, subjectId, topicId)
        logger.debug("Storing file for project: {}, subject: {}, topic: {}", projectId, subjectId, topicId)
        return Response.created(URI(filePath)).build()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileUploadResource::class.java)
    }
}
