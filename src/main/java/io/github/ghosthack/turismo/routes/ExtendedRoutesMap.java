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

package io.github.ghosthack.turismo.routes;

import io.github.ghosthack.turismo.action.Action;

/**
 * A {@link RoutesMap} extension that supports alias-style GET routes
 * where a path is forwarded to another resource via {@link Action#alias}.
 */
public abstract class ExtendedRoutesMap extends RoutesMap {

    /** Default constructor. */
    protected ExtendedRoutesMap() {
    }

    /**
     * Registers a GET route that forwards to the given target path.
     *
     * @param path   the URL path pattern
     * @param target the path to forward to
     */
    protected void get(String path, final String target) {
        resolver.route(GET, path, new Action() {
            @Override public void run() {
                alias(target);
            }
        });
    }

}
