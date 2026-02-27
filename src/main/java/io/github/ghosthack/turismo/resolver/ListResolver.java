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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import io.github.ghosthack.turismo.servlet.Env;

/**
 * A resolver that stores routes in a list and supports wildcard ({@code *})
 * and named parameter ({@code :param}) path segments.
 */
public class ListResolver extends MethodPathResolver {
    
    private HashMap<String, List<ParsedEntry>> methodPathList;
    private Runnable defaultRunnable;
    private static final String SYMBOL_PREFIX = ":";
    private static final String WILDCARD = "*";
    
    /**
     * A parsed route entry that pre-computes path segments, parameter
     * indexes, and wildcard indexes for efficient matching.
     */
    public static class ParsedEntry {
        private final Runnable runnable;
        private final String path;
        private final String[] parts;
        private final Set<Integer> paramIndexes;
        private final Set<Integer> wildcardIndexes;
        private final Set<Entry<String, Integer>> params;

        /**
         * Creates a parsed entry for the given path.
         *
         * @param runnable the action to execute
         * @param path     the URL path pattern
         */
        public ParsedEntry(Runnable runnable, String path) {
            super();
            if (path == null)
                throw new IllegalArgumentException("path must not be null");
            this.runnable = runnable;
            this.path = path;
            parts = path.split("/");
            if (parts.length > 0) {
                Map<String, Integer> paramMap = new HashMap<String, Integer>(parts.length);
                paramIndexes = new HashSet<Integer>(parts.length);
                wildcardIndexes = new HashSet<Integer>(parts.length);
                for (int i = 0; i < parts.length; i++) {
                    if (WILDCARD.equals(parts[i])) {
                        wildcardIndexes.add(i);
                    } else if (parts[i].startsWith(SYMBOL_PREFIX)) {
                        String sub = parts[i].substring(1);
                        paramMap.put(sub, i);
                        paramIndexes.add(i);
                    }
                }
                params = paramMap.entrySet();
            } else {
                paramIndexes = null;
                wildcardIndexes = null;
                params = null;
            }
        }

        /**
         * Returns whether the segment at index {@code i} is a named parameter.
         *
         * @param i the segment index
         * @return {@code true} if the segment is a parameter
         */
        public boolean isParam(int i) {
            if (paramIndexes == null)
                return false;
            return paramIndexes.contains(i);
        }

        /**
         * Returns whether the segment at index {@code i} is a wildcard.
         *
         * @param i the segment index
         * @return {@code true} if the segment is a wildcard
         */
        public boolean isWildcard(int i) {
            if (wildcardIndexes == null)
                return false;
            return wildcardIndexes.contains(i);
        }

        /**
         * Returns the named parameter entries (name to segment index).
         *
         * @return the parameter entries
         */
        public Set<Entry<String, Integer>> getParams() {
            return params;
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
         * @return the path segments array, or {@code null}
         */
        public String[] getParts() {
            return parts != null ? parts.clone() : null;
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
        methodPathList = new HashMap<String, List<ParsedEntry>>();
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
            pathList = new ArrayList<ParsedEntry>();
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
            // Split the request path once, outside the loop
            final String[] requestParts = path.split("/");
            for (ParsedEntry parsedEntry : pathList) {
                String[] parts = parsedEntry.getParts();
                if (parts == null) {
                    if (parsedEntry.pathEquals(path)) {
                        return parsedEntry.getRunnable();
                    }
                } else if (parts.length == requestParts.length) {
                    boolean match = true;
                    boolean hasParams = false;
                    for (int i = 0; i < requestParts.length; i++) {
                        if (parsedEntry.isWildcard(i)) {
                            // skipped
                        } else if (parsedEntry.isParam(i)) {
                            // it's a resource "symbol"
                            hasParams = true;
                        } else if (parts[i].equals(requestParts[i])) {
                            // exact match
                        } else {
                            // not a match
                            match = false;
                            break;
                        }
                    }
                    if (match) {
                        if (hasParams) {
                            Map<String, String> params = new HashMap<String, String>();
                            for (Map.Entry<String, Integer> paramsEntry : parsedEntry.getParams()) {
                                String paramKey = paramsEntry.getKey();
                                Integer paramPos = paramsEntry.getValue();
                                params.put(paramKey, requestParts[paramPos]);
                            }
                            Env.setResourceParams(params);
                        }
                        return parsedEntry.getRunnable();
                    }
                }
            }
        }
        // default route, no mapping found
        return defaultRunnable;
    }

}
