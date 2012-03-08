package com.ghosthack.turismo.multipart;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * Parses submitted multipart form data and creates a new request object.
 * <p>
 * If you have a custom Request object added to the filter chain, this filter
 * should be the first.
 * </p>
 * <p>
 * The file parameter data is added as a byte array attribute with the same
 * parameter name. <blockquote> Example:
 * 
 * <pre>
 *      &lt;input type="file" name="imageFile"/>
 * </pre>
 * 
 * File contents are be obtained via:
 * 
 * <pre>
 * <code>byte[] imageFileBytes = request.getAttribute("imageFile");</code>
 * </pre>
 * 
 * for later manipulation. </blockquote>
 * 
 * The file-name and content-type data sent by the browser are stored as a
 * String array in the request object. <blockquote> Example:
 * 
 * <pre>
 *      &lt;input type="file" name="imageFile"/>
 * </pre>
 * 
 * File name and content type are obtained like this:
 * 
 * <pre>
 * <code>String contentType = request.getParameter("imageFile")[0];</code>
 * <code>String fileName = request.getParameter("imageFile")[1];</code>
 * </pre>
 * 
 * </blockquote>
 * 
 * </p>
 * <p>
 * Configuration details:
 * 
 * <pre>
 *  &lt;filter>
 *      &lt;filter-name>multipart-filter&lt;/filter-name>
 *      &lt;filter-class>multipart.Filter&lt;/filter-class>
 *      &lt;init-param>
 *          &lt;param-name>charset-name&lt;/param-name>
 *          &lt;param-value>ISO-8859-1&lt;/param-value>
 *      &lt;/init-param>
 *  &lt;/filter>
 *  &lt;filter-mapping>
 *      &lt;filter-name>multipart-filter&lt;/filter-name>
 *      &lt;url-pattern>/eon/*&lt;/url-pattern>
 *  &lt;/filter-mapping>
 * </pre>
 * 
 * </p>
 * 
 */
public class MultipartFilter implements javax.servlet.Filter {

    private static final String CHARSET_NAME_PARAMETER = "charset-name";
    public static String CHARSET_NAME = "ISO-8859-1";

    // private static final java.util.logging.Logger LOG =
    // java.util.logging.Logger.getLogger(Filter.class.getName());

    /**
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
     *      javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        final String contentType = request.getContentType();
        if (contentType != null
                && contentType.startsWith(MultipartRequest.MULTIPART_FORM_DATA_BOUNDARY)) {
            final MultipartRequest multipartRequest;
            try {
              multipartRequest = MultipartRequest.wrapAndParse((HttpServletRequest) request);
            } catch (ParseException e) {
                dump(request);
                throw new ServletException(e);
            }
            chain.doFilter(multipartRequest, response);
        } else {
            chain.doFilter(request, response);
        }

    }

    private void dump(ServletRequest request) throws IOException {
        // final String data = new Dumper().dump(request.getInputStream(),
        // Filter.CHARSET_NAME);
        // LOG.info(data);
    }

    /**
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    public void init(FilterConfig config) throws ServletException {
        final String charsetName = config
                .getInitParameter(CHARSET_NAME_PARAMETER);
        if (charsetName != null) {
            MultipartFilter.CHARSET_NAME = charsetName;
        }
    }

    /**
     * @see javax.servlet.Filter#destroy()
     */
    public void destroy() {
        // nothing today
    }

}