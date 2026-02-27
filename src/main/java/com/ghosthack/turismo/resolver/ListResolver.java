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

package com.ghosthack.turismo.resolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.ghosthack.turismo.servlet.Env;


public class ListResolver extends MethodPathResolver {
    
    private HashMap<String, List<ParsedEntry>> methodPathList;
    private Runnable defaultRunnable;
    private static final String SYMBOL_PREFIX = ":";
    private static final String WILDCARD = "*";
    
    public static class ParsedEntry {
        private final Runnable runnable;
        private final String path;
        private final String[] parts;
        private final Set<Integer> paramIndexes;
        private final Set<Integer> wildcardIndexes;
        private final Set<Entry<String, Integer>> params;

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

        public boolean isParam(int i) {
            if (paramIndexes == null)
                return false;
            return paramIndexes.contains(i);
        }

        public boolean isWildcard(int i) {
            if (wildcardIndexes == null)
                return false;
            return wildcardIndexes.contains(i);
        }

        public Set<Entry<String, Integer>> getParams() {
            return params;
        }

        public boolean pathEquals(String path) {
            return this.path.equals(path);
        }

        public String[] getParts() {
            return parts;
        }

        public Runnable getRunnable() {
            return runnable;
        }

        public String getPath() {
            return path;
        }
    }
    
    public ListResolver() {
        methodPathList = new HashMap<String, List<ParsedEntry>>();
    }

    /** 
     * The target path must exist. Target can't have a different parameter spec. A hashmap could be used to enhance impl.
     * @param method
     * @param newPath
     * @param targetPath
     * @throws IllegalArgumentException if the parameter HTTP method hasn't anything mapped, also, when the "target" path isn't found
     */
    @Override
    public void route(String method, String newPath, String targetPath) {
        List<ParsedEntry> pathList = methodPathList.get(method);
        if(pathList == null) throw new IllegalArgumentException(method);
        for(ParsedEntry parsedEntry: pathList) {
            if(parsedEntry.getPath().equals(targetPath)) {
                Runnable runnable = parsedEntry.getRunnable();
                pathList.add(new ParsedEntry(runnable, newPath));
                return;
            }
        }
        throw new IllegalArgumentException(targetPath);
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
