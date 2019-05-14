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
import com.ghosthack.turismo.resolver.ListResolver;

public abstract class RoutesList implements Routes {

    protected final Resolver resolver;

    public RoutesList() {
        resolver = new ListResolver();
        resolver.route(new NotFoundAction());
        map();
    }

    @Override
    public Resolver getResolver() {
        return resolver;
    }

    protected abstract void map();

    // Route-alias shortcut methods

    protected void post(final String fromPath, final String targetPath) {
        resolver.route(POST, fromPath, targetPath);
    }

    protected void get(final String fromPath, final String targetPath) {
        resolver.route(GET, fromPath, targetPath);
    }

    protected void put(final String fromPath, final String targetPath) {
        resolver.route(PUT, fromPath, targetPath);
    }

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

    protected String POST = "POST";
    protected String GET = "GET";
    protected String HEAD = "HEAD";
    protected String OPTIONS = "OPTIONS";
    protected String PUT = "PUT";
    protected String DELETE = "DELETE";
    protected String TRACE = "TRACE";

}
