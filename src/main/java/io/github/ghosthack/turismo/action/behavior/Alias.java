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

package io.github.ghosthack.turismo.action.behavior;

import java.io.IOException;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;

import io.github.ghosthack.turismo.action.ActionException;
import io.github.ghosthack.turismo.servlet.Env;

/**
 * Forwards the current request to another resource via {@link RequestDispatcher}.
 */
public class Alias {

    /** Creates a new Alias instance. */
    public Alias() {
    }

    /**
     * Forwards the request to the given target path.
     * <p>
     * The target is a path inside the web application, starting with
     * {@code /} and relative to the context root. It can name any
     * resource, including ones under {@code /WEB-INF}, so don't build it
     * from request input: a user-controlled target can expose resources
     * that are not meant to be served directly.
     *
     * @param target the context-relative path to forward to
     * @throws ActionException if the target is not a path starting with
     *         {@code /}, the context or dispatcher is unavailable, or
     *         forwarding fails
     */
    public void forward(final String target) {
        if (target == null || !target.startsWith("/")) {
            throw new ActionException(
                    "Forward target must start with '/': " + target);
        }
        final ServletContext ctx = Env.ctx();
        if (ctx == null) {
            throw new ActionException("ServletContext is not available");
        }
        final RequestDispatcher dispatcher = ctx.getRequestDispatcher(target);
        if (dispatcher == null) {
            throw new ActionException("No resource found for: " + target);
        }
        try {
            dispatcher.forward(Env.req(), Env.res());
        } catch (ServletException e) {
            throw new ActionException(e);
        } catch (IOException e) {
            throw new ActionException(e);
        }
    }

}
