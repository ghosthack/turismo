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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import io.github.ghosthack.turismo.PathPattern;
import io.github.ghosthack.turismo.servlet.Env;

/**
 * A resolver that stores routes in a list and supports wildcard ({@code *})
 * and named parameter ({@code :param}) path segments.
 */
public class ListResolver extends MethodPathResolver {
    
    private final Map<String, List<ParsedEntry>> methodPathList;
    private Runnable defaultRunnable;
    
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
        methodPathList = new HashMap<>();
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
        List<ParsedEntry> pathList = methodPathList.get(method);
        if(pathList == null) {
            pathList = new ArrayList<>();
            methodPathList.put(method, pathList);
        }
        ParsedEntry parsed = new ParsedEntry(runnable, path);
        pathList.add(parsed);
    }

    @Override
    public void route(Runnable runnable) {
        this.defaultRunnable = runnable;
    }

    @Override
    protected Runnable resolve(String method, String path) {
        List<ParsedEntry> pathList = methodPathList.get(method);
        if (pathList != null && path != null) {
            String[] requestParts = path.split("/");
            for (ParsedEntry parsedEntry : pathList) {
                Map<String, String> params = parsedEntry.pattern.match(requestParts);
                if (params != null) {
                    if (!params.isEmpty()) {
                        Env.setResourceParams(params);
                    }
                    return parsedEntry.getRunnable();
                }
            }
        }
        // default route, no mapping found
        return defaultRunnable;
    }

}
