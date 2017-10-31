package org.radarcns.gateway.filter;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
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
import java.io.Writer;

public class AvroContentFilter implements Filter {
    private JsonFactory factory;
    private ServletContext context;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.context = filterConfig.getServletContext();
        this.context.log("AvroContentFilter initialized");

        this.factory = new JsonFactory();
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
            jsonErrorResponse(res, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type", "Only Avro JSON messages are supported");
            return;
        }

        DecodedJWT token = (DecodedJWT) request.getAttribute("jwt");
        if (token == null || token.getSubject() == null) {
            this.context.log("Request was not authenticated by a previous filter: "
                    + "no token attribute found or no user found");
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        try (ServletInputStream stream = request.getInputStream()) {
            final byte[] data = ServletInputStreamWrapper.readFully(stream);
            parseRequest(data, token);

            chain.doFilter(new HttpServletRequestWrapper(req) {
                @Override
                public ServletInputStream getInputStream() throws IOException {
                    return new ServletInputStreamWrapper(new ByteArrayInputStream(data));
                }
                public BufferedReader getReader() throws IOException {
                    return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data)));
                }
            }, response);
        } catch (JsonParseException ex) {
            jsonErrorResponse(res, HttpServletResponse.SC_BAD_REQUEST, "malformed_content", ex.getMessage());
        } catch (IOException ex) {
            context.log("IOException", ex);
            jsonErrorResponse(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "server_exception", "Failed to process message: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            jsonErrorResponse(res, 422, "invalid_content", ex.getMessage());
        }
    }

    @Override
    public void destroy() {
        this.context = null;
        this.factory = null;
    }

    /** Parse Kafka REST proxy payload. */
    private void parseRequest(byte[] data, DecodedJWT token) throws IOException {
        JsonParser parser = factory.createParser(data);

        boolean hasKeySchema = false;
        boolean hasValueSchema = false;

        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new JsonParseException(parser, "Expecting JSON object in payload");
        }
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            switch (parser.getCurrentName()) {
                case "key_schema_id": case "key_schema":
                    hasKeySchema = true;
                    parser.nextToken(); // value
                    break;
                case "value_schema_id": case "value_schema":
                    hasValueSchema = true;
                    parser.nextToken(); // value
                    break;
                case "records":
                    parseRecords(parser, token);
                    break;
                default:
                    skipToEndOfValue(parser);
            }
        }
        if (!hasKeySchema) {
            throw new IllegalArgumentException("Missing key schema");
        }
        if (!hasValueSchema) {
            throw new IllegalArgumentException("Missing value schema");
        }
    }

    private void parseRecords(JsonParser parser, DecodedJWT token) throws IOException {
        if (parser.nextToken() != JsonToken.START_ARRAY) {
            throw semanticException(parser, "Expecting JSON array for records field");
        }
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            boolean foundKey = false;
            boolean foundValue = false;

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.getCurrentName();
                if (fieldName.equals("key")) {
                    foundKey = true;
                    parseKey(parser, token);
                } else {
                    if (fieldName.equals("value")) {
                        foundValue = true;
                    }
                    skipToEndOfValue(parser);
                }
            }

            if (!foundKey) {
                throw semanticException(parser, "Missing key field in record");
            }
            if (!foundValue) {
                throw semanticException(parser, "Missing value field in record");
            }
        }
    }

    /** Parse single record key. */
    private void parseKey(JsonParser parser, DecodedJWT token) throws IOException {
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw semanticException(parser, "Field key must be a JSON object");
        }

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.getCurrentName().equals("userId")) {
                if (parser.nextToken() != JsonToken.VALUE_STRING) {
                    throw semanticException(parser, "userId field string");
                }
                String userId = parser.getValueAsString();
                if (!userId.equals(token.getSubject())) {
                    throw semanticException(parser, "record userID '" + userId + "' does not match authenticated user ID '" + token.getSubject() + '\'');
                }
            } else {
                skipToEndOfValue(parser);
            }
        }
    }

    /**
     * Skip to the last part of a value. This method assumes that the parser is now pointing to a
     * field name, and when it returns, it will point at the value if it is a simple type, or at the
     * end of an array or object if it is an array or object.
     */
    private void skipToEndOfValue(JsonParser parser) throws IOException {
        JsonToken nextToken = parser.nextToken();
        // skip nested contents
        if (nextToken == JsonToken.START_ARRAY || nextToken == JsonToken.START_OBJECT) {
            parser.skipChildren();
        }
    }

    /** Return a JSON error string. */
    private void jsonErrorResponse(HttpServletResponse response, int statusCode, String error,
            String errorDescription) throws IOException {
        response.setStatus(statusCode);
        response.setHeader("Content-Type", "application/json; charset=utf-8");
        try (Writer writer = response.getWriter()) {
            writer.write("{\"error_code\":");
            writer.write(Integer.toString(statusCode));
            writer.write(",\"message\":\"");
            writer.write(error);
            writer.write(": ");
            writer.write(errorDescription.replace("\n", "").replace("\"", "'"));
            writer.write("\"}");
        }
    }

    private IllegalArgumentException semanticException(JsonParser parser, String message) {
        JsonLocation location = parser.getCurrentLocation();
        return new IllegalArgumentException(message
                + " at [line " + location.getLineNr()
                + " column " + location.getColumnNr() + ']');
    }
}
