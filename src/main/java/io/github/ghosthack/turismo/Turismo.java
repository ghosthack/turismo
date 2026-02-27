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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.ghosthack.turismo.http.Server;

/**
 * Static facade for the turismo web framework. Provides a zero-dependency,
 * Sinatra/Express-style API for defining routes and handling HTTP requests
 * using the JDK's built-in HTTP server.
 *
 * <pre>{@code
 * import static io.github.ghosthack.turismo.Turismo.*;
 *
 * public class App {
 *     public static void main(String[] args) {
 *         get("/hello", () -> print("Hello World"));
 *         get("/users/:id", () -> print("User " + param("id")));
 *         start(8080);
 *     }
 * }
 * }</pre>
 *
 * <p>Routes support exact paths, named parameters ({@code :name}), and
 * wildcards ({@code *}). Named parameters are accessible via
 * {@link #param(String)}, which falls back to query string parameters.
 *
 * @see Context
 * @see io.github.ghosthack.turismo.http.Server
 */
public final class Turismo {

    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, String>> PATH_PARAMS =
            new ThreadLocal<>();

    private static final Map<String, Map<String, Runnable>> EXACT =
            new ConcurrentHashMap<>();
    private static final List<PatternRoute> PATTERNS =
            new CopyOnWriteArrayList<>();
    private static volatile Runnable NOT_FOUND = Turismo::defaultNotFound;
    private static volatile Server server;

    private Turismo() {
    }

    // ---------------------------------------------------------------
    // Route registration
    // ---------------------------------------------------------------

    /**
     * Registers a GET route.
     *
     * @param path   the URL path pattern
     * @param action the action to execute
     */
    public static void get(String path, Runnable action) {
        route("GET", path, action);
    }

    /**
     * Registers a POST route.
     *
     * @param path   the URL path pattern
     * @param action the action to execute
     */
    public static void post(String path, Runnable action) {
        route("POST", path, action);
    }

    /**
     * Registers a PUT route.
     *
     * @param path   the URL path pattern
     * @param action the action to execute
     */
    public static void put(String path, Runnable action) {
        route("PUT", path, action);
    }

    /**
     * Registers a DELETE route.
     *
     * @param path   the URL path pattern
     * @param action the action to execute
     */
    public static void delete(String path, Runnable action) {
        route("DELETE", path, action);
    }

    /**
     * Registers a PATCH route.
     *
     * @param path   the URL path pattern
     * @param action the action to execute
     */
    public static void patch(String path, Runnable action) {
        route("PATCH", path, action);
    }

    /**
     * Registers a HEAD route.
     *
     * @param path   the URL path pattern
     * @param action the action to execute
     */
    public static void head(String path, Runnable action) {
        route("HEAD", path, action);
    }

    /**
     * Registers an OPTIONS route.
     *
     * @param path   the URL path pattern
     * @param action the action to execute
     */
    public static void options(String path, Runnable action) {
        route("OPTIONS", path, action);
    }

    /**
     * Sets the handler for requests that match no registered route.
     *
     * @param action the not-found action
     */
    public static void notFound(Runnable action) {
        NOT_FOUND = action;
    }

    /**
     * Registers a route for a specific HTTP method and path pattern.
     * Paths containing {@code :} or {@code *} are treated as pattern
     * routes; all others are exact-match routes resolved in O(1).
     *
     * @param method the HTTP method (e.g. "GET")
     * @param path   the URL path pattern
     * @param action the action to execute
     */
    public static void route(String method, String path, Runnable action) {
        if (path.contains(":") || path.contains("*")) {
            PATTERNS.add(new PatternRoute(method, path, action));
        } else {
            EXACT.computeIfAbsent(method, k -> new ConcurrentHashMap<>())
                 .put(path, action);
        }
    }

    // ---------------------------------------------------------------
    // Request access
    // ---------------------------------------------------------------

    /**
     * Returns the current thread's {@link Context}.
     *
     * @return the context
     * @throws IllegalStateException if no context exists on the current thread
     */
    public static Context context() {
        Context ctx = CONTEXT.get();
        if (ctx == null) {
            throw new IllegalStateException(
                    "No context on current thread. "
                    + "Call Turismo.handle() or start a server first.");
        }
        return ctx;
    }

    /**
     * Returns the HTTP method of the current request.
     *
     * @return the request method
     */
    public static String method() {
        return context().method();
    }

    /**
     * Returns the path of the current request.
     *
     * @return the request path
     */
    public static String path() {
        return context().path();
    }

    /**
     * Returns a parameter value by name. Checks path parameters first
     * (e.g. {@code :id} in a route pattern), then falls back to query
     * string parameters.
     *
     * @param name the parameter name
     * @return the value, or {@code null} if not found
     */
    public static String param(String name) {
        Map<String, String> params = PATH_PARAMS.get();
        if (params != null) {
            String value = params.get(name);
            if (value != null) {
                return value;
            }
        }
        return context().query(name);
    }

    /**
     * Returns all path parameters as an unmodifiable map.
     *
     * @return the path parameters
     */
    public static Map<String, String> params() {
        Map<String, String> params = PATH_PARAMS.get();
        return params != null
                ? Collections.unmodifiableMap(params)
                : Collections.emptyMap();
    }

    /**
     * Returns a query string parameter by name.
     *
     * @param name the parameter name
     * @return the value, or {@code null} if not present
     */
    public static String query(String name) {
        return context().query(name);
    }

    /**
     * Returns a request header value by name.
     *
     * @param name the header name
     * @return the value, or {@code null} if not present
     */
    public static String header(String name) {
        return context().header(name);
    }

    /**
     * Returns the request body as an input stream.
     *
     * @return the request body
     */
    public static InputStream body() {
        return context().body();
    }

    // ---------------------------------------------------------------
    // Response
    // ---------------------------------------------------------------

    /**
     * Sets the response HTTP status code.
     *
     * @param code the status code
     */
    public static void status(int code) {
        context().status(code);
    }

    /**
     * Sets a response header.
     *
     * @param name  the header name
     * @param value the header value
     */
    public static void header(String name, String value) {
        context().header(name, value);
    }

    /**
     * Sets the response Content-Type header.
     *
     * @param contentType the content type (e.g. "text/html")
     */
    public static void type(String contentType) {
        context().header("Content-Type", contentType);
    }

    /**
     * Writes a string to the response body.
     *
     * @param text the text to write
     */
    public static void print(String text) {
        context().print(text);
    }

    /**
     * Returns the response output stream for writing binary data.
     *
     * @return the output stream
     */
    public static OutputStream output() {
        return context().output();
    }

    /**
     * Sends an HTTP 302 redirect to the given URL.
     *
     * @param url the redirect target
     * @throws IllegalArgumentException if the URL is null or contains CR/LF
     */
    public static void redirect(String url) {
        redirect(302, url);
    }

    /**
     * Sends a redirect with the given status code and URL.
     *
     * @param code the HTTP status code (e.g. 301, 302, 307)
     * @param url  the redirect target
     * @throws IllegalArgumentException if the URL is null or contains CR/LF
     */
    public static void redirect(int code, String url) {
        validateLocation(url);
        status(code);
        header("Location", url);
    }

    /**
     * Sends an HTTP 301 (Moved Permanently) redirect.
     *
     * @param url the new URL
     * @throws IllegalArgumentException if the URL is null or contains CR/LF
     */
    public static void movedPermanently(String url) {
        redirect(301, url);
    }

    /**
     * Sends an HTTP 404 (Not Found) response.
     */
    public static void notFound() {
        defaultNotFound();
    }

    // ---------------------------------------------------------------
    // Server lifecycle
    // ---------------------------------------------------------------

    /**
     * Starts an embedded HTTP server on the given port. Routes must be
     * registered before calling this method. The server runs on
     * background threads; the JVM will not exit while it is running.
     *
     * @param port the port to listen on (use 0 for a random available port)
     */
    public static void start(int port) {
        try {
            Server s = new Server(port);
            server = s;
            s.start();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to start server on port " + port, e);
        }
    }

    /**
     * Stops the embedded HTTP server, if one is running.
     */
    public static void stop() {
        Server s = server;
        if (s != null) {
            s.stop();
            server = null;
        }
    }

    /**
     * Returns the port the embedded server is listening on. Useful when
     * the server was started with port 0 (random available port).
     *
     * @return the port number
     * @throws IllegalStateException if no server is running
     */
    public static int port() {
        Server s = server;
        if (s == null) {
            throw new IllegalStateException("Server not started");
        }
        return s.port();
    }

    // ---------------------------------------------------------------
    // Framework
    // ---------------------------------------------------------------

    /**
     * Dispatches a request through the routing engine. Resolves the
     * route for the given context, sets up the thread-local environment,
     * and executes the matching action.
     *
     * <p>Transport adapters (such as {@link Server}) call this method
     * for each incoming request. Custom transport implementations can
     * use this to integrate with the turismo routing engine.
     *
     * @param ctx the request/response context
     */
    public static void handle(Context ctx) {
        RouteMatch match = resolve(ctx.method(), ctx.path());
        CONTEXT.set(ctx);
        PATH_PARAMS.set(match.params);
        try {
            match.action.run();
        } finally {
            CONTEXT.remove();
            PATH_PARAMS.remove();
        }
    }

    /**
     * Clears all registered routes and stops the server if running.
     * Intended for use in tests.
     */
    public static void reset() {
        stop();
        EXACT.clear();
        PATTERNS.clear();
        NOT_FOUND = Turismo::defaultNotFound;
    }

    // ---------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------

    static RouteMatch resolve(String method, String path) {
        // Exact match (O(1) HashMap lookup)
        Map<String, Runnable> methodRoutes = EXACT.get(method);
        if (methodRoutes != null) {
            Runnable action = methodRoutes.get(path);
            if (action != null) {
                return new RouteMatch(action, Collections.emptyMap());
            }
        }
        // Pattern match (linear scan)
        if (path != null) {
            String[] requestParts = path.split("/");
            for (PatternRoute pr : PATTERNS) {
                if (!pr.method.equals(method)) {
                    continue;
                }
                Map<String, String> params = pr.match(requestParts);
                if (params != null) {
                    return new RouteMatch(pr.action, params);
                }
            }
        }
        // Not found
        return new RouteMatch(NOT_FOUND, Collections.emptyMap());
    }

    private static void defaultNotFound() {
        status(404);
        print("Not Found");
    }

    static void validateLocation(String url) {
        if (url == null) {
            throw new IllegalArgumentException(
                    "Location must not be null");
        }
        if (url.indexOf('\r') >= 0 || url.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(
                    "Location must not contain CR or LF characters "
                    + "(possible header injection)");
        }
    }

    // ---------------------------------------------------------------
    // Inner classes
    // ---------------------------------------------------------------

    /** A resolved route with its extracted path parameters. */
    static class RouteMatch {
        final Runnable action;
        final Map<String, String> params;

        RouteMatch(Runnable action, Map<String, String> params) {
            this.action = action;
            this.params = params;
        }
    }

    /**
     * A route pattern that supports named parameters ({@code :name})
     * and wildcards ({@code *}).
     */
    static class PatternRoute {
        final String method;
        final String[] parts;
        final Map<String, Integer> paramNames;
        final Set<Integer> paramPositions;
        final Set<Integer> wildcardPositions;
        final Runnable action;

        PatternRoute(String method, String path, Runnable action) {
            this.method = method;
            this.action = action;
            this.parts = path.split("/");
            this.paramNames = new HashMap<>();
            this.paramPositions = new HashSet<>();
            this.wildcardPositions = new HashSet<>();
            for (int i = 0; i < parts.length; i++) {
                if ("*".equals(parts[i])) {
                    wildcardPositions.add(i);
                } else if (parts[i].startsWith(":")) {
                    paramNames.put(parts[i].substring(1), i);
                    paramPositions.add(i);
                }
            }
        }

        /**
         * Attempts to match the given request path segments against
         * this pattern. Returns extracted parameters on match, or
         * {@code null} if no match.
         */
        Map<String, String> match(String[] requestParts) {
            if (requestParts.length != parts.length) {
                return null;
            }
            for (int i = 0; i < parts.length; i++) {
                if (wildcardPositions.contains(i)
                        || paramPositions.contains(i)) {
                    continue;
                }
                if (!parts[i].equals(requestParts[i])) {
                    return null;
                }
            }
            if (paramNames.isEmpty()) {
                return Collections.emptyMap();
            }
            Map<String, String> params = new HashMap<>();
            for (Map.Entry<String, Integer> entry : paramNames.entrySet()) {
                params.put(entry.getKey(), requestParts[entry.getValue()]);
            }
            return params;
        }
    }
}
