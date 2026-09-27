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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.ghosthack.turismo.PathPattern;
import io.github.ghosthack.turismo.servlet.Env;

/**
 * A resolver that stores routes in a list and supports wildcard ({@code *})
 * and named parameter ({@code :param}) path segments. Routes can be added
 * while requests are being resolved.
 */
public class ListResolver extends MethodPathResolver {
    
    private final Map<String, List<ParsedEntry>> methodPathList;
    private volatile Runnable defaultRunnable;

    /**
     * A parsed route entry that delegates path matching to {@link PathPattern}.
     */
    public static class ParsedEntry {
        private final Runnable runnable;
        private final String path;
        private final PathPattern pattern;

        /**
         * Creates a parsed entry for the given path.
         *
         * @param runnable the action to execute
         * @param path     the URL path pattern
         */
        public ParsedEntry(Runnable runnable, String path) {
            if (path == null) {
                throw new IllegalArgumentException("path must not be null");
            }
            this.runnable = runnable;
            this.path = path;
            this.pattern = new PathPattern(path);
        }

        /**
         * Returns whether the segment at index {@code i} is a named parameter.
         *
         * @param i the segment index
         * @return {@code true} if the segment is a parameter
         */
        public boolean isParam(int i) {
            return pattern.isParam(i);
        }

        /**
         * Returns whether the segment at index {@code i} is a wildcard.
         *
         * @param i the segment index
         * @return {@code true} if the segment is a wildcard
         */
        public boolean isWildcard(int i) {
            return pattern.isWildcard(i);
        }

        /**
         * Returns the named parameter entries (name to segment index).
         *
         * @return the parameter entries
         */
        public Set<Entry<String, Integer>> getParams() {
            return pattern.paramEntries();
        }

        /**
         * Returns whether this entry's path equals the given path exactly.
         *
         * @param path the path to compare
         * @return {@code true} if paths are equal
         */
        public boolean pathEquals(String path) {
            return this.path.equals(path);
        }

        /**
         * Returns a copy of the path segments.
         *
         * @return the path segments array
         */
        public String[] getParts() {
            return pattern.parts();
        }

        /**
         * Returns the action for this route.
         *
         * @return the runnable action
         */
        public Runnable getRunnable() {
            return runnable;
        }

        /**
         * Returns the original path pattern.
         *
         * @return the path
         */
        public String getPath() {
            return path;
        }
    }
    
    /** Creates a new list-based resolver. */
    public ListResolver() {
        methodPathList = new ConcurrentHashMap<>();
    }

    /** 
     * Creates an alias so that {@code newPath} resolves to the same action as {@code targetPath}.
     * The target path must already be registered.
     *
     * @param method     the HTTP method
     * @param newPath    the new path to register
     * @param targetPath the existing path whose action should be reused
     * @throws IllegalArgumentException if no routes exist for the method or the target path is not found
     */
    @Override
    public void route(String method, String newPath, String targetPath) {
        List<ParsedEntry> pathList = methodPathList.get(method);
        if(pathList == null) throw new IllegalArgumentException(
                "No routes registered for HTTP method '" + method + "'");
        for(ParsedEntry parsedEntry: pathList) {
            if(parsedEntry.getPath().equals(targetPath)) {
                Runnable runnable = parsedEntry.getRunnable();
                pathList.add(new ParsedEntry(runnable, newPath));
                return;
            }
        }
        throw new IllegalArgumentException(
                "Target path '" + targetPath + "' not found in routes for method '" + method + "'");
    }

    @Override
    public void route(String method, String path, Runnable runnable) {
        if (method == null) {
            throw new IllegalArgumentException("method must not be null");
        }
        ParsedEntry parsed = new ParsedEntry(runnable, path);
        methodPathList.computeIfAbsent(method, k -> new CopyOnWriteArrayList<>())
                .add(parsed);
    }

    @Override
    public void route(Runnable runnable) {
        this.defaultRunnable = runnable;
    }

    @Override
    protected Runnable resolve(String method, String path) {
        return dispatch(method, path, defaultRunnable);
    }

    @Override
    protected Runnable resolveEncoded(String method, String[] segments) {
        return dispatch(method, null, segments, defaultRunnable);
    }

    @Override
    protected Runnable find(String method, String path) {
        return find(method, path, path != null ? path.split("/") : null);
    }

    /**
     * Matches the routes against {@code segments}; every route is a
     * pattern route, so {@code path} is not used.
     *
     * @param method   the HTTP method
     * @param path     the decoded request path, or {@code null}
     * @param segments the decoded path segments
     * @return the matching action, or {@code null}
     */
    @Override
    protected Runnable find(String method, String path, String[] segments) {
        List<ParsedEntry> pathList = method != null ? methodPathList.get(method) : null;
        if (pathList != null && segments != null) {
            for (ParsedEntry parsedEntry : pathList) {
                Map<String, String> params = parsedEntry.pattern.match(segments);
                if (params != null) {
                    if (!params.isEmpty()) {
                        Env.setResourceParams(params);
                    }
                    return parsedEntry.getRunnable();
                }
            }
        }
        return null;
    }

    @Override
    protected Set<String> allowedMethods(String path) {
        return allowedMethods(path, path != null ? path.split("/") : null);
    }

    @Override
    protected Set<String> allowedMethods(String path, String[] segments) {
        Set<String> allowed = new HashSet<>();
        if (segments == null) {
            return allowed;
        }
        for (Map.Entry<String, List<ParsedEntry>> e : methodPathList.entrySet()) {
            for (ParsedEntry parsedEntry : e.getValue()) {
                if (parsedEntry.pattern.match(segments) != null) {
                    allowed.add(e.getKey());
                    break;
                }
            }
        }
        return allowed;
    }

}
