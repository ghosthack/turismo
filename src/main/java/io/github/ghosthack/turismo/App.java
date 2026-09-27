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

import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.ghosthack.turismo.annotation.DELETE;
import io.github.ghosthack.turismo.annotation.GET;
import io.github.ghosthack.turismo.annotation.PATCH;
import io.github.ghosthack.turismo.annotation.POST;
import io.github.ghosthack.turismo.annotation.PUT;
import io.github.ghosthack.turismo.http.Server;

/**
 * A set of routes, a not-found handler and (optionally) the embedded
 * server that serves them. Each instance is independent, so several apps
 * can run in one JVM, and a test can build a fresh app instead of
 * resetting shared state.
 *
 * <pre>{@code
 * import static io.github.ghosthack.turismo.Turismo.*;
 *
 * App app = new App();
 * app.get("/hello", () -> print("Hello World"));
 * app.start(8080);
 * }</pre>
 *
 * <p>Handlers read the request and write the response through the static
 * helpers in {@link Turismo} ({@code param()}, {@code print()},
 * {@code json()}, ...), which work for whichever app is serving the
 * current request. The static registration methods in {@link Turismo}
 * ({@code get()}, {@code start()}, ...) act on a shared default app,
 * {@link Turismo#app()}.
 *
 * <p>Routes can be registered from any thread, including while the server
 * is running.
 *
 * @see Turismo
 */
public class App {

    private final Map<String, Map<String, Runnable>> exact =
            new ConcurrentHashMap<>();
    private final List<PatternRoute> patterns = new CopyOnWriteArrayList<>();
    /** Default for {@link #setMaxFormSize}: 2 MB. */
    static final int DEFAULT_MAX_FORM_SIZE = 2 * 1024 * 1024;

    private volatile Runnable notFound = Turismo::defaultNotFound;
    private volatile int maxFormSize = DEFAULT_MAX_FORM_SIZE;
    private Server server;

    /** Creates an app with no routes. */
    public App() {
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
    public void get(String path, Runnable action) {
        route("GET", path, action);
    }

    /**
     * Registers a GET route that returns a fixed string body.
     *
     * @param path the URL path pattern
     * @param body the response body text
     */
    public void get(String path, String body) {
        route("GET", path, () -> Turismo.print(body));
    }

    /**
     * Registers a POST route.
     *
     * @param path   the URL path pattern
     * @param action the action to execute
     */
    public void post(String path, Runnable action) {
        route("POST", path, action);
    }

    /**
     * Registers a POST route that returns a fixed string body.
     *
     * @param path the URL path pattern
     * @param body the response body text
     */
    public void post(String path, String body) {
        route("POST", path, () -> Turismo.print(body));
    }

    /**
     * Registers a PUT route.
     *
     * @param path   the URL path pattern
     * @param action the action to execute
     */
    public void put(String path, Runnable action) {
        route("PUT", path, action);
    }

    /**
     * Registers a PUT route that returns a fixed string body.
     *
     * @param path the URL path pattern
     * @param body the response body text
     */
    public void put(String path, String body) {
        route("PUT", path, () -> Turismo.print(body));
    }

    /**
     * Registers a DELETE route.
     *
     * @param path   the URL path pattern
     * @param action the action to execute
     */
    public void delete(String path, Runnable action) {
        route("DELETE", path, action);
    }

    /**
     * Registers a DELETE route that returns a fixed string body.
     *
     * @param path the URL path pattern
     * @param body the response body text
     */
    public void delete(String path, String body) {
        route("DELETE", path, () -> Turismo.print(body));
    }

    /**
     * Registers a PATCH route.
     *
     * @param path   the URL path pattern
     * @param action the action to execute
     */
    public void patch(String path, Runnable action) {
        route("PATCH", path, action);
    }

    /**
     * Registers a PATCH route that returns a fixed string body.
     *
     * @param path the URL path pattern
     * @param body the response body text
     */
    public void patch(String path, String body) {
        route("PATCH", path, () -> Turismo.print(body));
    }

    /**
     * Registers a HEAD route. Without one, HEAD requests are served by
     * the matching GET route (the response body is discarded).
     *
     * @param path   the URL path pattern
     * @param action the action to execute
     */
    public void head(String path, Runnable action) {
        route("HEAD", path, action);
    }

    /**
     * Registers an OPTIONS route.
     *
     * @param path   the URL path pattern
     * @param action the action to execute
     */
    public void options(String path, Runnable action) {
        route("OPTIONS", path, action);
    }

    /**
     * Sets the handler for requests that match no registered route.
     *
     * @param action the not-found action
     * @throws IllegalArgumentException if action is null
     */
    public void notFound(Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        this.notFound = action;
    }

    /**
     * Sets the largest {@code application/x-www-form-urlencoded} request
     * body that {@link Turismo#form(String)} and {@link Turismo#param(String)}
     * will read; larger ones are answered with {@code 413 Content Too
     * Large}. The default is 2 MB.
     *
     * @param bytes the limit in bytes
     * @throws IllegalArgumentException if bytes is negative or
     *         {@code Integer.MAX_VALUE}
     */
    public void setMaxFormSize(int bytes) {
        if (bytes < 0 || bytes == Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "max form size out of range: " + bytes);
        }
        this.maxFormSize = bytes;
    }

    /**
     * Registers a route for a specific HTTP method and path pattern.
     * Paths containing {@code :} or {@code *} are treated as pattern
     * routes; all others are exact-match routes resolved in O(1).
     *
     * @param method the HTTP method (e.g. "GET")
     * @param path   the URL path pattern
     * @param action the action to execute
     * @throws IllegalArgumentException if any argument is null or the
     *         path does not start with {@code /}
     */
    public void route(String method, String path, Runnable action) {
        if (method == null || method.isEmpty()) {
            throw new IllegalArgumentException("method must not be empty");
        }
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException(
                    "path must start with '/': " + path);
        }
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (path.contains(":") || path.contains("*")) {
            patterns.add(new PatternRoute(method, path, action));
        } else {
            exact.computeIfAbsent(method, k -> new ConcurrentHashMap<>())
                 .put(path, action);
        }
    }

    // ---------------------------------------------------------------
    // Controller registration
    // ---------------------------------------------------------------

    /**
     * Registers an annotated controller instance. Scans the instance's
     * class for methods annotated with {@link GET @GET}, {@link POST @POST},
     * {@link PUT @PUT}, {@link DELETE @DELETE}, or {@link PATCH @PATCH},
     * and registers each as a route. Annotated methods declared in
     * superclasses are included; an annotated override in a subclass
     * replaces the superclass's route.
     *
     * <p>Route method arguments are bound from the request: path, query
     * and form parameters by name (see {@link io.github.ghosthack.turismo.annotation.Param
     * @Param}), converted to {@code String}, primitives and their
     * wrappers, enums or {@code UUID}; a {@link Context} argument gets the
     * request context and an {@code InputStream} argument the request
     * body. A parameter value that can't be converted, or is missing for
     * a primitive argument, is answered with {@code 400 Bad Request}.
     *
     * <pre>{@code
     * public class MyController {
     *     @GET("/hello")
     *     void hello() {
     *         Turismo.print("Hello!");
     *     }
     *
     *     @GET("/items/:id")
     *     void item(@Param("id") int id) {
     *         Turismo.print("item: " + id);
     *     }
     * }
     *
     * app.controller(new MyController());
     * }</pre>
     *
     * @param instance the controller instance
     * @throws IllegalArgumentException if the instance has no annotated
     *         methods, or an annotated method has an argument that can't
     *         be bound
     */
    public void controller(Object instance) {
        int count = 0;
        // Names of registered overridable methods: a subclass's annotated
        // override replaces the superclass's route instead of adding to it.
        Set<String> registered = new HashSet<>();
        for (Class<?> c = instance.getClass(); c != null && c != Object.class;
                c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.isSynthetic() || m.isBridge()) {
                    continue;
                }
                boolean overridable = !Modifier.isPrivate(m.getModifiers())
                        && !Modifier.isStatic(m.getModifiers());
                if (overridable && registered.contains(m.getName())) {
                    continue;
                }
                boolean annotated = false;
                for (Annotation a : m.getDeclaredAnnotations()) {
                    String httpMethod = httpMethod(a);
                    if (httpMethod == null) {
                        continue;
                    }
                    route(httpMethod, routePath(a),
                            new RouteMethod(instance, m));
                    annotated = true;
                    count++;
                }
                if (annotated && overridable) {
                    registered.add(m.getName());
                }
            }
        }
        if (count == 0) {
            throw new IllegalArgumentException(
                    "No annotated routes found in "
                    + instance.getClass().getName());
        }
    }

    private static String httpMethod(Annotation a) {
        if (a instanceof GET) return "GET";
        if (a instanceof POST) return "POST";
        if (a instanceof PUT) return "PUT";
        if (a instanceof DELETE) return "DELETE";
        if (a instanceof PATCH) return "PATCH";
        return null;
    }

    private static String routePath(Annotation a) {
        if (a instanceof GET g) return g.value();
        if (a instanceof POST p) return p.value();
        if (a instanceof PUT p) return p.value();
        if (a instanceof DELETE d) return d.value();
        if (a instanceof PATCH p) return p.value();
        throw new IllegalArgumentException("Not a route annotation: " + a);
    }

    // ---------------------------------------------------------------
    // Server lifecycle
    // ---------------------------------------------------------------

    /**
     * Starts an embedded HTTP server on the given port. Routes must be
     * registered before calling this method. The server runs on
     * background threads; the JVM will not exit while it is running.
     * Each request is handled on its own virtual thread.
     *
     * @param port the port to listen on (use 0 for a random available port)
     * @throws IllegalStateException if a server is already running
     */
    public synchronized void start(int port) {
        if (server != null) {
            throw new IllegalStateException(
                    "Server already running on port " + server.port()
                    + "; call stop() first");
        }
        Server s;
        try {
            s = new Server(this, port);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to start server on port " + port, e);
        }
        try {
            s.start();
        } catch (RuntimeException e) {
            s.stop();
            throw new RuntimeException(
                    "Failed to start server on port " + port, e);
        }
        server = s;
    }

    /**
     * Stops the embedded HTTP server gracefully, if one is running:
     * new connections are refused and requests in progress get up to
     * {@code grace} to complete before being interrupted.
     *
     * @param grace how long to wait for requests in progress
     * @throws IllegalArgumentException if grace is null or negative
     */
    public synchronized void stop(Duration grace) {
        Server s = server;
        if (s != null) {
            s.stop(grace);
            server = null;
        }
    }

    /**
     * Stops the embedded HTTP server immediately, if one is running.
     * Requests still in progress are interrupted; use
     * {@link #stop(Duration)} to let them finish.
     */
    public synchronized void stop() {
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
    public synchronized int port() {
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
    public void handle(Context ctx) {
        RouteMatch match = resolve(ctx.method(), ctx.path(), ctx.rawPath());
        Object[] previous = Turismo.bind(ctx, match.params, maxFormSize);
        try {
            match.action.run();
        } catch (RequestException e) {
            ctx.status(e.status());
            ctx.print(e.getMessage());
        } finally {
            Turismo.restore(previous);
        }
    }

    /**
     * Clears all registered routes, restores the default not-found
     * handler and form size limit, and stops the server if running.
     */
    public void reset() {
        stop();
        exact.clear();
        patterns.clear();
        notFound = Turismo::defaultNotFound;
        maxFormSize = DEFAULT_MAX_FORM_SIZE;
    }

    // ---------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------

    RouteMatch resolve(String method, String path) {
        return resolve(method, path, null);
    }

    /**
     * Resolves a route. Pattern routes are matched against the segments
     * of {@code rawPath} (each percent-decoded on its own, so an encoded
     * {@code /} stays inside its segment) when it is available, otherwise
     * against the segments of the decoded {@code path}.
     */
    RouteMatch resolve(String method, String path, String rawPath) {
        String[] segments = segments(path, rawPath);
        // An encoded slash is part of a segment, not a separator, so the
        // decoded path must not be used to look up an exact route: that
        // would let /admin%2Fsecret reach the /admin/secret route
        String exactPath = hasEncodedSlash(rawPath) ? null : path;
        RouteMatch match = find(method, exactPath, segments);
        if (match == null && "HEAD".equals(method)) {
            match = find("GET", exactPath, segments);
        }
        if (match != null) {
            return match;
        }
        Set<String> allowed = allowedMethods(exactPath, segments);
        if (!allowed.isEmpty()) {
            return new RouteMatch(() -> methodNotAllowed(allowed),
                    Collections.emptyMap());
        }
        return new RouteMatch(notFound, Collections.emptyMap());
    }

    private RouteMatch find(String method, String path,
            String[] segments) {
        // Exact match (O(1) HashMap lookup)
        Map<String, Runnable> methodRoutes = exact.get(method);
        if (methodRoutes != null && path != null) {
            Runnable action = methodRoutes.get(path);
            if (action != null) {
                return new RouteMatch(action, Collections.emptyMap());
            }
        }
        // Pattern match (linear scan)
        if (segments != null) {
            for (PatternRoute pr : patterns) {
                if (!pr.method.equals(method)) {
                    continue;
                }
                Map<String, String> params = pr.pattern.match(segments);
                if (params != null) {
                    return new RouteMatch(pr.action, params);
                }
            }
        }
        return null;
    }

    /** Methods with a route for this path, for the 405 Allow header. */
    private Set<String> allowedMethods(String path, String[] segments) {
        Set<String> allowed = new TreeSet<>();
        if (path != null) {
            for (Map.Entry<String, Map<String, Runnable>> e : exact.entrySet()) {
                if (e.getValue().containsKey(path)) {
                    allowed.add(e.getKey());
                }
            }
        }
        if (segments != null) {
            for (PatternRoute pr : patterns) {
                if (pr.pattern.match(segments) != null) {
                    allowed.add(pr.method);
                }
            }
        }
        if (allowed.contains("GET")) {
            allowed.add("HEAD");
        }
        return allowed;
    }

    private static void methodNotAllowed(Set<String> allowed) {
        Turismo.status(405);
        Turismo.header("Allow", String.join(", ", allowed));
        Turismo.print("Method Not Allowed");
    }

    private static boolean hasEncodedSlash(String rawPath) {
        if (rawPath == null) {
            return false;
        }
        for (int i = rawPath.indexOf('%'); i >= 0 && i + 2 < rawPath.length();
                i = rawPath.indexOf('%', i + 1)) {
            if (rawPath.charAt(i + 1) == '2'
                    && (rawPath.charAt(i + 2) == 'F'
                        || rawPath.charAt(i + 2) == 'f')) {
                return true;
            }
        }
        return false;
    }

    private static String[] segments(String path, String rawPath) {
        if (rawPath == null) {
            return path != null ? path.split("/") : null;
        }
        String[] segments = rawPath.split("/");
        for (int i = 0; i < segments.length; i++) {
            segments[i] = percentDecode(segments[i]);
        }
        return segments;
    }

    /**
     * Decodes {@code %XX} escapes as UTF-8. Unlike {@link
     * java.net.URLDecoder}, {@code +} is left alone (it is literal in
     * paths) and malformed escapes are kept as-is.
     */
    static String percentDecode(String s) {
        if (s.indexOf('%') < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int i = 0;
        while (i < s.length()) {
            while (i + 2 < s.length() && s.charAt(i) == '%'
                    && hex(s.charAt(i + 1)) >= 0 && hex(s.charAt(i + 2)) >= 0) {
                bytes.write(hex(s.charAt(i + 1)) << 4 | hex(s.charAt(i + 2)));
                i += 3;
            }
            if (bytes.size() > 0) {
                sb.append(bytes.toString(StandardCharsets.UTF_8));
                bytes.reset();
            }
            if (i < s.length()) {
                sb.append(s.charAt(i++));
            }
        }
        return sb.toString();
    }

    private static int hex(char c) {
        return Character.digit(c, 16);
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
     * and wildcards ({@code *}). Delegates to {@link PathPattern}.
     */
    static class PatternRoute {
        final String method;
        final PathPattern pattern;
        final Runnable action;

        PatternRoute(String method, String path, Runnable action) {
            this.method = method;
            this.pattern = new PathPattern(path);
            this.action = action;
        }
    }
}
