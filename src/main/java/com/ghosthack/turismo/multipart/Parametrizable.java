package com.ghosthack.turismo.multipart;

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

}
