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

package com.ghosthack.turismo;

/**
 * Resolves a request to a {@link Runnable} action and stores route mappings.
 */
public interface Resolver {

    /**
     * Resolves the current request to a route action.
     *
     * @return the action to execute, or {@code null} if no route matches
     */
    Runnable resolve();

    /**
     * Registers a route for a specific HTTP method and path.
     *
     * @param method   the HTTP method (e.g. "GET", "POST")
     * @param path     the URL path pattern
     * @param runnable the action to execute when the route matches
     */
    void route(String method, String path, Runnable runnable);

    /**
     * Creates an alias so that {@code fromPath} resolves to the same action as {@code targetPath}.
     *
     * @param method     the HTTP method
     * @param fromPath   the new path to register
     * @param targetPath the existing path whose action should be reused
     */
    void route(String method, String fromPath, String targetPath);

    /**
     * Registers the default (fallback) route used when no other route matches.
     *
     * @param runnable the default action
     */
    void route(Runnable runnable);

}
