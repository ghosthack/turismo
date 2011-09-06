package com.ghosthack.turismo.servlet;

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

    private HttpServletRequest req;
    private HttpServletResponse res;
    private ServletContext ctx;

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
}