package io.github.ghosthack.turismo.multipart;

/**
 * Thrown when a multipart body is larger than
 * {@link MultipartParser#getMaxContentSize()} or has more parts than
 * {@link MultipartParser#getMaxParts()}.
 */
public class ContentTooLargeException extends ParseException {

    ContentTooLargeException(long size, int max) {
        super("Content size " + size + " exceeds maximum allowed size " + max);
    }

    ContentTooLargeException(String desc) {
        super(desc);
    }

    private static final long serialVersionUID = 1L;

}
