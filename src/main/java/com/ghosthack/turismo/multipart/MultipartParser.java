/*
 * Created on May 2, 2004
 */
package com.ghosthack.turismo.multipart;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
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
        this.is = new BufferedInputStream(is, BUFFER_SIZE);
        this.parameters = parameters;
        charsetDecoder = Charset.forName(charsetName).newDecoder();
        boundarySize = boundary.getBytes().length;
        separator = (LINE_STRING + boundary).getBytes();
        buffer = new byte[separator.length + OFFSET];
        // Allocates the full file size in memmory, will throw OOME if memory is
        // not enough
        // Beware: there is a bug in 1.4.2_04 and earlier versions
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4879883
        bb = ByteBuffer.allocateDirect(size);
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
            read(eval);

            if (isFile) {
                parameters.setAttribute(name, bytes());
            } else {
                parameters.addParameter(name, decode());
            }

        } while (!Arrays.equals(eval, END));

        LOG.fine(PARSE_TIME + (System.currentTimeMillis() - t0));

    }

    private String decode() throws CharacterCodingException {
        return charsetDecoder.decode(bb).toString();
    }

    private byte[] bytes() {
        final byte[] bytes = new byte[bb.remaining()];
        bb.get(bytes);
        return bytes;
    }

    private void read() throws ParseException, IOException {
        if ((bi = is.read()) == END_OF_STREAM)
            throw new ParseException();
        b = (byte) bi;
    }

    private void read(final byte[] bytes) throws ParseException, IOException {
        if (is.read(bytes) == END_OF_STREAM)
            throw new ParseException();
    }

    private void skip(final long n) throws ParseException, IOException {
        if (n != is.skip(n))
            throw new ParseException();
    }

    /**
     * Reads until the limit is met.
     */
    private void readUntil(final byte[] limit) throws ParseException,
            IOException {
        boolean coincidenceComplete = false;
        int coincidence = 0;
        bb.clear();
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
    private static final byte[] LINE = LINE_STRING.getBytes();
    private static final int LINE_SIZE = LINE.length;
    private static final int CONTENT_TYPE_SIZE = LINE_SIZE
            + "Content-Type: ".getBytes().length;
    private static final int CONTENT_DISPOSITION_SIZE = LINE_SIZE
            + "Content-Disposition: form-data; name=\"".getBytes().length;
    private static final int BUFFER_SIZE = 10 * 1024;
    private static final byte[] QUOTE = "\"".getBytes();
    private static final byte[] END = "--".getBytes();
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
