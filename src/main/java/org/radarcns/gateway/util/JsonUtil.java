package org.radarcns.gateway.util;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;

public final class JsonUtil {

    private JsonUtil() {
        // utility class
    }

    /** Return a JSON error string. */
    public static void jsonErrorResponse(HttpServletResponse response, int statusCode, String error,
            String errorDescription) throws IOException {
        response.setStatus(statusCode);
        response.setHeader("Content-Type", "application/json; charset=utf-8");
        try (Writer writer = response.getWriter()) {
            writer.write("{\"error_code\":");
            writer.write(Integer.toString(statusCode));
            writer.write(",\"message\":\"");
            writer.write(error);
            writer.write(": ");
            writer.write(errorDescription
                    .replace("\n", "")
                    .replace("\"", "'"));
            writer.write("\"}");
        }
    }
}
