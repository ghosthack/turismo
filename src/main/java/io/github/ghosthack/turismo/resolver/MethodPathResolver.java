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

package io.github.ghosthack.turismo.resolver;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.ghosthack.turismo.PathPattern;
import io.github.ghosthack.turismo.Resolver;
import io.github.ghosthack.turismo.action.ActionException;
import io.github.ghosthack.turismo.servlet.Env;

/**
 * Base resolver that extracts the HTTP method and request path from the
 * current {@link Env} and delegates to {@link #resolve(String, String)}.
 */
public abstract class MethodPathResolver implements Resolver {

    private static final String UNDEFINED_PATH = "Undefined path";

    /** Default constructor. */
    protected MethodPathResolver() {
    }

    /**
     * Resolves the current request. A request whose raw URI contains an
     * encoded slash ({@code %2F}) is resolved with
     * {@link #resolveEncoded(String, String[])} instead of
     * {@link #resolve(String, String)}, so {@code /admin%2Fsecret} does
     * not reach an {@code /admin/secret} route through the
     * container-decoded path.
     */
    @Override
    public Runnable resolve() throws ActionException {
        HttpServletRequest req = Env.req();
        String pathInfo = req.getPathInfo();
        String path = extractPath();
        String method = req.getMethod();
        String[] segments = EncodedPath.segments(req, pathInfo != null);
        if (segments == EncodedPath.NO_MATCH) {
            return resolveEncoded(method, null);
        }
        if (segments != null) {
            return resolveEncoded(method, segments);
        }
        Runnable route = resolve(method, path);
        return route;
    }

    /**
     * Resolves a request whose raw path contains an encoded slash. The
     * segments are those of the raw path, each percent-decoded on its
     * own, so an encoded {@code /} stays inside its segment; there is no
     * decoded path to look up exact routes with. The default dispatches
     * to {@link #find(String, String, String[])} and answers anything
     * else with {@code 404 Not Found}; the built-in resolvers use their
     * default route instead.
     *
     * @param method   the HTTP method
     * @param segments the decoded segments of the raw path, or {@code null}
     *                 if it can't be matched against any route
     * @return the action to run
     */
    protected Runnable resolveEncoded(String method, String[] segments) {
        return dispatch(method, null, segments, MethodPathResolver::notFound);
    }

    /**
     * Resolves a route for the given HTTP method and path.
     *
     * @param method the HTTP method
     * @param path   the request path
     * @return the matching action, or {@code null}
     */
    protected abstract Runnable resolve(String method, String path);

    /**
     * Finds the action registered for exactly this method and path,
     * without any fallback. Subclasses that override this and
     * {@link #allowedMethods(String)} can implement
     * {@link #resolve(String, String)} with {@link #dispatch}.
     *
     * @param method the HTTP method
     * @param path   the request path
     * @return the matching action, or {@code null}
     */
    protected Runnable find(String method, String path) {
        return null;
    }

    /**
     * Returns the methods that have a route matching the path.
     *
     * @param path the request path
     * @return the matching methods; empty if none
     */
    protected Set<String> allowedMethods(String path) {
        return Collections.emptySet();
    }

    /**
     * Finds the action for this method, given the path both as a whole
     * (for exact routes) and as decoded segments (for pattern routes).
     * {@code path} is {@code null} when it must not be used for exact
     * lookups, because the raw path had an encoded slash. The default
     * calls {@link #find(String, String)} when there is a path.
     *
     * @param method   the HTTP method
     * @param path     the decoded request path, or {@code null}
     * @param segments the decoded path segments, or {@code null}
     * @return the matching action, or {@code null}
     */
    protected Runnable find(String method, String path, String[] segments) {
        return path != null ? find(method, path) : null;
    }

    /**
     * Returns the methods that have a route matching the path, given as in
     * {@link #find(String, String, String[])}. The default calls
     * {@link #allowedMethods(String)} when there is a path.
     *
     * @param path     the decoded request path, or {@code null}
     * @param segments the decoded path segments, or {@code null}
     * @return the matching methods; empty if none
     */
    protected Set<String> allowedMethods(String path, String[] segments) {
        return path != null ? allowedMethods(path) : Collections.emptySet();
    }

    /**
     * Resolves a request using {@link #find}: a HEAD request with no
     * HEAD route is served by the GET route, a path that only matches
     * routes for other methods gets {@code 405 Method Not Allowed}, and
     * anything else gets {@code fallback}.
     *
     * @param method   the HTTP method
     * @param path     the request path
     * @param fallback the action to use when nothing matches
     * @return the action to run, or {@code fallback} (possibly null)
     */
    protected final Runnable dispatch(String method, String path,
            Runnable fallback) {
        return dispatch(method, path,
                path != null ? PathPattern.split(path) : null, fallback);
    }

    /**
     * Resolves a request like {@link #dispatch(String, String, Runnable)},
     * with the path given as in {@link #find(String, String, String[])}.
     *
     * @param method   the HTTP method
     * @param path     the decoded request path, or {@code null}
     * @param segments the decoded path segments
     * @param fallback the action to use when nothing matches
     * @return the action to run, or {@code fallback} (possibly null)
     */
    protected final Runnable dispatch(String method, String path,
            String[] segments, Runnable fallback) {
        Runnable route = find(method, path, segments);
        if (route == null && "HEAD".equals(method)) {
            route = find("GET", path, segments);
        }
        if (route != null) {
            return route;
        }
        Set<String> allowed = new TreeSet<>(allowedMethods(path, segments));
        if (!allowed.isEmpty()) {
            if (allowed.contains("GET")) {
                allowed.add("HEAD");
            }
            allowed.add("OPTIONS");
            if ("OPTIONS".equals(method)) {
                return () -> options(allowed);
            }
            return () -> methodNotAllowed(allowed);
        }
        return fallback;
    }

    private static void options(Set<String> allowed) {
        HttpServletResponse res = Env.res();
        res.setHeader("Allow", String.join(", ", allowed));
        res.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private static void methodNotAllowed(Set<String> allowed) {
        HttpServletResponse res = Env.res();
        res.setHeader("Allow", String.join(", ", allowed));
        try {
            res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        } catch (IOException e) {
            throw new ActionException(e);
        }
    }

    private static void notFound() {
        try {
            Env.res().sendError(HttpServletResponse.SC_NOT_FOUND);
        } catch (IOException e) {
            throw new ActionException(e);
        }
    }

    private String extractPath() throws ActionException {
        String path = Env.req().getPathInfo();
        if (path == null) {
            path = Env.req().getServletPath();
            if (path == null) {
                throw new ActionException(UNDEFINED_PATH);
            }
        }
        return path;
    }

}
