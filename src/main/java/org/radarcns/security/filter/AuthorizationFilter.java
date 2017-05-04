package org.radarcns.security.filter;

import java.io.IOException;
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
import org.radarcns.security.commons.config.ServerConfig;
import org.radarcns.security.model.RadarUserToken;
import org.radarcns.security.oauth.OAuth2AuthorizationException;
import org.radarcns.security.oauth.OAuth2Handler;

/**
 * Created by nivethika on 4-4-17.
 */
public class AuthorizationFilter implements Filter {
    private ServletContext context;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.context = filterConfig.getServletContext();
        this.context.log("AuthorizationFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String authorizationHeader = req.getHeader(HttpHeaders.AUTHORIZATION);

        // Check if the HTTP Authorization header is present and formatted correctly
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            this.context.log("No authorization header provided in the request");
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setHeader("WWW-Authenticate", "Bearer");
            return;
        }

        // Extract the token from the HTTP Authorization header
        final String token = authorizationHeader.substring("Bearer".length()).trim();
        ServerConfig config = new ServerConfig();
        config.setHost("localhost");
        config.setPort(9443);
        config.setProtocol("https");
        config.setUsername("admin");
        config.setPassword("admin");
        config.setPath("oauth2/introspect");
        OAuth2Handler handler = new OAuth2Handler(config);
        RadarUserToken validatedToken;
        try {
            validatedToken = handler.authorizeAccessToken(token);
        } catch (OAuth2AuthorizationException e) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setHeader("WWW-Authenticate",
                    "Bearer error=\"" + e.getError() + "\", "
                            + "error_description=\"" + e.getErrorDescription() + "\"");
            return;
        } catch (IOException ex) {
            context.log("Failed to validate token", ex);
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        this.context.log("Token " + validatedToken);

        String uri = req.getRequestURI();
        req.setAttribute("token", validatedToken);
        this.context.log("Requested Resource: " + uri);
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {

    }
}
