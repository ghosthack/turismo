package com.ghosthack.turismo.action;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ghosthack.turismo.servlet.Env;


public abstract class Action implements Runnable {

    protected HttpServletRequest req() {
        return Env.req();
    }

    protected HttpServletResponse res() {
        return Env.res();
    }

    protected ServletContext ctx() {
        return Env.ctx();
    }

    protected void alias(String target) {
        forward(target);
    }

    protected void forward(String target) {
        Behaviors.forward().on(target);
    }
    
    protected void jsp(String path) {
        Behaviors.jsp().on(path);
    }

    protected void movedPermanently(String newLocation) {
        Behaviors.movedPermanently().on(newLocation);
    }

    protected void notFound() {
        Behaviors.notFound().on(null);
    }

    protected void redirect(String newLocation) {
        Behaviors.redirect().on(newLocation);
    }

    protected void print(String string) {
        Behaviors.string().on(string);
    }

}
