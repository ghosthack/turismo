package io.github.ghosthack.turismo.multipart;

/**
 * This exception is used by the multipart parser.
 * 
 * @see MultipartParser
 */
public class ParseException extends Exception {

    ParseException(String desc) {
        super(desc);
    }

    ParseException() {
        super();
    }

    ParseException(String desc, Throwable cause) {
        super(desc, cause);
    }

    ParseException(Throwable cause) {
        super(cause);
    }

    /**
     * Serializable implementation specific.
     */
    private static final long serialVersionUID = 1L;

}
