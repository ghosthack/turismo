package com.ghosthack.turismo.resolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;


public class ListResolver extends MethodPathResolver {
    
    private HashMap<String, List<ParsedEntry>> methodpathList;
    private Runnable defaultRunnable;
    private static final String SYMBOL_PREFIX = ":";
    private static final String WILDCARD = "*";
    
    public static class ParsedEntry {
        Runnable runnable;
        String[] parts;
        List<Integer> paramList;
        List<Integer> wildcardList;
        private Set<Entry<String, Integer>> params;
        public String[] getParts() {
            return parts;
        }
        public Runnable getRunnable() {
            return runnable;
        }
        public ParsedEntry(Runnable runnable, String path) {
            super();
            this.runnable = runnable;
            if(path != null) {
                String[] parts = path.split("/");
                if(parts.length>0) {
                    HashMap<String, Integer> paramMap = new HashMap<String, Integer>(parts.length);
                    paramList = new ArrayList<Integer>(parts.length);
                    wildcardList = new ArrayList<Integer>(parts.length);
                    for(int i = 0; i<parts.length; i++) {
                        if(WILDCARD.equals(parts[i])) {
                            wildcardList.add(i);
                        } else if(parts[i].startsWith(SYMBOL_PREFIX)) {
                            String sub = parts[i].substring(1);
                            paramMap.put(sub, i);
                            paramList.add(i);
                        }
                    }
                    params = paramMap.entrySet();
                }
            }
        }
        public boolean isParam(int i) {
            return paramList.contains(i);
        }
        public boolean isWildcard(int i) {
            return wildcardList.contains(i);
        }
        public Set<Entry<String, Integer>> getParams() {
            return params;
        }
    }
    
    public ListResolver() {
        methodpathList = new HashMap<String, List<ParsedEntry>>();
    }

    public void route(String method, String path, Runnable runnable) {
        List<ParsedEntry> pathList = methodpathList.get(method);
        if(pathList == null) {
            pathList = new ArrayList<ParsedEntry>();
            methodpathList.put(method, pathList);
        }
        ParsedEntry parsed = new ParsedEntry(runnable, path);
        pathList.add(parsed);
    }

    public void route(Runnable runnable) {
        this.defaultRunnable = runnable;
    }
    
    public Runnable testResolve(String method, String path) {
        return resolve(method, path);
    }

    @Override
    protected Runnable resolve(String method, String path) {
        List<ParsedEntry> pathList = methodpathList.get(method);
        if(pathList != null) {
            if(path != null) {
                String[] splitted = path.split("/");
                for(ParsedEntry parsedEntry: pathList) {
                    if(parsedEntry != null) {
                        String[] parts = parsedEntry.getParts();
                        if(parts != null) {
                            if(parts.length != splitted.length) {
                                boolean match = true;
                                boolean hasParams = false;
                                for(int i = 0; i < splitted.length; i++) {
                                    if(parsedEntry.isWildcard(i)) {
                                        // skipped
                                    } else if(parsedEntry.isParam(i)) {
                                        // it's a resource "symbol"
                                        hasParams = true;
                                    } else if(parts[i].equals(splitted[i])) {
                                        // exact match
                                    } else {
                                        // not a match
                                        match = false;
                                        break;
                                    }
                                }
                                if(match) {
                                    if(hasParams) {
                                        Map<String, String> params = new HashMap<String, String>();
                                        for(Map.Entry<String, Integer> paramsEntry: parsedEntry.getParams()) {
                                            String paramKey = paramsEntry.getKey();
                                            Integer paramPos = paramsEntry.getValue();
                                            params.put(paramKey, splitted[paramPos]);
                                        }
                                    }
                                    return parsedEntry.getRunnable();
                                }
                            }
                        }
                    }
                }
            }
        }
        //default route, no mapping found
        return defaultRunnable;
    }

}
