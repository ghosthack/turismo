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

package io.github.ghosthack.turismo.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

import io.github.ghosthack.turismo.Context;

/**
 * {@link Context} implementation backed by {@link HttpExchange} from the
 * JDK's built-in HTTP server ({@code jdk.httpserver} module).
 *
 * <p>Response output is buffered internally and flushed to the client
 * when {@link #finish()} is called. This allows the framework to set
 * the correct {@code Content-Length} header automatically. Large
 * responses can opt into {@linkplain #stream() streaming} instead.
 *
 * @see Server
 */
public class HttpContext implements Context {

    private final HttpExchange exchange;
    private int statusCode = 200;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private OutputStream stream;
    private Map<String, List<String>> queryParams;

    /**
     * Creates a context wrapping the given HTTP exchange.
     *
     * @param exchange the HTTP exchange from the JDK HTTP server
     */
    public HttpContext(HttpExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public String method() {
        return exchange.getRequestMethod();
    }

    @Override
    public String path() {
        return exchange.getRequestURI().getPath();
    }

    @Override
    public String rawPath() {
        return exchange.getRequestURI().getRawPath();
    }

    @Override
    public String query(String name) {
        List<String> values = queryValues(name);
        return values.isEmpty() ? null : values.get(0);
    }

    @Override
    public List<String> queryValues(String name) {
        if (queryParams == null) {
            queryParams = parseQuery(exchange.getRequestURI().getRawQuery());
        }
        return queryParams.getOrDefault(name, List.of());
    }

    @Override
    public String header(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    @Override
    public InputStream body() {
        return exchange.getRequestBody();
    }

    /**
     * Sets the response status code.
     *
     * @param code the status code, from 200 to 599; informational (1xx)
     *        codes can't be sent as a final response
     * @throws IllegalArgumentException if code is outside 200-599
     * @throws IllegalStateException if the response is already
     *         {@linkplain #stream() streaming}
     */
    @Override
    public void status(int code) {
        if (code < 200 || code > 599) {
            throw new IllegalArgumentException(
                    "Status code must be between 200 and 599: " + code);
        }
        checkNotCommitted();
        this.statusCode = code;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if the response is already
     *         {@linkplain #stream() streaming}
     */
    @Override
    public void header(String name, String value) {
        checkNotCommitted();
        exchange.getResponseHeaders().set(name, value);
    }

    @Override
    public void print(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (stream != null) {
            try {
                stream.write(bytes);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            buffer.write(bytes, 0, bytes.length);
        }
    }

    /**
     * Returns the response body stream: the in-memory buffer, or the
     * client connection once the response is {@linkplain #stream()
     * streaming}.
     */
    @Override
    public OutputStream output() {
        return stream != null ? stream : buffer;
    }

    /**
     * Switches the response to streaming: sends the status and headers
     * now and returns a stream that writes the body straight to the
     * client, using chunked transfer encoding. Use it for large or
     * incremental responses that shouldn't be held in memory. Anything
     * already written to the buffer is sent first; later
     * {@link #print(String)} and {@link #output()} write to the stream.
     *
     * <p>After this call the status and headers can no longer change
     * ({@link #status(int)} and {@link #header(String, String)} throw
     * {@link IllegalStateException}), and an error in the handler can't
     * be turned into a 500 response: the server logs it and closes the
     * connection. Calling this again returns the same stream. For HEAD
     * requests and statuses without a body, the returned stream
     * discards what is written.
     *
     * @return the response body stream
     * @throws IOException if the headers can't be sent
     */
    public OutputStream stream() throws IOException {
        if (stream == null) {
            if (mayHaveBody()) {
                exchange.sendResponseHeaders(statusCode, 0);
                stream = exchange.getResponseBody();
                buffer.writeTo(stream);
            } else {
                exchange.sendResponseHeaders(statusCode, -1);
                stream = OutputStream.nullOutputStream();
            }
            buffer.reset();
        }
        return stream;
    }

    /**
     * Returns whether the response is {@linkplain #stream() streaming},
     * meaning the status and headers have been sent.
     *
     * @return {@code true} once {@link #stream()} has been called
     */
    public boolean isStreaming() {
        return stream != null;
    }

    /**
     * Flushes the buffered response to the client (or ends a
     * {@linkplain #stream() streaming} one) and closes the exchange.
     * Must be called exactly once after the route action has completed.
     * No body is sent for HEAD requests or for status codes that forbid
     * one (204, 304); a HEAD response still carries the
     * {@code Content-Length} its GET would have.
     *
     * @throws IOException if an I/O error occurs while sending
     */
    public void finish() throws IOException {
        if (stream != null) {
            try {
                stream.close();
            } finally {
                exchange.close();
            }
            return;
        }
        byte[] body = buffer.toByteArray();
        // No body for HEAD (including HEAD served by a GET route) or for
        // status codes that forbid one
        boolean sendBody = body.length > 0 && mayHaveBody();
        if (!sendBody && body.length > 0 && isHead() && allowsBody()) {
            // The JDK server takes a HEAD length only from the headers
            exchange.getResponseHeaders().set("Content-Length",
                    Integer.toString(body.length));
        }
        exchange.sendResponseHeaders(statusCode,
                sendBody ? body.length : -1);
        if (sendBody) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
        exchange.close();
    }

    /**
     * Discards the output and response headers written so far. Nothing
     * has been sent yet, since the response is buffered until
     * {@link #finish()}. Does nothing once the response is
     * {@linkplain #stream() streaming}, since it has been sent.
     */
    @Override
    public void reset() {
        if (stream != null) {
            return;
        }
        buffer.reset();
        exchange.getResponseHeaders().clear();
    }

    private void checkNotCommitted() {
        if (stream != null) {
            throw new IllegalStateException(
                    "Response is streaming; status and headers were sent");
        }
    }

    private boolean isHead() {
        return "HEAD".equals(method());
    }

    private boolean allowsBody() {
        return statusCode != 204 && statusCode != 304;
    }

    private boolean mayHaveBody() {
        return !isHead() && allowsBody();
    }

    private static Map<String, List<String>> parseQuery(String query) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = decode(eq < 0 ? pair : pair.substring(0, eq));
            String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
            params.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        params.replaceAll((k, v) -> Collections.unmodifiableList(v));
        return params;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
