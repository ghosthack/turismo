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

public class Env {
    
    private static ThreadLocal<Env> locals = new ThreadLocal<Env>(); 
    
    public static Env get() {
        return locals.get();
    }
    
    public static void create(HttpServletRequest req, HttpServletResponse res,
            ServletContext context) {
        locals.set(new Env(req, res, context));
    }
    
    public static void destroy() {
        locals.remove();
    }
    
    public static HttpServletRequest req() {
        return get().getReq();
    }

    public static HttpServletResponse res() {
        return get().getRes();
    }
    
    public static ServletContext ctx() {
        return get().getCtx();
    }

    public static void setResourceParams(Map<String, String> params) {
        get().setParams(params);
    }

    public static String params(String key) {
        Map<String, String> params2 = get().getParams();
        String string = params2.get(key);
        if(string == null) {
            return Env.req().getParameter(key);
        }
        return string;
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

    public ServletContext getCtx() {
        return ctx;
    }

    public HttpServletRequest getReq() {
        return req;
    }

    public HttpServletResponse getRes() {
        return res;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    public Map<String, String> getParams() {
        return params;
    }

}
