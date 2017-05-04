package org.radarcns.security.oauth;

import javax.servlet.ServletException;

/**
 * Created by joris on 04/05/2017.
 */
public class OAuth2AuthorizationException extends ServletException {
    private final String error;
    private final String errorDescription;

    public OAuth2AuthorizationException(String error, String errorDescription) {
        this(error, errorDescription, null);
    }

    public OAuth2AuthorizationException(String error, String errorDescription, Throwable cause) {
        super(error + ": " + errorDescription, cause);
        this.error = error;
        this.errorDescription = errorDescription;
    }

    public String getError() {
        return error;
    }

    public String getErrorDescription() {
        return errorDescription;
    }
}
