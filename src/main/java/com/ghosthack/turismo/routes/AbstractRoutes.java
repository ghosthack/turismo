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

/**
 * Base class for route containers. Provides HTTP method shortcut methods
 * and defers route initialization to {@link #map()}, which is called lazily
 * on the first invocation of {@link #getResolver()} to avoid the anti-pattern
 * of calling an abstract method from the constructor.
 */
public abstract class AbstractRoutes implements Routes {

    protected static final String GET = "GET";
    protected static final String POST = "POST";
    protected static final String PUT = "PUT";
    protected static final String DELETE = "DELETE";
    protected static final String HEAD = "HEAD";
    protected static final String OPTIONS = "OPTIONS";
    protected static final String TRACE = "TRACE";
    protected static final String PATCH = "PATCH";

    protected final Resolver resolver;
    private volatile boolean initialized = false;

    protected AbstractRoutes(Resolver resolver) {
        this.resolver = resolver;
        this.resolver.route(new NotFoundAction());
    }

    /**
     * Returns the resolver, initializing routes lazily on first access.
     * This avoids calling the abstract {@link #map()} method from the
     * constructor, which can cause issues if subclass fields are not
     * yet initialized.
     */
    @Override
    public Resolver getResolver() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    map();
                    initialized = true;
                }
            }
        }
        return resolver;
    }

    /**
     * Define your routes in this method. Called lazily on first
     * {@link #getResolver()} invocation.
     */
    protected abstract void map();

    // HTTP method shortcut methods

    protected void get(final String path, Runnable runnable) {
        resolver.route(GET, path, runnable);
    }

    protected void post(final String path, Runnable runnable) {
        resolver.route(POST, path, runnable);
    }

    protected void put(final String path, Runnable runnable) {
        resolver.route(PUT, path, runnable);
    }

    protected void delete(final String path, Runnable runnable) {
        resolver.route(DELETE, path, runnable);
    }

    protected void head(final String path, Runnable runnable) {
        resolver.route(HEAD, path, runnable);
    }

    protected void options(final String path, Runnable runnable) {
        resolver.route(OPTIONS, path, runnable);
    }

    protected void trace(final String path, Runnable runnable) {
        resolver.route(TRACE, path, runnable);
    }

    protected void patch(final String path, Runnable runnable) {
        resolver.route(PATCH, path, runnable);
    }

    protected void route(Runnable runnable) {
        resolver.route(runnable);
    }

}
