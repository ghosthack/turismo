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
 *
 * <p>If you have a custom Request object added to the filter chain, this filter
 * should be the first.</p>
 *
 * <p>The file parameter data is added as a byte array attribute with the same
 * parameter name.</p>
 *
 * <blockquote>Example:
 * <pre>
 *      &lt;input type="file" name="imageFile"/&gt;
 * </pre>
 *
 * File contents are obtained via:
 * <pre>
 * byte[] imageFileBytes = (byte[]) request.getAttribute("imageFile");
 * </pre>
 * for later manipulation.</blockquote>
 *
 * <p>The file-name and content-type data sent by the browser are stored as a
 * String array in the request object.</p>
 *
 * <blockquote>Example:
 * <pre>
 *      &lt;input type="file" name="imageFile"/&gt;
 * </pre>
 *
 * File name and content type are obtained like this:
 * <pre>
 * String contentType = request.getParameterValues("imageFile")[0];
 * String fileName = request.getParameterValues("imageFile")[1];
 * </pre>
 * </blockquote>
 *
 * <p>Configuration details:</p>
 * <pre>
 *  &lt;filter&gt;
 *      &lt;filter-name&gt;multipart-filter&lt;/filter-name&gt;
 *      &lt;filter-class&gt;multipart.Filter&lt;/filter-class&gt;
 *      &lt;init-param&gt;
 *          &lt;param-name&gt;charset-name&lt;/param-name&gt;
 *          &lt;param-value&gt;ISO-8859-1&lt;/param-value&gt;
 *      &lt;/init-param&gt;
 *  &lt;/filter&gt;
 *  &lt;filter-mapping&gt;
 *      &lt;filter-name&gt;multipart-filter&lt;/filter-name&gt;
 *      &lt;url-pattern&gt;/eon/*&lt;/url-pattern&gt;
 *  &lt;/filter-mapping&gt;
 * </pre>
 */
public class MultipartFilter implements javax.servlet.Filter {

    /** Creates a new MultipartFilter with the default charset. */
    public MultipartFilter() {
    }

    private static final String CHARSET_NAME_PARAMETER = "charset-name";
    private static final String DEFAULT_CHARSET_NAME = "ISO-8859-1";

    private String charsetName = DEFAULT_CHARSET_NAME;

    /**
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
     *      javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        final String contentType = request.getContentType();
        if (contentType != null
                && contentType.toLowerCase(java.util.Locale.US).startsWith(MultipartRequest.MULTIPART_FORM_DATA)) {
            final MultipartRequest multipartRequest;
            try {
              multipartRequest = MultipartRequest.wrapAndParse(
                      (HttpServletRequest) request, charsetName);
            } catch (ParseException e) {
                throw new ServletException(e);
            }
            chain.doFilter(multipartRequest, response);
        } else {
            chain.doFilter(request, response);
        }

    }

    /**
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    @Override
    public void init(FilterConfig config) throws ServletException {
        final String configCharset = config
                .getInitParameter(CHARSET_NAME_PARAMETER);
        if (configCharset != null) {
            this.charsetName = configCharset;
        }
    }

    /**
     * Returns the charset name configured for this filter instance.
     *
     * @return the charset name
     */
    public String getCharsetName() {
        return charsetName;
    }

    /**
     * Returns the default charset name used when no filter configuration is available.
     *
     * @return the default charset name
     */
    public static String getDefaultCharsetName() {
        return DEFAULT_CHARSET_NAME;
    }

    /**
     * @see javax.servlet.Filter#destroy()
     */
    @Override
    public void destroy() {
        // nothing today
    }

}