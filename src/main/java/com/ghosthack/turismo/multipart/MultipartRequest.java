package com.ghosthack.turismo.multipart;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

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

    /** Multipart form data boundary key */
    public static final String MULTIPART_FORM_DATA_BOUNDARY = "multipart/form-data; boundary=";

    private static final String BOUNDARY_HEAD = "--";
    private static final int MULTIPART_SIZE = MULTIPART_FORM_DATA_BOUNDARY
            .length();

    private final Map<String, String[]> parameterMap = new HashMap<String, String[]>();
    private String boundary;

    /** @see HttpServletRequestWrapper#HttpServletRequestWrapper(HttpServletRequest) */
    public MultipartRequest(HttpServletRequest servletRequest) {
        super(servletRequest);
        final String contentType = getContentType();
        if (contentType != null && contentType.length() > MULTIPART_SIZE) {
            boundary = BOUNDARY_HEAD + contentType.substring(MULTIPART_SIZE);
        }
    }

    /**
     * @see javax.servlet.ServletRequest#getParameterMap()
     * @return Map
     */
    public Map<String, String[]> getParameteMap() {
        return parameterMap;
    }

    /** @see javax.servlet.ServletRequest#getParameter(java.lang.String) */
    public String getParameter(String name) {
        final String[] values = getParameterValues(name);
        return (values == null) ? null : values[0];
    }

    /** @see javax.servlet.ServletRequest#getParameterNames() */
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(parameterMap.keySet());
    }

    /** @see javax.servlet.ServletRequest#getParameterValues(java.lang.String) */
    public String[] getParameterValues(String name) {
        return parameterMap.get(name);
    }

    /** @see Parametrizable#addParameter(String, String) */
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

    public static MultipartRequest wrapAndParse(HttpServletRequest req) throws ParseException, IOException {
        final MultipartRequest multipart = new MultipartRequest(req);
        final String boundary = multipart.getBoundary();
        final int size = req.getContentLength();
        String encoding = req.getCharacterEncoding();
        if(encoding == null) {
            encoding = MultipartFilter.CHARSET_NAME;
        }
        InputStream is = null;
        try {
            is = req.getInputStream();
            new MultipartParser(is, boundary, multipart, encoding, size).parse();
        } finally {
            if (is != null)
                is.close();
        }
        return multipart;
    }

}
