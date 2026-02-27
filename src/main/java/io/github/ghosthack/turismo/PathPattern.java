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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A compiled URL path pattern that supports named parameters ({@code :name})
 * and wildcards ({@code *}).
 *
 * <p>Instances are immutable and thread-safe. Typical usage:
 *
 * <pre>{@code
 * PathPattern pattern = new PathPattern("/users/:id/posts/:postId");
 * Map<String, String> params = pattern.match("/users/42/posts/7".split("/"));
 * // params = {id=42, postId=7}
 * }</pre>
 *
 * @see Turismo
 */
public final class PathPattern {

    private final String[] parts;
    private final Map<String, Integer> paramNames;
    private final Set<Integer> paramPositions;
    private final Set<Integer> wildcardPositions;

    /**
     * Compiles a path pattern. Segments starting with {@code :} are named
     * parameters; segments equal to {@code *} are wildcards.
     *
     * @param path the path pattern (e.g. {@code /users/:id})
     * @throws IllegalArgumentException if path is null
     */
    public PathPattern(String path) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        this.parts = path.split("/");
        Map<String, Integer> names = new HashMap<>();
        Set<Integer> params = new HashSet<>();
        Set<Integer> wildcards = new HashSet<>();
        for (int i = 0; i < parts.length; i++) {
            if ("*".equals(parts[i])) {
                wildcards.add(i);
            } else if (parts[i].startsWith(":")) {
                names.put(parts[i].substring(1), i);
                params.add(i);
            }
        }
        this.paramNames = names;
        this.paramPositions = params;
        this.wildcardPositions = wildcards;
    }

    /**
     * Attempts to match the given request path segments against this
     * pattern. Named parameter values are extracted into the returned map.
     *
     * @param requestParts the request path split by {@code /}
     * @return an unmodifiable map of parameter names to values on match,
     *         or {@code null} if the path does not match
     */
    public Map<String, String> match(String[] requestParts) {
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

    /**
     * Returns the compiled path segments.
     *
     * @return a copy of the path segments
     */
    public String[] parts() {
        return parts.clone();
    }

    /**
     * Returns whether the segment at the given index is a named parameter.
     *
     * @param index the segment index
     * @return {@code true} if the segment is a parameter
     */
    public boolean isParam(int index) {
        return paramPositions.contains(index);
    }

    /**
     * Returns whether the segment at the given index is a wildcard.
     *
     * @param index the segment index
     * @return {@code true} if the segment is a wildcard
     */
    public boolean isWildcard(int index) {
        return wildcardPositions.contains(index);
    }

    /**
     * Returns the named parameter entries (name to segment index).
     *
     * @return the parameter entries
     */
    public Set<Map.Entry<String, Integer>> paramEntries() {
        return paramNames.entrySet();
    }
}
