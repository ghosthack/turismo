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

import com.ghosthack.turismo.resolver.ListResolver;

/**
 * Routes container using list-based resolution with wildcard and
 * named parameter support. Extend this class and override {@link #map()}
 * to define your routes.
 */
public abstract class RoutesList extends AbstractRoutes {

    public RoutesList() {
        super(new ListResolver());
    }

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

    protected void delete(final String fromPath, final String targetPath) {
        resolver.route(DELETE, fromPath, targetPath);
    }

    protected void patch(final String fromPath, final String targetPath) {
        resolver.route(PATCH, fromPath, targetPath);
    }

}
