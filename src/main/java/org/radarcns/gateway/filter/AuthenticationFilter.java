package org.radarcns.gateway.filter;

import com.auth0.jwt.interfaces.DecodedJWT;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.HttpHeaders;
import org.radarcns.auth.authentication.TokenValidator;
import org.radarcns.auth.authorization.Permission;
import org.radarcns.auth.authorization.RadarAuthorization;
import org.radarcns.auth.config.ServerConfig;
import org.radarcns.auth.config.YamlServerConfig;
import org.radarcns.auth.exception.NotAuthorizedException;
import org.radarcns.auth.exception.TokenValidationException;

/**
 * Created by dverbeec on 27/09/2017.
 */
public class AuthenticationFilter implements Filter {
    private ServletContext context;

    private static TokenValidator validator = null;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.context = filterConfig.getServletContext();
        this.context.log("Authentication filter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String token = getToken(request);
        HttpServletResponse res = (HttpServletResponse) response;
        if (token == null) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setHeader("WWW-Authenticate", "Bearer");
            return;
        }

        try {
            DecodedJWT jwt = getValidator(context).validateAccessToken(token);
            RadarAuthorization.checkPermission(jwt, Permission.MEASUREMENT_CREATE);
            request.setAttribute("jwt", jwt);
            chain.doFilter(request, response);
        } catch (TokenValidationException | NotAuthorizedException ex) {
            context.log("Failed to process token", ex);
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setHeader("WWW-Authenticate", "Bearer");
        }
    }

    private static synchronized TokenValidator getValidator(ServletContext context) {
        if (validator == null) {
            ServerConfig config = null;
            String mpUrlString = context.getInitParameter("managementPortalUrl");
            if (mpUrlString != null) {
                try {
                    YamlServerConfig cfg = new YamlServerConfig();
                    cfg.setPublicKeyEndpoint(new URI(mpUrlString + "/oauth/token_key"));
                    config = cfg;
                } catch (URISyntaxException e) {
                    context.log("Failed to load Management Portal URL " + mpUrlString, e);
                }
            }

            validator = config == null ? new TokenValidator() : new TokenValidator(config);
        }
        return validator;
    }

    @Override
    public void destroy() {
        // nothing to destroy
    }

    private String getToken(ServletRequest request) {
        HttpServletRequest req = (HttpServletRequest) request;
        String authorizationHeader = req.getHeader(HttpHeaders.AUTHORIZATION);

        // Check if the HTTP Authorization header is present and formatted correctly
        if (authorizationHeader == null
                || !authorizationHeader.toLowerCase(Locale.US).startsWith("bearer ")) {
            this.context.log("No authorization header provided in the request");
            return null;
        }

        // Extract the token from the HTTP Authorization header
        return authorizationHeader.substring("Bearer".length()).trim();
    }
}
