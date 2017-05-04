package org.radarcns.security.filter;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.radarcns.security.model.RadarUserToken;

public class AvroContentFilter implements Filter {
    private final JsonFactory factory;
    private ServletContext context;

    public AvroContentFilter() {
        factory = new JsonFactory();
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.context = filterConfig.getServletContext();
        this.context.log("AvroContentFilter initialized");
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

        RadarUserToken token = (RadarUserToken)request.getAttribute("token");
        if (token == null || token.getUser() == null) {
            this.context.log("Request was not authenticated by a previous filter: "
                    + "no token attribute found or no user found");
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        try (BufferedReader requestReader = request.getReader()) {
            parseRequest(requestReader, token);
        } catch (JsonParseException ex) {
            jsonErrorResponse(res, HttpServletResponse.SC_BAD_REQUEST, "malformed_content", ex.getMessage());
            return;
        } catch (IOException ex) {
            jsonErrorResponse(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "server_exception", "Failed to process message: " + ex.getMessage());
            return;
        } catch (IllegalArgumentException ex) {
            jsonErrorResponse(res, HttpServletResponse.SC_BAD_REQUEST, "invalid_content", ex.getMessage());
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }

    /** Parse Kafka REST proxy payload. */
    private void parseRequest(BufferedReader reader, RadarUserToken token) throws IOException {
        JsonParser parser = factory.createParser(reader);

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

    private void parseRecords(JsonParser parser, RadarUserToken token) throws IOException {
        if (parser.nextToken() != JsonToken.START_ARRAY) {
            throw new JsonParseException(parser, "Expecting JSON array for records field");
        }
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new JsonParseException(parser, "Expecting JSON object for record");
            }

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
                throw new JsonParseException(parser, "Missing key field in record");
            }
            if (!foundValue) {
                throw new JsonParseException(parser, "Missing value field in record");
            }
        }
    }

    /** Parse single record key. */
    private void parseKey(JsonParser parser, RadarUserToken token) throws IOException {
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new JsonParseException(parser, "Field key must be a JSON object");
        }
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.getCurrentName().equals("userId")) {
                if (parser.nextToken() != JsonToken.VALUE_STRING) {
                    throw new JsonParseException(parser, "userId field string");
                }
                String userId = parser.getValueAsString();
                if (userId.equals(token.getUser())) {
                    return;
                } else {
                    throw new IllegalArgumentException("Record userID " + userId + " does not match authenticated user ID " + token.getUser());
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
            writer.write("{\"error\":\"");
            writer.write(error);
            writer.write("\",\"errorDescription\":\"");
            writer.write(errorDescription);
            writer.write("\"}");
        }
    }
}
