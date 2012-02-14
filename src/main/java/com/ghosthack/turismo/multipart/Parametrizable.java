package com.ghosthack.turismo.multipart;

/**
 * Separates Request implementation from Parser.
 * 
 */
public interface Parametrizable {

    /**
     * Adds a parameter to the parameter map.
     * 
     * @param name
     * @param value
     */
    void addParameter(String name, String value);

    /**
     * Adds a parameter to the parameter map.
     * 
     * @param name
     * @param value
     */
    void addParameter(String name, String[] value);

    /**
     * Adds an attribute to the attribute map.
     * 
     * @param name
     * @param value
     */
    void setAttribute(String name, Object value);

}
