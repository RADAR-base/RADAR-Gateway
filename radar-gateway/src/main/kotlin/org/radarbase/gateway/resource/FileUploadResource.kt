package org.radarbase.gateway.resource

import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.container.AsyncResponse
import jakarta.ws.rs.container.Suspended
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.glassfish.jersey.media.multipart.FormDataContentDisposition
import org.glassfish.jersey.media.multipart.FormDataParam
import org.radarbase.auth.authorization.Permission
import org.radarbase.gateway.config.GatewayConfig
import org.radarbase.gateway.inject.ProcessFileUpload
import org.radarbase.gateway.service.storage.StorageService
import org.radarbase.gateway.service.storage.path.StoragePath
import org.radarbase.jersey.auth.Authenticated
import org.radarbase.jersey.auth.NeedsPermission
import org.radarbase.jersey.service.AsyncCoroutineService
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URI
import kotlin.time.Duration.Companion.seconds

@Path("")
class FileUploadResource(
    @Context private val storageService: StorageService? = null,
    @Context private val asyncService: AsyncCoroutineService,
    @Context config: GatewayConfig,
) {
    val timeout = config.server.requestTimeout.seconds

    @ProcessFileUpload
    @POST
    @Path("/{projectId}/{subjectId}/{topic}/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Authenticated
    @NeedsPermission(Permission.MEASUREMENT_CREATE, "projectId", "subjectId")
    fun uploadFileForSubject(
        @FormDataParam("file") fileInputStream: InputStream,
        @FormDataParam("file") fileInfo: FormDataContentDisposition,
        @PathParam("projectId") projectId: String,
        @PathParam("subjectId") subjectId: String,
        @PathParam("topic") topicId: String,
        @Suspended asyncResponse: AsyncResponse,
    ) {
        asyncService.runAsCoroutine(asyncResponse, timeout) {
            if (storageService != null) {
                val pathInfo = StoragePath(
                    filename = fileInfo.fileName,
                    projectId = projectId,
                    subjectId = subjectId,
                    topicId = topicId,
                )

                val filePath = storageService.store(
                    fileInputStream,
                    pathInfo,
                )
                logger.debug(
                    "Storing file for project: {}, subject: {}, topic: {}",
                    projectId,
                    subjectId,
                    topicId,
                )
                Response.created(URI(filePath)).build()
            } else {
                logger.debug("File uploading is disabled")
                Response.Status.NOT_FOUND
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileUploadResource::class.java)
    }
}
