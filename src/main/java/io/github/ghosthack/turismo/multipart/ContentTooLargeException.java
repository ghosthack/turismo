package io.github.ghosthack.turismo.multipart;

/**
 * Thrown when a multipart body is larger than
 * {@link MultipartParser#getMaxContentSize()}.
 */
public class ContentTooLargeException extends ParseException {

    ContentTooLargeException(long size, int max) {
        super("Content size " + size + " exceeds maximum allowed size " + max);
    }

    private static final long serialVersionUID = 1L;

}
