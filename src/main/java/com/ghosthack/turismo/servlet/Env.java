package com.ghosthack.turismo.servlet;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Env {

    public HttpServletRequest req;
    public HttpServletResponse res;
    public ServletContext ctx;

    public Env(HttpServletRequest req, HttpServletResponse res,
            ServletContext context) {
        this.req = req;
        this.res = res;
        this.ctx = context;
    }

}