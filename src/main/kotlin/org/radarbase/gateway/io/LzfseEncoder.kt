package org.radarbase.gateway.io

import jakarta.ws.rs.core.Response
import org.glassfish.jersey.spi.ContentEncoder
import org.radarbase.io.lzfse.LZFSEInputStream
import org.radarbase.jersey.exception.HttpApplicationException
import java.io.InputStream
import java.io.OutputStream

class LzfseEncoder : ContentEncoder("lzfse", "x-lzfse") {
    override fun encode(
        contentEncoding: String,
        entityStream: OutputStream,
    ): OutputStream = throw HttpApplicationException(Response.Status.NOT_ACCEPTABLE, "LZFSE encoding not implemented")

    override fun decode(
        contentEncoding: String,
        encodedStream: InputStream,
    ): InputStream = LZFSEInputStream(encodedStream)
}
