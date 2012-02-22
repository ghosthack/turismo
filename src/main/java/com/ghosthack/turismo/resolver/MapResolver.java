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

import java.util.HashMap;
import java.util.Map;

public class MapResolver extends MethodPathResolver {

    /** 
     * { method => { path => action-route } }
     */
    private final Map<String, Map<String, Runnable>> methodPathMap = new HashMap<String, Map<String, Runnable>>();

    private Runnable notFoundRoute;

    public Runnable getNotFoundRoute() {
        return notFoundRoute;
    }

    @Override
    public Runnable resolve(String method, String path) {
        Runnable route = findRoute(method, path);
        if(route != null) {
            return route;
        }
        return getNotFoundRoute();
    }

    @Override
    public void route(Runnable runnable) {
        notFoundRoute = runnable;
    }

    public void route(final String path, Runnable runnable) {
        route(null, path, runnable);
    }

    public void route(final String method, final String path, Runnable runnable) {
        r(method, path, runnable);
    }

    private Runnable findRoute(String method, String path) {
        Map<String, Runnable> methodMap = methodPathMap.get(method);
        
        if (methodMap == null) {
            methodMap = methodPathMap.get(null);
        }

        return methodMap != null? methodMap.get(path) : null;
    }

    private void r(final String method, final String path, Runnable action) {
        Map<String, Runnable> methodMap = methodPathMap.get(method);
        if(methodMap == null) {
            methodMap = new HashMap<String, Runnable>();
            methodPathMap.put(method, methodMap);
        }
        methodMap.put(path, action);
    }

    @Override
    public void route(String method, String fromPath, String targetPath) {
        throw new IllegalAccessError("Not implemented");
    }

}
