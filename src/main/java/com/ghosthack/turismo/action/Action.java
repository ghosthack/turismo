package com.ghosthack.turismo.action;

import com.ghosthack.turismo.servlet.Env;

public abstract class Action implements Runnable {

    protected void alias(Env env, String target) {
        Behaviors.forward().on(target);
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
