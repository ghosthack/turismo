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
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
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

import com.sun.net.httpserver.Headers;
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
 * <p>A response body whose handler set no {@code Content-Type} is sent as
 * {@value #DEFAULT_CONTENT_TYPE} if it was written with
 * {@link #print(String)} only, and as {@value #DEFAULT_BINARY_CONTENT_TYPE}
 * if the handler used {@link #output()} or {@link #stream()}. Every
 * response carries {@code X-Content-Type-Options: nosniff} unless the
 * handler set that header itself, so browsers hold a response to its
 * declared type: set it (for example {@code text/html}, {@code text/css}
 * or {@code image/png}) to send anything else.
 *
 * @see Server
 */
public class HttpContext implements Context {

    /**
     * Content type of a response whose handler set none and wrote the
     * body with {@link #print(String)} only.
     */
    public static final String DEFAULT_CONTENT_TYPE =
            "text/plain; charset=utf-8";

    /**
     * Content type of a response whose handler set none and wrote bytes
     * through {@link #output()} or {@link #stream()}: what they are is
     * unknown.
     */
    public static final String DEFAULT_BINARY_CONTENT_TYPE =
            "application/octet-stream";

    /**
     * Unread request bytes discarded so a keep-alive connection can
     * carry the next request. The JDK server itself only discards about
     * this much ({@code sun.net.httpserver.drainAmount}) and then waits
     * until the declared length has arrived, which swallows a pipelined
     * request. Anything larger is not waited on.
     */
    private static final int MAX_DISCARD_BYTES = 64 * 1024;

    /**
     * How long a response close may block while the JDK drains an unread
     * body before that read is abandoned. Long enough for the response
     * bytes already queued to be written, short of waiting out the body.
     */
    private static final long DRAIN_ABORT_MILLIS = 200;

    private final HttpExchange exchange;
    private int statusCode = 200;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private OutputStream stream;
    /** Whether the handler asked for the raw body stream. */
    private boolean rawOutput;
    /** Whether writing the streaming body to the client failed. */
    private boolean writeFailed;
    private Map<String, List<String>> queryParams;
    /** The request body, wrapped so a partial read can be finished here. */
    private RequestBody requestBody;
    /** Bytes of the request body the handler (or a discard) has read. */
    private long requestBodyRead;
    /** Whether the request body was read through to EOF. */
    private boolean requestBodyEof;
    /** The response must not leave this exchange on the keep-alive path. */
    private boolean dropConnection;
    /** {@link #closeExchange()} has already run. */
    private boolean exchangeClosed;

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
        if (requestBody == null) {
            requestBody = new RequestBody(exchange.getRequestBody());
        }
        return requestBody;
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

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if the response is already
     *         {@linkplain #stream() streaming}
     */
    @Override
    public void addHeader(String name, String value) {
        checkNotCommitted();
        exchange.getResponseHeaders().add(name, value);
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
        rawOutput = true;
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
     * connection without ending the chunked body, so the client sees a
     * failed transfer rather than a complete, shorter response. Calling
     * this again returns the same stream. For HEAD requests and statuses
     * without a body, the returned stream discards what is written.
     *
     * <p>Closing the returned stream only flushes it: the response ends
     * when the handler returns, so a {@code try}-with-resources block
     * that is left by an exception doesn't mark a partial body as
     * complete. A {@code Content-Length} header set by the handler is
     * dropped, since a chunked response must not carry one.
     *
     * @return the response body stream
     * @throws IOException if the headers can't be sent
     */
    public OutputStream stream() throws IOException {
        if (stream == null) {
            rawOutput = true;
            // Headers are sent now, before the handler returns. A body
            // that is already too large to discard cannot stay on a
            // keep-alive connection; a small one is discarded in finish
            // if the handler never reads it.
            if (unreadBodyTooLarge()) {
                exchange.getResponseHeaders().set("Connection", "close");
            }
            defaultHeaders(allowsBody());
            if (mayHaveBody()) {
                // The body is chunked, and a message with both framings
                // is invalid: proxies reject it or disagree on its end
                exchange.getResponseHeaders().remove("Content-Length");
                exchange.sendResponseHeaders(statusCode, 0);
                stream = new BodyStream(exchange.getResponseBody());
                buffer.writeTo(stream);
            } else if (unreadBodyTooLarge()) {
                // No response body, so the JDK closes the exchange as
                // soon as the headers are sent and would then wait for
                // this unread request body
                abortBlockingDrain(() ->
                        exchange.sendResponseHeaders(statusCode, -1));
                stream = OutputStream.nullOutputStream();
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
     * <p>A request body that was not read through to EOF is discarded
     * when the declared length is small enough to read safely, so the
     * connection can be reused. A larger remainder, including a chunked
     * body, is not waited on: the response carries
     * {@code Connection: close} and the socket is dropped. That covers
     * a 413, 405 or 404 and a successful handler that never reads the body.
     *
     * @throws IOException if an I/O error occurs while sending
     */
    public void finish() throws IOException {
        try {
            dropConnection = releaseUnreadBody();
            writeResponse();
        } finally {
            closeExchange();
        }
    }

    /**
     * Closes the exchange. Safe to call more than once. When the request
     * body is still unread and too large to discard, the JDK's drain is
     * not waited out: it would hold the connection until the declared
     * length arrived and a pipelined request would never be read.
     */
    void closeExchange() {
        if (exchangeClosed) {
            exchange.close();
            return;
        }
        exchangeClosed = true;
        try {
            if (dropConnection || unreadBodyTooLarge()) {
                abortBlockingDrain(exchange::close);
            } else {
                exchange.close();
            }
        } catch (IOException ignored) {
            // Client may have disconnected
        }
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
        rawOutput = false;
        buffer.reset();
        exchange.getResponseHeaders().clear();
    }

    /**
     * Headers a response gets unless the handler set them. A body without
     * a {@code Content-Type} would be sniffed by browsers, which turns
     * text that echoes request data into HTML; so printed text is sent as
     * plain text and raw bytes as an opaque download, and browsers are
     * told not to second-guess the declared type.
     */
    private void defaultHeaders(boolean hasBody) {
        Headers headers = exchange.getResponseHeaders();
        if (hasBody && !headers.containsKey("Content-Type")) {
            headers.set("Content-Type", rawOutput
                    ? DEFAULT_BINARY_CONTENT_TYPE : DEFAULT_CONTENT_TYPE);
        }
        if (!headers.containsKey("X-Content-Type-Options")) {
            headers.set("X-Content-Type-Options", "nosniff");
        }
    }

    /**
     * The request body handed to handlers. Closing it does not close the
     * JDK stream: that drain stops after about {@link #MAX_DISCARD_BYTES}
     * and then blocks until {@code Content-Length} is satisfied, so a
     * pipelined request is never read. {@link #finish()} discards a small
     * remainder or drops the connection instead.
     */
    private final class RequestBody extends FilterInputStream {
        RequestBody(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int n = in.read();
            note(n);
            return n;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = in.read(b, off, len);
            note(n);
            return n;
        }

        private void note(int n) {
            if (n < 0) {
                requestBodyEof = true;
            } else if (n > 0) {
                requestBodyRead += n;
            }
        }

        @Override
        public void close() {
            // See the class comment. The bytes stay available to finish().
        }
    }

    /**
     * The streaming response body handed to handlers. Closing it flushes
     * what was written but leaves the chunked body open, so that only
     * {@link #finish()} (a handler that returned normally) ends it.
     */
    private final class BodyStream extends FilterOutputStream {
        BodyStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            try {
                out.write(b);
            } catch (IOException e) {
                writeFailed = true;
                throw e;
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            try {
                // FilterOutputStream would write byte by byte
                out.write(b, off, len);
            } catch (IOException e) {
                writeFailed = true;
                throw e;
            }
        }

        @Override
        public void flush() throws IOException {
            try {
                out.flush();
            } catch (IOException e) {
                writeFailed = true;
                throw e;
            }
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }

    /**
     * Whether a write to the {@linkplain #stream() streaming} response
     * failed, which means the client went away: the handler error that
     * follows is not a fault of the application.
     */
    boolean writeFailed() {
        return writeFailed;
    }

    /**
     * Sends what a {@linkplain #stream() streaming} handler wrote so far,
     * before the server drops the connection of a failed response.
     */
    void flushStream() {
        try {
            stream.flush();
        } catch (IOException ignored) {
            // Client may have disconnected
        }
    }

    /**
     * Sends the status and headers, then the buffered or streaming body.
     * When {@link #dropConnection} is set, {@code Connection: close} is
     * written with the headers and the unread request body is not drained.
     */
    private void writeResponse() throws IOException {
        if (stream != null) {
            // Not `stream`, whose close() only flushes. Closing writes
            // the chunk trailer and then drains the request body.
            OutputStream raw = exchange.getResponseBody();
            if (dropConnection) {
                abortBlockingDrain(raw::close);
            } else {
                raw.close();
            }
            return;
        }
        byte[] body = buffer.toByteArray();
        // No body for HEAD (including HEAD served by a GET route) or for
        // status codes that forbid one
        boolean sendBody = body.length > 0 && mayHaveBody();
        defaultHeaders(body.length > 0 && allowsBody());
        if (dropConnection) {
            exchange.getResponseHeaders().set("Connection", "close");
        }
        if (!sendBody && body.length > 0 && isHead() && allowsBody()) {
            // The JDK server takes a HEAD length only from the headers
            exchange.getResponseHeaders().set("Content-Length",
                    Integer.toString(body.length));
        }
        if (!sendBody) {
            // A length of -1 makes the JDK close the exchange itself,
            // which drains the request body, so that close has to be
            // the one that is abandoned
            if (dropConnection) {
                abortBlockingDrain(() ->
                        exchange.sendResponseHeaders(statusCode, -1));
            } else {
                exchange.sendResponseHeaders(statusCode, -1);
            }
            return;
        }
        exchange.sendResponseHeaders(statusCode, body.length);
        OutputStream os = exchange.getResponseBody();
        os.write(body);
        if (dropConnection) {
            // The response is already on the wire. Closing would block
            // in the JDK's request-body drain; an interrupt makes that
            // read abort and the socket drop.
            abandonUnreadRequest(os);
        } else {
            os.close();
        }
    }

    /**
     * Discards a small unread remainder, or decides the connection has
     * to be closed. Returns {@code true} when the body was not read
     * through to EOF and must not be waited on.
     */
    private boolean releaseUnreadBody() throws IOException {
        if (requestBodyEof) {
            return false;
        }
        Long declared = declaredBodyLength();
        if (declared == null) {
            return true;
        }
        long remaining = declared - requestBodyRead;
        if (remaining <= 0) {
            return false;
        }
        if (remaining > MAX_DISCARD_BYTES) {
            return true;
        }
        discard(remaining);
        return !requestBodyEof;
    }

    /** A declared length still unread, or a chunked body not read to EOF. */
    private boolean unreadBodyTooLarge() {
        if (requestBodyEof) {
            return false;
        }
        Long declared = declaredBodyLength();
        if (declared == null) {
            return true;
        }
        return declared - requestBodyRead > MAX_DISCARD_BYTES;
    }

    /**
     * The declared length, {@code 0} when the request has no body, or
     * {@code null} for a chunked body (no length to discard safely).
     */
    private Long declaredBodyLength() {
        Headers headers = exchange.getRequestHeaders();
        String encoding = headers.getFirst("Transfer-Encoding");
        if (encoding != null && encoding.equalsIgnoreCase("chunked")) {
            return null;
        }
        String length = headers.getFirst("Content-Length");
        if (length == null || length.isBlank()) {
            return 0L;
        }
        try {
            long n = Long.parseLong(length.trim());
            return n < 0 ? null : n;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Reads the rest of a fixed-length body, including the read that
     * reports EOF, so the bytes of a pipelined request stay unread.
     */
    private void discard(long remaining) throws IOException {
        InputStream in = body();
        byte[] buf = new byte[8192];
        while (remaining > 0) {
            int n = in.read(buf, 0, (int) Math.min(buf.length, remaining));
            if (n < 0) {
                return;
            }
            if (n == 0) {
                throw new IOException("Unable to discard the request body");
            }
            remaining -= n;
        }
        in.read();
    }

    /**
     * Runs a close that writes the response and then drains the request
     * body. If the drain is still blocked after {@link #DRAIN_ABORT_MILLIS}
     * the read is interrupted, which drops the socket instead of waiting
     * for the client to finish sending.
     */
    private void abortBlockingDrain(IOAction action) throws IOException {
        Thread self = Thread.currentThread();
        Thread watchdog = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(DRAIN_ABORT_MILLIS);
            } catch (InterruptedException e) {
                return;
            }
            self.interrupt();
        });
        boolean shutdown = false;
        try {
            action.run();
        } finally {
            watchdog.interrupt();
            try {
                watchdog.join();
            } catch (InterruptedException e) {
                shutdown = true;
            }
            // Drop the flag the drain abort set. A shutdown interrupt
            // that arrived while waiting for the watchdog is kept.
            Thread.interrupted();
            if (shutdown) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Closes a response whose bytes are already written, without draining. */
    private void abandonUnreadRequest(OutputStream os) throws IOException {
        boolean already = Thread.currentThread().isInterrupted();
        if (!already) {
            Thread.currentThread().interrupt();
        }
        try {
            os.close();
        } finally {
            if (!already) {
                Thread.interrupted();
            }
        }
    }

    @FunctionalInterface
    private interface IOAction {
        void run() throws IOException;
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
