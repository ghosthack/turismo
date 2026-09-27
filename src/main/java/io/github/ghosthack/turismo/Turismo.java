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
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import io.github.ghosthack.turismo.annotation.DELETE;
import io.github.ghosthack.turismo.annotation.GET;
import io.github.ghosthack.turismo.annotation.PATCH;
import io.github.ghosthack.turismo.annotation.POST;
import io.github.ghosthack.turismo.annotation.PUT;
import io.github.ghosthack.turismo.http.Server;
import io.github.ghosthack.turismo.util.Validation;

/**
 * Static facade for the turismo web framework. Provides a zero-dependency,
 * Sinatra/Express-style API for defining routes and handling HTTP requests
 * using the JDK's built-in HTTP server.
 *
 * <pre>{@code
 * import static io.github.ghosthack.turismo.Turismo.*;
 *
 * public class Main {
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
 * <p>The registration and server methods ({@code get()}, {@code start()},
 * ...) act on a shared default {@link App}, returned by {@link #app()}.
 * Create more {@code App} instances for separate route sets, for example
 * several servers in one JVM or an isolated app per test. The request and
 * response helpers ({@code param()}, {@code print()}, ...) work inside the
 * handlers of any app.
 *
 * @see App
 * @see Context
 * @see io.github.ghosthack.turismo.http.Server
 */
public final class Turismo {

    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, String>> PATH_PARAMS =
            new ThreadLocal<>();
    private static final ThreadLocal<Form> FORM = new ThreadLocal<>();

    private static final App APP = new App();

    private Turismo() {
    }

    // ---------------------------------------------------------------
    // Route registration (default app)
    // ---------------------------------------------------------------

    /**
     * Returns the default app that the static registration and server
     * methods of this class act on.
     *
     * @return the default app
     */
    public static App app() {
        return APP;
    }

    /**
     * Registers a GET route.
     *
     * @param path   the URL path pattern
     * @param action the action to execute
     */
    public static void get(String path, Runnable action) {
        APP.get(path, action);
    }

    /**
     * Registers a GET route that returns a fixed string body.
     *
     * @param path the URL path pattern
     * @param body the response body text
     */
    public static void get(String path, String body) {
        APP.get(path, body);
    }

    /**
     * Registers a POST route.
     *
     * @param path   the URL path pattern
     * @param action the action to execute
     */
    public static void post(String path, Runnable action) {
        APP.post(path, action);
    }

    /**
     * Registers a POST route that returns a fixed string body.
     *
     * @param path the URL path pattern
     * @param body the response body text
     */
    public static void post(String path, String body) {
        APP.post(path, body);
    }

    /**
     * Registers a PUT route.
     *
     * @param path   the URL path pattern
     * @param action the action to execute
     */
    public static void put(String path, Runnable action) {
        APP.put(path, action);
    }

    /**
     * Registers a PUT route that returns a fixed string body.
     *
     * @param path the URL path pattern
     * @param body the response body text
     */
    public static void put(String path, String body) {
        APP.put(path, body);
    }

    /**
     * Registers a DELETE route.
     *
     * @param path   the URL path pattern
     * @param action the action to execute
     */
    public static void delete(String path, Runnable action) {
        APP.delete(path, action);
    }

    /**
     * Registers a DELETE route that returns a fixed string body.
     *
     * @param path the URL path pattern
     * @param body the response body text
     */
    public static void delete(String path, String body) {
        APP.delete(path, body);
    }

    /**
     * Registers a PATCH route.
     *
     * @param path   the URL path pattern
     * @param action the action to execute
     */
    public static void patch(String path, Runnable action) {
        APP.patch(path, action);
    }

    /**
     * Registers a PATCH route that returns a fixed string body.
     *
     * @param path the URL path pattern
     * @param body the response body text
     */
    public static void patch(String path, String body) {
        APP.patch(path, body);
    }

    /**
     * Registers a HEAD route. Without one, HEAD requests are served by
     * the matching GET route (the response body is discarded).
     *
     * @param path   the URL path pattern
     * @param action the action to execute
     */
    public static void head(String path, Runnable action) {
        APP.head(path, action);
    }

    /**
     * Registers an OPTIONS route.
     *
     * @param path   the URL path pattern
     * @param action the action to execute
     */
    public static void options(String path, Runnable action) {
        APP.options(path, action);
    }

    /**
     * Sets the handler for requests that match no registered route.
     *
     * @param action the not-found action
     * @throws IllegalArgumentException if action is null
     */
    public static void notFound(Runnable action) {
        APP.notFound(action);
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
    public static void route(String method, String path, Runnable action) {
        APP.route(method, path, action);
    }

    /**
     * Registers an annotated controller instance. Scans the instance's
     * class for methods annotated with {@link GET @GET}, {@link POST @POST},
     * {@link PUT @PUT}, {@link DELETE @DELETE}, or {@link PATCH @PATCH},
     * and registers each as a route. Annotated methods declared in
     * superclasses are included; an annotated override in a subclass
     * replaces the superclass's route. Method arguments are bound from
     * the request as described in {@link App#controller(Object)}.
     *
     * <pre>{@code
     * public class MyController {
     *     @GET("/hello")
     *     void hello() {
     *         print("Hello!");
     *     }
     * }
     *
     * Turismo.controller(new MyController());
     * }</pre>
     *
     * @param instance the controller instance
     * @throws IllegalArgumentException if the instance has no annotated
     *         methods, or an annotated method has an argument that can't
     *         be bound
     */
    public static void controller(Object instance) {
        APP.controller(instance);
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
     * (e.g. {@code :id} in a route pattern), then query string
     * parameters, then fields of an {@code application/x-www-form-urlencoded}
     * request body (see {@link #form(String)}).
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
        String value = context().query(name);
        return value != null ? value : form(name);
    }

    /**
     * Returns a field of an {@code application/x-www-form-urlencoded}
     * request body, as sent by an HTML form. The body is read and parsed
     * on first use (the raw body stays available through {@link #body()}).
     * A body over the app's {@linkplain App#setMaxFormSize limit} is
     * answered with {@code 413}, a malformed one with {@code 400}.
     *
     * @param name the field name
     * @return the value, or {@code null} if not present or the request
     *         body is not a form
     */
    public static String form(String name) {
        return form().get(name);
    }

    /**
     * Returns all fields of an {@code application/x-www-form-urlencoded}
     * request body as an unmodifiable map, as described in
     * {@link #form(String)}.
     *
     * @return the form fields, empty if the request body is not a form
     */
    public static Map<String, String> forms() {
        return form().fields();
    }

    private static Form form() {
        context(); // Throws if no request is bound; FORM is bound with it
        return FORM.get();
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
        Form form = FORM.get();
        return form != null ? form.body() : context().body();
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
     * Writes multiple strings to the response body. Avoids string
     * concatenation when building output from multiple parts.
     *
     * @param parts the text parts to write
     */
    public static void print(String... parts) {
        Context ctx = context();
        for (String part : parts) {
            ctx.print(part);
        }
    }

    /**
     * Returns the response output stream for writing binary data.
     *
     * @return the output stream
     */
    public static OutputStream output() {
        return context().output();
    }

    // ---------------------------------------------------------------
    // JSON
    // ---------------------------------------------------------------

    /**
     * Sets the Content-Type to {@code application/json} and writes
     * the given object as JSON to the response body. Supports
     * {@link Map}, {@link Iterable}, arrays, records, {@link String},
     * {@link Character}, enums, {@link Number}, {@link Boolean}, and
     * {@code null}. Non-finite
     * numbers (NaN, Infinity) are written as {@code null}.
     *
     * @param obj the object to serialize
     */
    public static void json(Object obj) {
        type("application/json");
        print(toJson(obj));
    }

    /**
     * Serializes an object to a JSON string. Supports {@link Map},
     * {@link Iterable}, arrays, records (as objects keyed by component
     * name), {@link String}, {@link Character}, enums (by name),
     * {@link Number}, {@link Boolean}, and {@code null}. Non-finite
     * numbers (NaN, Infinity) are written as {@code null}.
     *
     * @param obj the object to serialize
     * @return the JSON string
     * @throws IllegalArgumentException if the object type is not supported
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof String || obj instanceof Character) {
            return jsonString(obj.toString());
        }
        if (obj instanceof Enum<?> e) {
            return jsonString(e.name());
        }
        if (obj instanceof Double d) {
            return jsonNumber(d);
        }
        if (obj instanceof Float f) {
            return jsonNumber(f);
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj instanceof Map<?, ?>) {
            return jsonMap((Map<?, ?>) obj);
        }
        if (obj instanceof Iterable<?>) {
            return jsonIterable((Iterable<?>) obj);
        }
        if (obj.getClass().isArray()) {
            return jsonArray(obj);
        }
        if (obj instanceof Record r) {
            return jsonRecord(r);
        }
        throw new IllegalArgumentException(
                "Unsupported type: " + obj.getClass().getName());
    }

    /** JSON has no NaN or Infinity; like JavaScript, write them as null. */
    private static String jsonNumber(double d) {
        return Double.isFinite(d) ? Double.toString(d) : "null";
    }

    private static String jsonNumber(float f) {
        return Float.isFinite(f) ? Float.toString(f) : "null";
    }

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static String jsonMap(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<?, ?> entry = it.next();
            sb.append(toJson(String.valueOf(entry.getKey())));
            sb.append(':');
            sb.append(toJson(entry.getValue()));
            if (it.hasNext()) {
                sb.append(',');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private static String jsonIterable(Iterable<?> iter) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        Iterator<?> it = iter.iterator();
        while (it.hasNext()) {
            sb.append(toJson(it.next()));
            if (it.hasNext()) {
                sb.append(',');
            }
        }
        sb.append(']');
        return sb.toString();
    }

    private static String jsonArray(Object arr) {
        // Array.get boxes primitive elements, so every array type goes
        // through the same element handling as boxed values
        int length = Array.getLength(arr);
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < length; i++) {
            if (i > 0) sb.append(',');
            sb.append(toJson(Array.get(arr, i)));
        }
        sb.append(']');
        return sb.toString();
    }

    private static String jsonRecord(Record r) {
        RecordComponent[] components = r.getClass().getRecordComponents();
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (int i = 0; i < components.length; i++) {
            if (i > 0) sb.append(',');
            Method accessor = components[i].getAccessor();
            Object value;
            try {
                accessor.setAccessible(true);
                value = accessor.invoke(r);
            } catch (InvocationTargetException e) {
                throw new IllegalArgumentException(
                        "Failed to read record component "
                        + components[i].getName(), e.getCause());
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new IllegalArgumentException(
                        "Cannot access record component "
                        + components[i].getName(), e);
            }
            sb.append(jsonString(components[i].getName()));
            sb.append(':');
            sb.append(toJson(value));
        }
        sb.append('}');
        return sb.toString();
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
    // Server lifecycle (default app)
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
    public static void start(int port) {
        APP.start(port);
    }

    /**
     * Stops the embedded HTTP server gracefully, if one is running:
     * new connections are refused and requests in progress get up to
     * {@code grace} to complete before being interrupted.
     *
     * @param grace how long to wait for requests in progress
     * @throws IllegalArgumentException if grace is null or negative
     */
    public static void stop(Duration grace) {
        APP.stop(grace);
    }

    /**
     * Stops the embedded HTTP server immediately, if one is running.
     * Requests still in progress are interrupted; use
     * {@link #stop(Duration)} to let them finish.
     */
    public static void stop() {
        APP.stop();
    }

    /**
     * Returns the port the embedded server is listening on. Useful when
     * the server was started with port 0 (random available port).
     *
     * @return the port number
     * @throws IllegalStateException if no server is running
     */
    public static int port() {
        return APP.port();
    }

    /**
     * Dispatches a request through the default app's routes. Resolves
     * the route for the given context, sets up the thread-local
     * environment, and executes the matching action.
     *
     * <p>Transport adapters (such as {@link Server}) call this method
     * for each incoming request. Custom transport implementations can
     * use this to integrate with the turismo routing engine.
     *
     * @param ctx the request/response context
     */
    public static void handle(Context ctx) {
        APP.handle(ctx);
    }

    /**
     * Clears all routes of the default app, restores its default
     * not-found handler, and stops its server if running. Tests that
     * need isolation can use a fresh {@link App} instead.
     */
    public static void reset() {
        APP.reset();
    }

    // ---------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------

    /**
     * Binds a request context to the current thread; used by {@link App}.
     * Returns the previous binding, to be restored with {@link #restore},
     * so an app can dispatch to another from inside a handler.
     */
    static Object[] bind(Context ctx, Map<String, String> params,
            int maxFormSize) {
        Object[] previous = {CONTEXT.get(), PATH_PARAMS.get(), FORM.get()};
        CONTEXT.set(ctx);
        PATH_PARAMS.set(params);
        FORM.set(new Form(ctx, maxFormSize));
        return previous;
    }

    @SuppressWarnings("unchecked")
    static void restore(Object[] previous) {
        if (previous[0] == null) {
            CONTEXT.remove();
            PATH_PARAMS.remove();
            FORM.remove();
        } else {
            CONTEXT.set((Context) previous[0]);
            PATH_PARAMS.set((Map<String, String>) previous[1]);
            FORM.set((Form) previous[2]);
        }
    }

    static void defaultNotFound() {
        status(404);
        print("Not Found");
    }

    static void validateLocation(String url) {
        Validation.validateLocation(url);
    }
}
