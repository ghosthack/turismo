package io.github.ghosthack.turismo.multipart;

/**
 * Separates Request implementation from Parser.
 */
public interface Parametrizable {

    /**
     * Adds a string parameter.
     *
     * @param name  the parameter name
     * @param value the parameter value
     */
    void addParameter(String name, String value);

    /**
     * Adds a multi-valued parameter (used for file metadata).
     *
     * @param name  the parameter name
     * @param value the parameter values
     */
    void addParameter(String name, String[] value);

    /**
     * Adds a request attribute (used for file content bytes).
     *
     * @param name  the attribute name
     * @param value the attribute value
     */
    void setAttribute(String name, Object value);

    /**
     * Adds an uploaded file. Called once per file part, so a field may
     * receive several files.
     *
     * <p>The default implementation keeps the pre-5.0 contract: it adds
     * {@code [contentType, fileName]} as a parameter and the content as an
     * attribute, both under {@code name}.</p>
     *
     * @param name        the form field name
     * @param contentType the part's content type
     * @param fileName    the client-supplied file name (untrusted)
     * @param content     the file bytes
     */
    default void addFile(String name, String contentType, String fileName,
            byte[] content) {
        addParameter(name, new String[] { contentType, fileName });
        setAttribute(name, content);
    }

}
