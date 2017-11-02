package org.radarcns.gateway.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.servlet.ServletInputStream;

/**
 * ServletInputStream wrapper for an InputStream.
 */
public class ServletInputStreamWrapper extends ServletInputStream {
    private final InputStream stream;

    public ServletInputStreamWrapper(InputStream stream) throws IOException {
        this.stream = stream;
    }

    @Override
    public int read() throws IOException {
        return stream.read();
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        return stream.read(buf, off, len);
    }

    @Override
    public long skip(long off) throws IOException {
        return stream.skip(off);
    }

    @Override
    public void mark(int readLimit) {
        stream.mark(readLimit);
    }

    @Override
    public boolean markSupported() {
        return stream.markSupported();
    }

    @Override
    public void reset() throws IOException {
        stream.reset();
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }

    /** read all bytes from a stream. Will not return on infinite streams. */
    public static byte[] readFully(InputStream stream) throws IOException {
        try (ByteArrayOutputStream dataStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int numRead;
            while ((numRead = stream.read(buffer, 0, buffer.length)) != -1) {
                dataStream.write(buffer, 0, numRead);
            }
            return dataStream.toByteArray();
        }
    }
}
