/*
 * Copyright (c) 2011 Adrian Fernandez
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.github.ghosthack.turismo;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Transport-neutral HTTP request/response context. Provides access to the
 * current request data and response controls without coupling to a specific
 * server implementation such as the Servlet API.
 *
 * <p>Implementations wrap a transport-specific exchange object:
 * <ul>
 *   <li>{@link io.github.ghosthack.turismo.http.HttpContext} wraps
 *       {@code com.sun.net.httpserver.HttpExchange}</li>
 * </ul>
 *
 * @see Turismo
 */
public interface Context {

    /**
     * Returns the HTTP method (GET, POST, PUT, etc.).
     *
     * @return the request method
     */
    String method();

    /**
     * Returns the request path without the query string.
     *
     * @return the request path
     */
    String path();

    /**
     * Returns a query string parameter value by name.
     *
     * @param name the parameter name
     * @return the value, or {@code null} if not present
     */
    String query(String name);

    /**
     * Returns a request header value by name.
     *
     * @param name the header name
     * @return the value, or {@code null} if not present
     */
    String header(String name);

    /**
     * Returns the request body as an input stream.
     *
     * @return the request body stream
     */
    InputStream body();

    /**
     * Sets the response HTTP status code.
     *
     * @param code the status code
     */
    void status(int code);

    /**
     * Sets a response header. Replaces any existing value for the
     * given header name.
     *
     * @param name  the header name
     * @param value the header value
     */
    void header(String name, String value);

    /**
     * Writes a string to the response body using UTF-8 encoding.
     *
     * @param text the text to write
     */
    void print(String text);

    /**
     * Returns the response body as an output stream for writing
     * binary data.
     *
     * @return the response output stream
     */
    OutputStream output();
}
