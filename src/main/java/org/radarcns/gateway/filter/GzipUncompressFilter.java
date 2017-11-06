package org.radarcns.gateway.filter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;
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
import org.radarcns.gateway.util.ServletInputStreamWrapper;

public class GzipUncompressFilter implements Filter {

    private ServletContext context;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        context = filterConfig.getServletContext();
        context.log("GzipUncompressFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;

        String encoding = req.getHeader("Content-Encoding");
        if (encoding == null || encoding.equalsIgnoreCase("identity")) {
            chain.doFilter(request, response);
        } else if (encoding.equalsIgnoreCase("gzip")) {
            context.log("Decompressing input");
            chain.doFilter(new HttpServletRequestWrapper(req) {
                @Override
                public ServletInputStream getInputStream() throws IOException {
                    GZIPInputStream gzipStream = new GZIPInputStream(super.getInputStream());
                    return new ServletInputStreamWrapper(gzipStream);
                }

                @Override
                public BufferedReader getReader() throws IOException {
                    GZIPInputStream gzipStream = new GZIPInputStream(super.getInputStream());
                    return new BufferedReader(new InputStreamReader(gzipStream));
                }

                @Override
                public int getContentLength() {
                    return -1;
                }
            }, response);
        } else {
            HttpServletResponse res = (HttpServletResponse) response;
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    @Override
    public void destroy() {
        // nothing to destroy
    }
}
