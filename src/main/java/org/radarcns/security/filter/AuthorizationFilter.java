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
import org.radarcns.security.commons.exceptions.NotAuthorizedException;
import org.radarcns.security.commons.model.RadarToken;
import org.radarcns.security.wso2.Wso2AuthorizationHandler;

/**
 * Created by nivethika on 4-4-17.
 */
public class AuthorizationFilter implements Filter {
    private ServletContext context;
//    private OAuth2TokenValidationServiceStub stub;

//    Logger log = Logger.getLogger(AuthorizationFilter.class.getName());


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
//            log.error("No authorization header provided in the request");
            throw new ServletException("Authorization header must be provided");
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
        Wso2AuthorizationHandler handler = new Wso2AuthorizationHandler(config);
        RadarToken validatedToken = null;
        try {
            validatedToken =  handler.authorizeAccessToken(token);
        } catch (NotAuthorizedException e) {
            throw new SecurityException("Cannot authorize" , e);
        }

        System.out.println(validatedToken.getToken());
//        CarbonUtils.setBasicAccessSecurityHeaders(OAuth2ClientServlet.userName, OAuth2ClientServlet.password, true, stub._getServiceClient());
//        ServiceClient client = stub._getServiceClient();
//        Options options = client.getOptions();
//        options.setTimeOutInMilliSeconds(TIMEOUT_IN_MILLIS);
//        options.setProperty(HTTPConstants.SO_TIMEOUT, TIMEOUT_IN_MILLIS);
//        options.setProperty(HTTPConstants.CONNECTION_TIMEOUT, TIMEOUT_IN_MILLIS);
//        options.setCallTransportCleanup(true);
//        options.setManageSession(true);

        String uri = req.getRequestURI();
        this.context.log("Requested Resource::"+uri);
        chain.doFilter(request, response);
        System.out.println(uri);
    }

    @Override
    public void destroy() {

    }
}
