/*
 * Created on May 2, 2004
 */
package io.github.ghosthack.turismo.multipart;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parser for {@code multipart/form-data} request bodies
 * (<a href="https://www.rfc-editor.org/rfc/rfc7578">RFC 7578</a>).
 *
 * <p>Plain fields are added as string parameters. For file fields, the
 * content type and file name are added as a two-element parameter
 * ({@code [contentType, fileName]}) and the content as a {@code byte[]}
 * attribute, both under the field name.
 *
 * <p>The body is read as it arrives, up to {@link #getMaxContentSize()}
 * bytes; memory is never reserved up front based on the declared
 * {@code Content-Length}.
 *
 * @author Adrian
 */
public final class MultipartParser {

    /** Default maximum content size: 10 MB */
    public static final int DEFAULT_MAX_CONTENT_SIZE = 10 * 1024 * 1024;

    private static final String DEFAULT_FILE_CONTENT_TYPE =
            "application/octet-stream";
    private static final byte[] CRLF = {'\r', '\n'};
    private static final byte[] HEADER_END = {'\r', '\n', '\r', '\n'};
    private static final byte[] CLOSE = {'-', '-'};
    private static final int READ_CHUNK = 8 * 1024;
    private static final int CONTENT_TYPE_POS = 0;
    private static final int NAME_POS = 1;

    private static volatile int maxContentSize = DEFAULT_MAX_CONTENT_SIZE;

    /**
     * Sets the maximum allowed content size for multipart uploads.
     *
     * @param maxSize the maximum size in bytes (must be positive)
     */
    public static void setMaxContentSize(int maxSize) {
        if (maxSize <= 0)
            throw new IllegalArgumentException("maxContentSize must be positive");
        maxContentSize = maxSize;
    }

    /**
     * Gets the maximum allowed content size for multipart uploads.
     *
     * @return the maximum size in bytes
     */
    public static int getMaxContentSize() {
        return maxContentSize;
    }

    private final InputStream is;
    private final byte[] dashBoundary;
    private final byte[] delimiter;
    private final Parametrizable parameters;
    private final Charset charset;
    private final long size;

    /**
     * Constructs a new parser for multipart form data.
     *
     * @param is
     *            the byte input stream, can't be null.
     * @param boundary
     *            the complete boundary (including the extra starting "--"),
     *            can't be null.
     * @param parameters
     *            the container used to store parameters, can't be null.
     * @param charsetName
     *            the charset used to decode bytes as strings, can't be null.
     * @param size
     *            the declared content length, or a negative value if it is
     *            unknown (for example a chunked request).
     * @throws IllegalArgumentException if an argument is null or the
     *            charset is not supported
     */
    public MultipartParser(final InputStream is, final String boundary,
            final Parametrizable parameters, final String charsetName,
            final long size) {
        if (is == null || boundary == null || parameters == null
                || charsetName == null)
            throw new IllegalArgumentException();
        this.is = is;
        this.dashBoundary = boundary.getBytes(StandardCharsets.US_ASCII);
        this.delimiter = new byte[CRLF.length + dashBoundary.length];
        System.arraycopy(CRLF, 0, delimiter, 0, CRLF.length);
        System.arraycopy(dashBoundary, 0, delimiter, CRLF.length,
                dashBoundary.length);
        this.parameters = parameters;
        this.charset = Charset.forName(charsetName);
        this.size = size;
    }

    /**
     * Constructs a new parser for multipart form data.
     *
     * @param is          the byte input stream, can't be null.
     * @param boundary    the complete boundary (including the extra
     *                    starting "--"), can't be null.
     * @param parameters  the container used to store parameters, can't
     *                    be null.
     * @param charsetName the charset used to decode bytes as strings,
     *                    can't be null.
     * @param size        the declared content length, or a negative value
     *                    if it is unknown.
     * @see #MultipartParser(InputStream, String, Parametrizable, String, long)
     */
    public MultipartParser(final InputStream is, final String boundary,
            final Parametrizable parameters, final String charsetName,
            final int size) {
        this(is, boundary, parameters, charsetName, (long) size);
    }

    /**
     * Parses the body, adding its fields to the parameters container.
     *
     * @throws ContentTooLargeException if the body is larger than
     *             {@link #getMaxContentSize()}
     * @throws ParseException if the body is not valid multipart data
     * @throws IOException if reading the body fails
     */
    public void parse() throws ParseException, IOException {
        final int max = maxContentSize;
        if (size > max)
            throw new ContentTooLargeException(size, max);
        final byte[] data = readAll(max);

        int pos = firstBoundary(data);
        while (true) {
            int p = pos + dashBoundary.length;
            if (startsWith(data, p, CLOSE))
                return; // close delimiter
            // Transport padding (RFC 2046) may follow the boundary
            while (p < data.length && (data[p] == ' ' || data[p] == '\t'))
                p++;
            if (!startsWith(data, p, CRLF))
                throw new ParseException("Malformed boundary line");
            p += CRLF.length;

            final int bodyStart;
            final String headerBlock;
            if (startsWith(data, p, CRLF)) {
                headerBlock = "";
                bodyStart = p + CRLF.length;
            } else {
                int headerEnd = indexOf(data, HEADER_END, p);
                if (headerEnd < 0)
                    throw new ParseException("Unterminated part headers");
                headerBlock = new String(data, p, headerEnd - p, charset);
                bodyStart = headerEnd + HEADER_END.length;
            }

            int next = indexOf(data, delimiter, bodyStart);
            if (next < 0)
                throw new ParseException("Missing closing boundary");
            addPart(headerBlock, Arrays.copyOfRange(data, bodyStart, next));
            pos = next + CRLF.length;
        }
    }

    private void addPart(String headerBlock, byte[] body) throws ParseException {
        Map<String, String> headers = parseHeaders(headerBlock);
        String disposition = headers.get("content-disposition");
        if (disposition == null)
            throw new ParseException("Part without Content-Disposition");
        Map<String, String> params = parseParams(disposition);
        String name = params.get("name");
        if (name == null)
            return; // not a form field, ignore it
        String fileName = params.get("filename");
        if (fileName != null) {
            String contentType = headers.get("content-type");
            final String[] file = new String[2];
            file[CONTENT_TYPE_POS] = contentType != null
                    ? contentType : DEFAULT_FILE_CONTENT_TYPE;
            file[NAME_POS] = fileName;
            parameters.addParameter(name, file);
            parameters.setAttribute(name, body);
        } else {
            parameters.addParameter(name, new String(body, charset));
        }
    }

    private static Map<String, String> parseHeaders(String block) {
        Map<String, String> headers = new HashMap<>();
        if (block.isEmpty())
            return headers;
        for (String line : block.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim()
                        .toLowerCase(Locale.ROOT),
                        line.substring(colon + 1).trim());
            }
        }
        return headers;
    }

    /**
     * Parses the parameters of a header value such as
     * {@code form-data; name="a"; filename="b.txt"}. Parameter names are
     * lower-cased; quoted values may contain {@code ;} and escaped
     * characters.
     */
    static Map<String, String> parseParams(String value) {
        Map<String, String> params = new HashMap<>();
        int i = value.indexOf(';');
        while (i >= 0 && i < value.length()) {
            i++; // skip ';'
            int eq = value.indexOf('=', i);
            int semi = value.indexOf(';', i);
            if (eq < 0 || (semi >= 0 && semi < eq)) {
                i = semi;
                continue;
            }
            String key = value.substring(i, eq).trim().toLowerCase(Locale.ROOT);
            int v = eq + 1;
            while (v < value.length() && value.charAt(v) == ' ')
                v++;
            String parsed;
            if (v < value.length() && value.charAt(v) == '"') {
                StringBuilder sb = new StringBuilder();
                v++;
                while (v < value.length() && value.charAt(v) != '"') {
                    char c = value.charAt(v);
                    if (c == '\\' && v + 1 < value.length()) {
                        c = value.charAt(++v);
                    }
                    sb.append(c);
                    v++;
                }
                parsed = sb.toString();
                i = value.indexOf(';', v);
            } else {
                int end = value.indexOf(';', v);
                parsed = value.substring(v, end < 0 ? value.length() : end)
                        .trim();
                i = end;
            }
            params.putIfAbsent(key, parsed);
        }
        return params;
    }

    private int firstBoundary(byte[] data) throws ParseException {
        if (startsWith(data, 0, dashBoundary))
            return 0;
        // Otherwise skip the preamble up to the first CRLF + boundary
        int i = indexOf(data, delimiter, 0);
        if (i < 0)
            throw new ParseException("Multipart boundary not found");
        return i + CRLF.length;
    }

    private byte[] readAll(int max) throws IOException, ContentTooLargeException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(
                (int) Math.min(Math.max(size, 0), READ_CHUNK));
        byte[] chunk = new byte[READ_CHUNK];
        long total = 0;
        int n;
        while ((n = is.read(chunk)) != -1) {
            total += n;
            if (total > max)
                throw new ContentTooLargeException(total, max);
            out.write(chunk, 0, n);
        }
        return out.toByteArray();
    }

    private static boolean startsWith(byte[] data, int from, byte[] prefix) {
        if (from < 0 || from + prefix.length > data.length)
            return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[from + i] != prefix[i])
                return false;
        }
        return true;
    }

    private static int indexOf(byte[] data, byte[] pattern, int from) {
        int last = data.length - pattern.length;
        for (int i = Math.max(from, 0); i <= last; i++) {
            if (startsWith(data, i, pattern))
                return i;
        }
        return -1;
    }

}
