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

import jakarta.servlet.http.HttpServletResponse;

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

    @Override
    public Runnable resolve() throws ActionException {
        String path = extractPath();
        String method = Env.req().getMethod();
        Runnable route = resolve(method, path);
        return route;
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
        Runnable route = find(method, path);
        if (route == null && "HEAD".equals(method)) {
            route = find("GET", path);
        }
        if (route != null) {
            return route;
        }
        Set<String> allowed = new TreeSet<>(allowedMethods(path));
        if (!allowed.isEmpty()) {
            if (allowed.contains("GET")) {
                allowed.add("HEAD");
            }
            return () -> methodNotAllowed(allowed);
        }
        return fallback;
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
