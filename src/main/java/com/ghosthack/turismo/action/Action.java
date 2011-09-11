package com.ghosthack.turismo.action;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ghosthack.turismo.action.behavior.Alias;
import com.ghosthack.turismo.action.behavior.MovedPermanently;
import com.ghosthack.turismo.action.behavior.MovedTemporarily;
import com.ghosthack.turismo.action.behavior.NotFound;
import com.ghosthack.turismo.action.behavior.Redirect;
import com.ghosthack.turismo.action.behavior.StringPrinter;
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
        new Alias().forward(target);
    }
    
    protected void jsp(String path) {
        forward(path);
    }

    protected void movedPermanently(String newLocation) {
        new MovedPermanently().send301(newLocation);
    }

    protected void movedTemporarily(String newLocation) {
        new MovedTemporarily().send302(newLocation);
    }

    protected void notFound() {
        new NotFound().send404();
    }

    protected void redirect(String newLocation) {
        new Redirect().redirect(newLocation);
    }

    protected void print(String string) {
        new StringPrinter().print(string);
    }

}
