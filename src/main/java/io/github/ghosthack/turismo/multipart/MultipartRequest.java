package io.github.ghosthack.turismo.multipart;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wrapper that stores multipart form parameters.
 *
 * <p>The parameter methods merge the wrapped request's parameters (usually
 * the query string) with the text fields of the body, query values first.
 * Uploaded files are kept apart from text fields and are available through
 * {@link #getFile(String)} and {@link #getFiles(String)}.</p>
 *
 * <p>For compatibility, a name that has files but no text values reports
 * the first file's {@code [contentType, fileName]} as its parameter values,
 * and the first file's bytes are also set as a {@code byte[]} request
 * attribute under the field name.</p>
 *
 * <p>File names are client-supplied and untrusted; see {@link FilePart}.</p>
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

    private final Map<String, List<String>> fields = new LinkedHashMap<>();
    private final Map<String, List<FilePart>> files = new LinkedHashMap<>();
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
     * Returns the wrapped request's parameters followed by the multipart
     * text fields; see the class description for file fields.
     *
     * @see jakarta.servlet.ServletRequest#getParameterMap()
     * @return an unmodifiable map of the merged parameters
     */
    @Override
    public Map<String, String[]> getParameterMap() {
        final Map<String, String[]> map = new LinkedHashMap<>();
        for (String name : parameterNames()) {
            map.put(name, getParameterValues(name));
        }
        return Collections.unmodifiableMap(map);
    }

    /** @see jakarta.servlet.ServletRequest#getParameter(java.lang.String) */
    @Override
    public String getParameter(String name) {
        final String[] values = getParameterValues(name);
        return (values == null || values.length == 0) ? null : values[0];
    }

    /** @see jakarta.servlet.ServletRequest#getParameterNames() */
    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(parameterNames());
    }

    /**
     * Returns the wrapped request's values for {@code name} followed by the
     * multipart text values. When there are none and {@code name} is a file
     * field, returns the first file's {@code [contentType, fileName]}.
     *
     * @see jakarta.servlet.ServletRequest#getParameterValues(java.lang.String)
     */
    @Override
    public String[] getParameterValues(String name) {
        final String[] query = super.getParameterValues(name);
        final List<String> body = fields.get(name);
        if (body == null) {
            if (query != null)
                return query;
            final FilePart file = getFile(name);
            return file == null ? null
                    : new String[] { file.contentType(), file.fileName() };
        }
        final int offset = query == null ? 0 : query.length;
        final String[] values = new String[offset + body.size()];
        if (query != null)
            System.arraycopy(query, 0, values, 0, offset);
        for (int i = 0; i < body.size(); i++)
            values[offset + i] = body.get(i);
        return values;
    }

    private Set<String> parameterNames() {
        final Set<String> names = new LinkedHashSet<>();
        final Enumeration<String> query = super.getParameterNames();
        if (query != null) {
            while (query.hasMoreElements())
                names.add(query.nextElement());
        }
        names.addAll(fields.keySet());
        names.addAll(files.keySet());
        return names;
    }

    /**
     * Returns the first file uploaded under {@code name}.
     *
     * @param name the form field name
     * @return the file, or null if none was uploaded under that name
     */
    public FilePart getFile(String name) {
        final List<FilePart> list = files.get(name);
        return list == null ? null : list.get(0);
    }

    /**
     * Returns all files uploaded under {@code name}, in body order.
     *
     * @param name the form field name
     * @return an unmodifiable list, empty if none was uploaded
     */
    public List<FilePart> getFiles(String name) {
        final List<FilePart> list = files.get(name);
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    /**
     * Returns the names of the fields that received at least one file.
     *
     * @return an unmodifiable set of field names, in body order
     */
    public Set<String> getFileNames() {
        return Collections.unmodifiableSet(files.keySet());
    }

    /** @see Parametrizable#addParameter(String, String) */
    @Override
    public void addParameter(String name, String value) {
        fields.computeIfAbsent(name, k -> new ArrayList<>(1)).add(value);
    }

    /**
     * Replaces the multipart text values of {@code name}.
     *
     * @see Parametrizable#addParameter(String, String[])
     */
    @Override
    public void addParameter(String name, String[] value) {
        fields.put(name, new ArrayList<>(Arrays.asList(value)));
    }

    /**
     * Adds an uploaded file. The first file of each name is also set as a
     * {@code byte[]} request attribute under that name.
     *
     * @see Parametrizable#addFile(String, String, String, byte[])
     */
    @Override
    public void addFile(String name, String contentType, String fileName,
            byte[] content) {
        final List<FilePart> list = files.computeIfAbsent(name,
                k -> new ArrayList<>(1));
        list.add(new FilePart(contentType, fileName, content));
        if (list.size() == 1)
            setAttribute(name, content);
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
     * @throws ContentTooLargeException if the body is larger than
     *         {@link MultipartParser#getMaxContentSize()}
     * @throws ParseException if the multipart data cannot be parsed
     * @throws IOException if an I/O error occurs
     */
    public static MultipartRequest wrapAndParse(HttpServletRequest req, String defaultCharset)
            throws ParseException, IOException {
        final MultipartRequest multipart = new MultipartRequest(req);
        final String boundary = multipart.getBoundary();
        if (boundary == null) {
            throw new ParseException("Missing multipart/form-data boundary");
        }
        // Negative when unknown (chunked); the parser enforces the limit
        // on the bytes actually read
        final long size = req.getContentLengthLong();
        if (size > MultipartParser.getMaxContentSize()) {
            throw new ContentTooLargeException(size,
                    MultipartParser.getMaxContentSize());
        }
        String encoding = req.getCharacterEncoding();
        if (encoding == null) {
            encoding = defaultCharset;
        }
        try (InputStream is = req.getInputStream()) {
            final MultipartParser parser;
            try {
                parser = new MultipartParser(is, boundary, multipart,
                        encoding, size);
            } catch (IllegalArgumentException e) {
                throw new ParseException("Unsupported charset: " + encoding, e);
            }
            parser.parse();
        }
        return multipart;
    }

}
