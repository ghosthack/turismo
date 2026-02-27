/*
 * Created on May 2, 2004
 */
package com.ghosthack.turismo.multipart;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Partial implementation of <a
 * href="http://www.google.com/search?q=rfc1867">rfc1867</a>.
 * 
 * @author Adrian
 */
public final class MultipartParser {

    /** Default maximum content size: 10 MB */
    public static final int DEFAULT_MAX_CONTENT_SIZE = 10 * 1024 * 1024;

    private static final Charset US_ASCII = Charset.forName("US-ASCII");

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
     *            the size used to create the byte buffer, usually request content-length.
     */
    public MultipartParser(final InputStream is, final String boundary,
            final Parametrizable parameters, final String charsetName,
            final int size) {
        if (is == null || boundary == null || parameters == null
                || charsetName == null)
            throw new IllegalArgumentException();
        if (size <= 0)
            throw new IllegalArgumentException("Content size must be positive, was: " + size);
        if (size > maxContentSize)
            throw new IllegalArgumentException(
                    "Content size " + size + " exceeds maximum allowed size " + maxContentSize);
        this.is = new BufferedInputStream(is, BUFFER_SIZE);
        this.parameters = parameters;
        charsetDecoder = Charset.forName(charsetName).newDecoder();
        boundarySize = boundary.getBytes(US_ASCII).length;
        separator = (LINE_STRING + boundary).getBytes(US_ASCII);
        buffer = new byte[separator.length + OFFSET];
        bb = ByteBuffer.allocate(size);
    }

    /**
     * Parses the multipart form data.
     * 
     * @throws ParseException
     *             if end of stream is reached prematurely.
     * @throws java.io.IOException
     *             throwed by the underlying input stream.
     */
    public final void parse() throws ParseException, IOException {

        final long t0 = System.currentTimeMillis();

        skip(boundarySize + END_SIZE);

        do {

            skip(CONTENT_DISPOSITION_2_SIZE);

            final String name = decodeUntil(QUOTE);

            if (isFile = skipUntilAny(QUOTE, LINE)) {
                final String[] file = new String[FILE_DESC];
                file[NAME_POS] = decodeUntil(QUOTE);
                skip(CONTENT_TYPE_SIZE);
                file[CONTENT_TYPE_POS] = decodeUntil(LINE);
                parameters.addParameter(name, file);
            }

            skip(LINE_SIZE);
            readUntil(separator);
            readFully(eval);

            if (isFile) {
                parameters.setAttribute(name, bytes());
            } else {
                parameters.addParameter(name, decode());
            }

        } while (!Arrays.equals(eval, END));

        LOG.fine(PARSE_TIME + (System.currentTimeMillis() - t0));

    }

    private String decode() throws CharacterCodingException {
        charsetDecoder.reset();
        return charsetDecoder.decode(bb).toString();
    }

    private byte[] bytes() {
        final byte[] bytes = new byte[bb.remaining()];
        bb.get(bytes);
        return bytes;
    }

    private void read() throws ParseException, IOException {
        if ((bi = is.read()) == END_OF_STREAM)
            throw new ParseException("Unexpected end of stream while reading next byte");
        b = (byte) bi;
    }

    private void readFully(final byte[] bytes) throws ParseException, IOException {
        int offset = 0;
        int remaining = bytes.length;
        while (remaining > 0) {
            int read = is.read(bytes, offset, remaining);
            if (read == END_OF_STREAM)
                throw new ParseException(
                        "Unexpected end of stream: expected " + bytes.length
                        + " bytes but only read " + offset);
            offset += read;
            remaining -= read;
        }
    }

    private void skip(final long n) throws ParseException, IOException {
        long remaining = n;
        while (remaining > 0) {
            long skipped = is.skip(remaining);
            if (skipped == 0) {
                // skip(0) may mean EOF or no progress; try a single read to distinguish
                if (is.read() == END_OF_STREAM)
                    throw new ParseException(
                            "Unexpected end of stream: needed to skip " + n
                            + " bytes but could only skip " + (n - remaining));
                remaining--;
            } else {
                remaining -= skipped;
            }
        }
    }

    /**
     * Reads until the limit is met.
     */
    private void readUntil(final byte[] limit) throws ParseException,
            IOException {
        boolean coincidenceComplete = false;
        int coincidence = 0;
        bb.clear();
        try {
            while (!coincidenceComplete) {
                read();
                if (coincidence > 0 && b != limit[coincidence]) {
                    bb.put(buffer, 0, coincidence);
                    coincidence = 0;
                }
                if (b == limit[coincidence]) {
                    buffer[coincidence] = b;
                    coincidenceComplete = (++coincidence == limit.length);
                } else {
                    bb.put(b);
                }
            }
        } catch (BufferOverflowException e) {
            throw new ParseException("Multipart field value exceeds buffer capacity");
        }
        bb.flip();
    }

    private String decodeUntil(final byte[] limit) throws ParseException,
            IOException {
        readUntil(limit);
        return decode();
    }

    /**
     * Reads until one of the limits are met.
     * 
     * @return true when matched with the first limit.
     */
    private boolean skipUntilAny(final byte[] limit, final byte[] limit2)
            throws ParseException, IOException {
        boolean coincidenceComplete = false;
        boolean coincidenceComplete2 = false;
        int coincidenceNumber = 0;
        int coincidenceNumber2 = 0;
        while (!coincidenceComplete && !coincidenceComplete2) {
            read();
            if (coincidenceNumber > 0 && b != limit[coincidenceNumber])
                coincidenceNumber = 0;
            if (coincidenceNumber2 > 0 && b != limit2[coincidenceNumber2])
                coincidenceNumber2 = 0;

            if (b == limit[coincidenceNumber]) {
                coincidenceComplete = (++coincidenceNumber == limit.length);
            } else if (b == limit2[coincidenceNumber2]) {
                coincidenceComplete2 = (++coincidenceNumber2 == limit2.length);
            }
        }
        return coincidenceComplete;
    }

    private static final String LINE_STRING = "\r\n";
    private static final byte[] LINE = LINE_STRING.getBytes(US_ASCII);
    private static final int LINE_SIZE = LINE.length;
    private static final int CONTENT_TYPE_SIZE = LINE_SIZE
            + "Content-Type: ".getBytes(US_ASCII).length;
    private static final int CONTENT_DISPOSITION_SIZE = LINE_SIZE
            + "Content-Disposition: form-data; name=\"".getBytes(US_ASCII).length;
    private static final int BUFFER_SIZE = 10 * 1024;
    private static final byte[] QUOTE = "\"".getBytes(US_ASCII);
    private static final byte[] END = "--".getBytes(US_ASCII);
    private static final int END_SIZE = END.length;
    private static final int CONTENT_DISPOSITION_2_SIZE = CONTENT_DISPOSITION_SIZE
            - END_SIZE;
    private static final int OFFSET = 1;
    private static final int FILE_DESC = 2;
    private static final int CONTENT_TYPE_POS = 0;
    private static final int NAME_POS = 1;
    private static final int END_OF_STREAM = -1;
    private static final String PARSE_TIME = "parseTime[ms]: ";
    private static final Logger LOG = Logger.getLogger(MultipartParser.class.getName());

    private InputStream is;
    private Parametrizable parameters;
    private CharsetDecoder charsetDecoder;
    private int boundarySize;
    private byte[] separator;
    private byte[] buffer;

    private boolean isFile;

    private byte b;
    private int bi;
    private ByteBuffer bb;

    private final byte[] eval = new byte[END_SIZE];

}
