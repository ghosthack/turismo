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
 * the correct {@code Content-Length} header automatically.
 *
 * @see Server
 */
public class HttpContext implements Context {

    private final HttpExchange exchange;
    private int statusCode = 200;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
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

    @Override
    public void status(int code) {
        this.statusCode = code;
    }

    @Override
    public void header(String name, String value) {
        exchange.getResponseHeaders().set(name, value);
    }

    @Override
    public void print(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        buffer.write(bytes, 0, bytes.length);
    }

    @Override
    public OutputStream output() {
        return buffer;
    }

    /**
     * Flushes the buffered response to the client and closes the
     * exchange. Must be called exactly once after the route action
     * has completed. No body is sent for HEAD requests or for status
     * codes that forbid one (1xx, 204, 304).
     *
     * @throws IOException if an I/O error occurs while sending
     */
    public void finish() throws IOException {
        byte[] body = buffer.toByteArray();
        // No body for HEAD (including HEAD served by a GET route) or for
        // status codes that forbid one
        boolean sendBody = body.length > 0 && mayHaveBody();
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
     * {@link #finish()}.
     */
    @Override
    public void reset() {
        buffer.reset();
        exchange.getResponseHeaders().clear();
    }

    private boolean mayHaveBody() {
        return !"HEAD".equals(method())
                && statusCode >= 200 && statusCode != 204 && statusCode != 304;
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
