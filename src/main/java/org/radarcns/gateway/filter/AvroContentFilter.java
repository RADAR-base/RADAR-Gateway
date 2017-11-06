package org.radarcns.gateway.filter;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonParseException;
import org.radarcns.gateway.kafka.AvroValidator;
import org.radarcns.gateway.util.ServletInputStreamWrapper;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import static org.radarcns.gateway.kafka.AvroValidator.Util;

public class AvroContentFilter implements Filter {
    private ServletContext context;
    private AvroValidator validator;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.context = filterConfig.getServletContext();
        this.context.log("AvroContentFilter initialized");

        this.validator = new AvroValidator();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;

        // Only process POST requests
        if (!req.getMethod().equalsIgnoreCase("POST")) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletResponse res = (HttpServletResponse) response;

        if (!req.getContentType().startsWith("application/vnd.kafka.avro.v1+json")
                && !req.getContentType().startsWith("application/vnd.kafka.avro.v2+json")) {
            this.context.log("Got incompatible media type");
            Util.jsonErrorResponse(res, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "unsupported_media_type", "Only Avro JSON messages are supported");
            return;
        }

        DecodedJWT token = (DecodedJWT) request.getAttribute("jwt");
        if (token == null) {
            this.context.log("Request was not authenticated by a previous filter: "
                    + "no token attribute found or no user found");
            Util.jsonErrorResponse(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "server_error", "configuration error");
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        try (ServletInputStream stream = request.getInputStream()) {
            final byte[] data = ServletInputStreamWrapper.readFully(stream);
            validator.validate(data, token);

            chain.doFilter(new HttpServletRequestWrapper(req) {
                @Override
                public ServletInputStream getInputStream() throws IOException {
                    return new ServletInputStreamWrapper(
                            new ByteArrayInputStream(data));
                }

                @Override
                public BufferedReader getReader() throws IOException {
                    return new BufferedReader(new InputStreamReader(
                            new ByteArrayInputStream(data)));
                }
            }, response);
        } catch (JsonParseException ex) {
            Util.jsonErrorResponse(res, HttpServletResponse.SC_BAD_REQUEST, "malformed_content",
                    ex.getMessage());
        } catch (IOException ex) {
            context.log("IOException", ex);
            Util.jsonErrorResponse(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "server_exception",
                    "Failed to process message: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            Util.jsonErrorResponse(res, 422, "invalid_content", ex.getMessage());
        }
    }

    @Override
    public void destroy() {
        // nothing to destroy
    }
}
