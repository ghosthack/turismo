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

package com.ghosthack.turismo.routes;

import com.ghosthack.turismo.Resolver;
import com.ghosthack.turismo.Routes;
import com.ghosthack.turismo.action.NotFoundAction;
import com.ghosthack.turismo.resolver.MapResolver;

public abstract class RoutesMap implements Routes {
    
    protected final Resolver resolver;
    
    public RoutesMap() {
        resolver = new MapResolver();
        resolver.route(new NotFoundAction());
        map();
    }

    @Override
    public Resolver getResolver() {
        return resolver;
    }

    protected abstract void map();

    // Shortcuts methods

    protected void get(final String path, Runnable runnable) {
        resolver.route(GET, path, runnable);
    }

    protected void post(final String path, Runnable runnable) {
        resolver.route(POST, path, runnable);
    }

    protected void put(final String path, Runnable runnable) {
        resolver.route(PUT, path, runnable);
    }

    protected void head(final String path, Runnable runnable) {
        resolver.route(HEAD, path, runnable);
    }

    protected void options(final String path, Runnable runnable) {
        resolver.route(OPTIONS, path, runnable);
    }

    protected void delete(final String path, Runnable runnable) {
        resolver.route(DELETE, path, runnable);
    }

    protected void trace(final String path, Runnable runnable) {
        resolver.route(TRACE, path, runnable);
    }

    protected void route(Runnable runnable) {
        resolver.route(runnable);
    }

    protected static final String POST = "POST";
    protected static final String GET = "GET";
    protected static final String HEAD = "HEAD";
    protected static final String OPTIONS = "OPTIONS";
    protected static final String PUT = "PUT";
    protected static final String DELETE = "DELETE";
    protected static final String TRACE = "TRACE";

}
