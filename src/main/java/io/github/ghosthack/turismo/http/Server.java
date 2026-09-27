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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.github.ghosthack.turismo.App;
import io.github.ghosthack.turismo.Turismo;

/**
 * Embedded HTTP server backed by the JDK's built-in {@link HttpServer}.
 * Serves the routes of an {@link App}; usually started through
 * {@link App#start(int)} or {@link Turismo#start(int)}.
 *
 * <pre>{@code
 * import static io.github.ghosthack.turismo.Turismo.*;
 *
 * get("/hello", () -> print("Hello World"));
 * start(8080);
 * }</pre>
 *
 * <p>The server can also be used directly for more control:
 *
 * <pre>{@code
 * Server server = new Server(app, 8080);
 * server.start();
 * // ...
 * server.stop();
 * }</pre>
 *
 * <p>Each request is handled on its own virtual thread, so a slow or
 * blocking handler does not hold up other requests.
 *
 * <p>The server sets no request or response timeouts of its own. The
 * JDK server reads them from the {@code sun.net.httpserver.maxReqTime},
 * {@code sun.net.httpserver.maxRspTime} and
 * {@code sun.net.httpserver.idleInterval} system properties (all in
 * seconds), once per JVM; set them on the command line
 * ({@code -D...}) before the first server is created. See the README
 * for recommended values.
 *
 * @see Turismo#start(int)
 */
public class Server {

    private static final System.Logger LOG =
            System.getLogger(Server.class.getName());

    private final App app;
    private final HttpServer server;
    private final ExecutorService executor;

    /**
     * Creates a server for the {@linkplain Turismo#app() default app},
     * bound to the given port.
     *
     * @param port the port to listen on (use 0 for a random available port)
     * @throws IOException if the server socket cannot be created
     */
    public Server(int port) throws IOException {
        this(Turismo.app(), port);
    }

    /**
     * Creates a server for the given app, bound to the given port.
     *
     * @param app  the app whose routes are served
     * @param port the port to listen on (use 0 for a random available port)
     * @throws IOException if the server socket cannot be created
     * @throws IllegalArgumentException if app is null
     */
    public Server(App app, int port) throws IOException {
        if (app == null) {
            throw new IllegalArgumentException("app must not be null");
        }
        this.app = app;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/", this::handle);
        this.executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("turismo-", 0).factory());
        this.server.setExecutor(executor);
    }

    /**
     * Starts the server. This method returns immediately; the server
     * accepts connections on background threads.
     */
    public void start() {
        server.start();
    }

    /**
     * Stops the server immediately, closing all connections. Requests
     * still in progress are interrupted.
     */
    public void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    /**
     * Stops the server gracefully: new connections are refused at once,
     * requests in progress get up to {@code grace} to complete, and any
     * still running after that are interrupted.
     *
     * @param grace how long to wait for requests in progress
     * @throws IllegalArgumentException if grace is null or negative
     */
    public void stop(Duration grace) {
        if (grace == null || grace.isNegative()) {
            throw new IllegalArgumentException(
                    "grace must be zero or positive");
        }
        long deadline = System.nanoTime() + grace.toNanos();
        // HttpServer.stop takes whole seconds; round up so it never cuts
        // the grace period short, the executor wait below enforces it
        long seconds = grace.toSeconds() + (grace.toNanosPart() > 0 ? 1 : 0);
        server.stop((int) Math.min(seconds, Integer.MAX_VALUE));
        executor.shutdown();
        try {
            long remaining = deadline - System.nanoTime();
            if (remaining > 0) {
                executor.awaitTermination(remaining, TimeUnit.NANOSECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Returns the port the server is listening on. This is useful when
     * the server was created with port 0 to let the OS assign a free port.
     *
     * @return the port number
     */
    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) {
        HttpContext ctx = new HttpContext(exchange);
        try {
            try {
                app.handle(ctx);
            } catch (Throwable t) {
                // Catch Errors too, otherwise the client gets no response.
                // Log the raw path, escaped: a decoded %0A could forge
                // log lines
                LOG.log(System.Logger.Level.ERROR,
                        "Unhandled error in " + escape(ctx.method()) + " "
                        + escape(ctx.rawPath()), t);
                if (!ctx.isStreaming()) {
                    ctx.reset();
                    ctx.status(500);
                    ctx.print("Internal Server Error");
                }
                // Streaming: status and headers are already sent, so
                // just end the response below
            }
            ctx.finish();
        } catch (IOException ignored) {
            // Client may have disconnected
        } finally {
            exchange.close();
        }
    }

    /** Escapes control characters so request data can't break log lines. */
    static String escape(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                if (sb == null) {
                    sb = new StringBuilder(s.length() + 8);
                    sb.append(s, 0, i);
                }
                sb.append(String.format("\\u%04x", (int) c));
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb != null ? sb.toString() : s;
    }
}
