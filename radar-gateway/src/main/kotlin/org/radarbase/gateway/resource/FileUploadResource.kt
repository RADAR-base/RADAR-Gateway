package org.radarbase.gateway.resource

import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.glassfish.jersey.media.multipart.FormDataContentDisposition
import org.glassfish.jersey.media.multipart.FormDataParam
import org.radarbase.gateway.inject.ProcessFileUpload
import org.radarbase.gateway.service.storage.StorageService
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URI

@Path("/upload")
class FileUploadResource(
    @Context private val storageService: StorageService? = null,
) {

    @ProcessFileUpload
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    fun uploadFileForSubject(
        @FormDataParam("file") fileInputStream: InputStream,
        @FormDataParam("file") fileInfo: FormDataContentDisposition,
        @FormDataParam("projectId") projectId: String,
        @FormDataParam("subjectId") subjectId: String,
        @FormDataParam("topicId") topicId: String,
    ): Response {
        if (storageService == null) {
            logger.debug("File uploading is disabled")
            return Response.noContent().build()
        }
        val filePath = storageService.store(
            fileInputStream,
            fileInfo.fileName,
            projectId,
            subjectId,
            topicId,
        )
        logger.debug(
            "Storing file for project: {}, subject: {}, topic: {}",
            projectId,
            subjectId,
            topicId,
        )
        return Response.created(URI(filePath)).build()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileUploadResource::class.java)
    }
}
