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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A resolver that stores routes in a hash map for O(1) exact-match lookups.
 * Does not support wildcard or parameterized paths. Routes can be added
 * while requests are being resolved.
 */
public class MapResolver extends MethodPathResolver {

    /** Creates a new map-based resolver. */
    public MapResolver() {
    }

    /**
     * { method =&gt; { path =&gt; action-route } }
     */
    private final Map<String, Map<String, Runnable>> methodPathMap = new ConcurrentHashMap<>();

    /** Method-agnostic routes: { path =&gt; action-route } */
    private final Map<String, Runnable> anyMethodMap = new ConcurrentHashMap<>();

    private volatile Runnable notFoundRoute;

    /**
     * Returns the fallback route used when no match is found.
     *
     * @return the not-found route
     */
    public Runnable getNotFoundRoute() {
        return notFoundRoute;
    }

    @Override
    public Runnable resolve(String method, String path) {
        return dispatch(method, path, getNotFoundRoute());
    }

    @Override
    protected Runnable resolveEncoded(String method, String[] segments) {
        // Only exact routes, and an encoded slash never matches one
        return dispatch(method, null, segments, getNotFoundRoute());
    }

    @Override
    protected Runnable find(String method, String path) {
        return findRoute(method, path);
    }

    @Override
    protected Set<String> allowedMethods(String path) {
        Set<String> allowed = new HashSet<>();
        if (path == null) {
            return allowed;
        }
        // Method-agnostic routes match every method, so find() never
        // falls through to here for them
        for (Map.Entry<String, Map<String, Runnable>> e : methodPathMap.entrySet()) {
            if (e.getValue().containsKey(path)) {
                allowed.add(e.getKey());
            }
        }
        return allowed;
    }

    @Override
    public void route(Runnable runnable) {
        notFoundRoute = runnable;
    }

    /**
     * Registers a method-agnostic route for the given path.
     *
     * @param path     the URL path
     * @param runnable the action to execute
     */
    public void route(final String path, Runnable runnable) {
        route(null, path, runnable);
    }

    /**
     * Registers a route; a {@code null} method registers a
     * method-agnostic route.
     *
     * @param method   the HTTP method, or {@code null} for any method
     * @param path     the URL path
     * @param runnable the action to execute
     * @throws IllegalArgumentException if the path or action is null
     */
    @Override
    public void route(final String method, final String path, Runnable runnable) {
        addRoute(method, path, runnable);
    }

    private Runnable findRoute(String method, String path) {
        if (path == null) {
            return null;
        }
        Map<String, Runnable> methodMap = method != null ? methodPathMap.get(method) : null;
        if (methodMap != null) {
            Runnable route = methodMap.get(path);
            if (route != null) {
                return route;
            }
        }
        // Fall through to method-agnostic routes
        return anyMethodMap.get(path);
    }

    private void addRoute(final String method, final String path, Runnable action) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        Map<String, Runnable> methodMap = method == null ? anyMethodMap
                : methodPathMap.computeIfAbsent(method, k -> new ConcurrentHashMap<>());
        methodMap.put(path, action);
    }

    @Override
    public void route(String method, String fromPath, String targetPath) {
        throw new UnsupportedOperationException("Route aliasing is not supported by MapResolver");
    }

}
