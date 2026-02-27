package io.github.ghosthack.turismo.multipart;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper that stores multipart form parameters.
 * 
 */
public class MultipartRequest extends HttpServletRequestWrapper implements
        Parametrizable {

    /** Multipart form data content type prefix */
    public static final String MULTIPART_FORM_DATA = "multipart/form-data";

    /**
     * @deprecated Use {@link #MULTIPART_FORM_DATA} and
     *             {@link #extractBoundary(String)} instead.
     */
    @Deprecated(since = "3.0.0", forRemoval = true)
    public static final String MULTIPART_FORM_DATA_BOUNDARY = "multipart/form-data; boundary=";

    private static final String BOUNDARY_HEAD = "--";

    private final Map<String, String[]> parameterMap = new HashMap<>();
    private String boundary;

    /**
     * Creates a new multipart request wrapper, extracting the boundary from the Content-Type header.
     *
     * @param servletRequest the original HTTP request
     */
    public MultipartRequest(HttpServletRequest servletRequest) {
        super(servletRequest);
        final String contentType = getContentType();
        if (contentType != null) {
            boundary = extractBoundary(contentType);
        }
    }

    /**
     * Extracts the boundary parameter from a Content-Type header value.
     * Handles quoted values and arbitrary parameter ordering per RFC 2046.
     *
     * @param contentType the Content-Type header value
     * @return the boundary (prefixed with "--"), or null if not found
     */
    static String extractBoundary(String contentType) {
        if (contentType == null) {
            return null;
        }
        // Must be multipart/form-data
        String lower = contentType.toLowerCase(java.util.Locale.US);
        if (!lower.startsWith(MULTIPART_FORM_DATA)) {
            return null;
        }
        // Parse parameters after the media type
        String rest = contentType.substring(MULTIPART_FORM_DATA.length());
        String[] parts = rest.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.toLowerCase(java.util.Locale.US).startsWith("boundary=")) {
                String value = trimmed.substring("boundary=".length()).trim();
                // Remove quotes if present
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                if (value.isEmpty()) {
                    return null;
                }
                return BOUNDARY_HEAD + value;
            }
        }
        return null;
    }

    /**
     * @see jakarta.servlet.ServletRequest#getParameterMap()
     * @return an unmodifiable view of the parameter map
     */
    @Override
    public Map<String, String[]> getParameterMap() {
        return Collections.unmodifiableMap(parameterMap);
    }

    /** @see jakarta.servlet.ServletRequest#getParameter(java.lang.String) */
    @Override
    public String getParameter(String name) {
        final String[] values = getParameterValues(name);
        return (values == null) ? null : values[0];
    }

    /** @see jakarta.servlet.ServletRequest#getParameterNames() */
    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(parameterMap.keySet());
    }

    /** @see jakarta.servlet.ServletRequest#getParameterValues(java.lang.String) */
    @Override
    public String[] getParameterValues(String name) {
        return parameterMap.get(name);
    }

    /** @see Parametrizable#addParameter(String, String) */
    @Override
    public void addParameter(String name, String value) {
        String[] prev = parameterMap.put(name, new String[] { value });
        if (prev != null) {
            int length = prev.length;
            length++;
            final String[] values = new String[length];
            System.arraycopy(prev, 0, values, 0, prev.length);
            values[prev.length] = value;
            parameterMap.put(name, values);
        }
    }

    /** @see Parametrizable#addParameter(String, String[]) */
    @Override
    public void addParameter(String name, String[] value) {
        parameterMap.put(name, value);
    }

    /**
     * Gets the boundary obtained from the underlying request.
     * 
     * @return boundary
     */
    public String getBoundary() {
        return boundary;
    }

    /**
     * Wraps the request and parses multipart data using the default charset.
     *
     * @param req the HTTP request
     * @return the wrapped multipart request
     * @throws ParseException if the multipart data cannot be parsed
     * @throws IOException if an I/O error occurs
     */
    public static MultipartRequest wrapAndParse(HttpServletRequest req) throws ParseException, IOException {
        return wrapAndParse(req, MultipartFilter.getDefaultCharsetName());
    }

    /**
     * Wraps the request and parses multipart data using the specified charset.
     *
     * @param req the HTTP request
     * @param defaultCharset the charset to use if the request has no encoding
     * @return the wrapped multipart request
     * @throws ParseException if the multipart data cannot be parsed
     * @throws IOException if an I/O error occurs
     */
    public static MultipartRequest wrapAndParse(HttpServletRequest req, String defaultCharset)
            throws ParseException, IOException {
        final MultipartRequest multipart = new MultipartRequest(req);
        final String boundary = multipart.getBoundary();
        final int size = req.getContentLength();
        if (size < 0) {
            throw new ParseException("Content-Length is missing or invalid");
        }
        String encoding = req.getCharacterEncoding();
        if (encoding == null) {
            encoding = defaultCharset;
        }
        InputStream is = req.getInputStream();
        try {
            new MultipartParser(is, boundary, multipart, encoding, size).parse();
        } finally {
            is.close();
        }
        return multipart;
    }

}
