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
 * <p>Plain fields are added as string parameters and each file part is
 * passed to {@link Parametrizable#addFile}. File names are taken as sent:
 * a backslash is a literal character (browsers do not escape it) and only
 * {@code %22}, {@code %0D} and {@code %0A} are decoded, as browsers
 * percent-encode {@code "}, CR and LF. The resulting file name is
 * untrusted input: it may contain path separators or {@code ..}.
 *
 * <p>The body is read as it arrives, up to {@link #getMaxContentSize()}
 * bytes; memory is never reserved up front based on the declared
 * {@code Content-Length}. At most {@link #getMaxParts()} parts are
 * accepted.
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

    /** Default maximum number of parts: 1000 */
    public static final int DEFAULT_MAX_PARTS = 1000;

    private static volatile int maxContentSize = DEFAULT_MAX_CONTENT_SIZE;
    private static volatile int maxParts = DEFAULT_MAX_PARTS;

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

    /**
     * Sets the maximum number of parts (fields and files) a body may have.
     *
     * @param max the maximum number of parts (must be positive)
     */
    public static void setMaxParts(int max) {
        if (max <= 0)
            throw new IllegalArgumentException("maxParts must be positive");
        maxParts = max;
    }

    /**
     * Gets the maximum number of parts (fields and files) a body may have.
     *
     * @return the maximum number of parts
     */
    public static int getMaxParts() {
        return maxParts;
    }

    private final InputStream is;
    private final byte[] dashBoundary;
    private final byte[] delimiter;
    private final Parametrizable parameters;
    private final Charset charset;
    private final long size;

    // The body read so far; valid bytes are data[0, length)
    private byte[] data;
    private int length;

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
     *             {@link #getMaxContentSize()} or has more than
     *             {@link #getMaxParts()} parts
     * @throws ParseException if the body is not valid multipart data
     * @throws IOException if reading the body fails
     */
    public void parse() throws ParseException, IOException {
        final int max = maxContentSize;
        if (size > max)
            throw new ContentTooLargeException(size, max);
        final int partLimit = maxParts;
        readAll(max);

        int parts = 0;
        int pos = firstBoundary();
        while (true) {
            int p = pos + dashBoundary.length;
            if (startsWith(p, CLOSE))
                return; // close delimiter
            if (++parts > partLimit)
                throw new ContentTooLargeException("Multipart body has more than "
                        + partLimit + " parts");
            // Transport padding (RFC 2046) may follow the boundary
            while (p < length && (data[p] == ' ' || data[p] == '\t'))
                p++;
            if (!startsWith(p, CRLF))
                throw new ParseException("Malformed boundary line");
            p += CRLF.length;

            final int bodyStart;
            final String headerBlock;
            if (startsWith(p, CRLF)) {
                headerBlock = "";
                bodyStart = p + CRLF.length;
            } else {
                int headerEnd = indexOf(HEADER_END, p);
                if (headerEnd < 0)
                    throw new ParseException("Unterminated part headers");
                headerBlock = new String(data, p, headerEnd - p, charset);
                bodyStart = headerEnd + HEADER_END.length;
            }

            int next = indexOf(delimiter, bodyStart);
            if (next < 0)
                throw new ParseException("Missing closing boundary");
            addPart(headerBlock, bodyStart, next);
            pos = next + CRLF.length;
        }
    }

    /** Adds the part whose body is {@code data[from, to)}. */
    private void addPart(String headerBlock, int from, int to)
            throws ParseException {
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
            parameters.addFile(name, contentType != null
                    ? contentType : DEFAULT_FILE_CONTENT_TYPE, fileName,
                    Arrays.copyOfRange(data, from, to));
        } else {
            parameters.addParameter(name,
                    new String(data, from, to - from, charset));
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
     * lower-cased; quoted values may contain {@code ;}. Following the
     * HTML form encoding, a backslash is literal and only {@code %22},
     * {@code %0D} and {@code %0A} are decoded in quoted values.
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
                int close = value.indexOf('"', ++v);
                if (close < 0)
                    close = value.length();
                parsed = decodeQuoted(value.substring(v, close));
                v = close;
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

    /**
     * Decodes the percent escapes browsers use for {@code "}, CR and LF in
     * quoted form-data parameters; anything else is kept as is.
     */
    private static String decodeQuoted(String s) {
        if (s.indexOf('%') < 0)
            return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                String hex = s.substring(i + 1, i + 3).toUpperCase(Locale.ROOT);
                char decoded = switch (hex) {
                    case "22" -> '"';
                    case "0D" -> '\r';
                    case "0A" -> '\n';
                    default -> 0;
                };
                if (decoded != 0) {
                    sb.append(decoded);
                    i += 2;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private int firstBoundary() throws ParseException {
        if (startsWith(0, dashBoundary))
            return 0;
        // Otherwise skip the preamble up to the first CRLF + boundary
        int i = indexOf(delimiter, 0);
        if (i < 0)
            throw new ParseException("Multipart boundary not found");
        return i + CRLF.length;
    }

    /** Reads the whole body into {@link #data}, without a final copy. */
    private void readAll(int max) throws IOException, ContentTooLargeException {
        Buffer out = new Buffer(
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
        data = out.buffer();
        length = out.size();
    }

    /** Gives access to the internal buffer, avoiding {@code toByteArray}. */
    private static final class Buffer extends ByteArrayOutputStream {
        Buffer(int size) {
            super(size);
        }

        byte[] buffer() {
            return buf;
        }
    }

    private boolean startsWith(int from, byte[] prefix) {
        if (from < 0 || from + prefix.length > length)
            return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[from + i] != prefix[i])
                return false;
        }
        return true;
    }

    private int indexOf(byte[] pattern, int from) {
        int last = length - pattern.length;
        for (int i = Math.max(from, 0); i <= last; i++) {
            if (startsWith(i, pattern))
                return i;
        }
        return -1;
    }

}
