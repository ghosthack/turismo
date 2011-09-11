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