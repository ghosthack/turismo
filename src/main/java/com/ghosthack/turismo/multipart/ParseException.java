package com.ghosthack.turismo.multipart;

/**
 * This exception is used by the multipar parser
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

    /**
     * Serializable implementation specific.
     */
    private static final long serialVersionUID = 1L;

}
