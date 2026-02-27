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

package io.github.ghosthack.turismo.action;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.github.ghosthack.turismo.action.behavior.Alias;
import io.github.ghosthack.turismo.action.behavior.MovedPermanently;
import io.github.ghosthack.turismo.action.behavior.MovedTemporarily;
import io.github.ghosthack.turismo.action.behavior.NotFound;
import io.github.ghosthack.turismo.action.behavior.Redirect;
import io.github.ghosthack.turismo.action.behavior.StringPrinter;
import io.github.ghosthack.turismo.servlet.Env;

/**
 * Base class for route actions. Provides convenience accessors to the
 * current request environment and common HTTP behaviors.
 */
public abstract class Action implements Runnable {

    /** Default constructor. */
    protected Action() {
    }

    /**
     * Returns a URL path parameter or request parameter by key.
     *
     * @param key the parameter name
     * @return the parameter value, or {@code null} if not present
     */
    protected String params(String key) {
        return Env.params(key);
    }

    /**
     * Returns the current HTTP request.
     *
     * @return the request
     */
    protected HttpServletRequest req() {
        return Env.req();
    }

    /**
     * Returns the current HTTP response.
     *
     * @return the response
     */
    protected HttpServletResponse res() {
        return Env.res();
    }

    /**
     * Returns the servlet context.
     *
     * @return the servlet context
     */
    protected ServletContext ctx() {
        return Env.ctx();
    }

    /**
     * Forwards the request to the given target path via {@link javax.servlet.RequestDispatcher}.
     *
     * @param target the target path
     */
    protected void alias(String target) {
        forward(target);
    }

    /**
     * Forwards the request to the given target path via {@link javax.servlet.RequestDispatcher}.
     *
     * @param target the target path
     */
    protected void forward(String target) {
        new Alias().forward(target);
    }

    /**
     * Forwards the request to a JSP page.
     *
     * @param path the JSP path
     */
    protected void jsp(String path) {
        forward(path);
    }

    /**
     * Sends an HTTP 301 (Moved Permanently) redirect.
     *
     * @param newLocation the redirect URL
     */
    protected void movedPermanently(String newLocation) {
        new MovedPermanently().send301(newLocation);
    }

    /**
     * Sends an HTTP 302 (Moved Temporarily) redirect.
     *
     * @param newLocation the redirect URL
     */
    protected void movedTemporarily(String newLocation) {
        new MovedTemporarily().send302(newLocation);
    }

    /**
     * Sends an HTTP 404 (Not Found) response.
     */
    protected void notFound() {
        new NotFound().send404();
    }

    /**
     * Sends a redirect response via {@link HttpServletResponse#sendRedirect}.
     *
     * @param newLocation the redirect URL
     */
    protected void redirect(String newLocation) {
        new Redirect().redirect(newLocation);
    }

    /**
     * Writes a string to the response output.
     *
     * @param string the string to write
     */
    protected void print(String string) {
        new StringPrinter().print(string);
    }

}
