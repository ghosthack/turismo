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

package com.ghosthack.turismo.servlet;

import java.util.Collections;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Thread-local request context. Stores the current request, response,
 * and servlet context for the duration of a single servlet invocation.
 */
public class Env {
    
    private static ThreadLocal<Env> locals = new ThreadLocal<Env>(); 
    
    /**
     * Returns the current thread's Env, or {@code null} if none has been created.
     *
     * @return the Env instance, or {@code null}
     */
    public static Env get() {
        return locals.get();
    }
    
    /**
     * Creates and stores a new Env for the current thread.
     *
     * @param req     the HTTP request
     * @param res     the HTTP response
     * @param context the servlet context
     */
    public static void create(HttpServletRequest req, HttpServletResponse res,
            ServletContext context) {
        locals.set(new Env(req, res, context));
    }
    
    /**
     * Removes the Env from the current thread.
     */
    public static void destroy() {
        locals.remove();
    }
    
    /**
     * Returns the current request.
     *
     * @return the HTTP request
     * @throws IllegalStateException if no Env exists on the current thread
     */
    public static HttpServletRequest req() {
        return current().getReq();
    }

    /**
     * Returns the current response.
     *
     * @return the HTTP response
     * @throws IllegalStateException if no Env exists on the current thread
     */
    public static HttpServletResponse res() {
        return current().getRes();
    }
    
    /**
     * Returns the current servlet context.
     *
     * @return the servlet context
     * @throws IllegalStateException if no Env exists on the current thread
     */
    public static ServletContext ctx() {
        return current().getCtx();
    }

    /**
     * Sets URL path parameters extracted by the resolver.
     *
     * @param params the path parameter map
     * @throws IllegalStateException if no Env exists on the current thread
     */
    public static void setResourceParams(Map<String, String> params) {
        current().setParams(params);
    }

    /**
     * Returns a parameter value by key, checking path parameters first
     * and falling back to request parameters.
     *
     * @param key the parameter name
     * @return the value, or {@code null} if not found
     * @throws IllegalStateException if no Env exists on the current thread
     */
    public static String params(String key) {
        Map<String, String> params2 = current().getParams();
        String string = params2.get(key);
        if(string == null) {
            return Env.req().getParameter(key);
        }
        return string;
    }

    /**
     * Returns the current thread's Env, throwing a descriptive exception if none exists.
     *
     * @return the current Env, never null
     * @throws IllegalStateException if no Env has been created for the current thread
     */
    private static Env current() {
        Env env = locals.get();
        if (env == null) {
            throw new IllegalStateException(
                    "No Env available on current thread. "
                    + "Env.create() must be called before accessing request context. "
                    + "This typically means the code is running outside of a servlet request.");
        }
        return env;
    }

    private HttpServletRequest req;
    private HttpServletResponse res;
    private ServletContext ctx;
    private Map<String, String> params = Collections.emptyMap(); 

    private Env(HttpServletRequest req, HttpServletResponse res,
            ServletContext context) {
        this.req = req;
        this.res = res;
        this.ctx = context;
    }

    /**
     * Returns the servlet context.
     *
     * @return the servlet context
     */
    public ServletContext getCtx() {
        return ctx;
    }

    /**
     * Returns the HTTP request.
     *
     * @return the request
     */
    public HttpServletRequest getReq() {
        return req;
    }

    /**
     * Returns the HTTP response.
     *
     * @return the response
     */
    public HttpServletResponse getRes() {
        return res;
    }

    /**
     * Sets the path parameter map.
     *
     * @param params the parameters
     */
    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    /**
     * Returns the path parameter map.
     *
     * @return the parameters
     */
    public Map<String, String> getParams() {
        return params;
    }

}
