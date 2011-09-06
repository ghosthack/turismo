package com.ghosthack.turismo.action;

import com.ghosthack.turismo.IAction;
import com.ghosthack.turismo.servlet.Env;

public abstract class Action implements IAction {
    
    @Override
    public void perform(Env env) {
        doPerform(env);
    }
    
    protected void alias(Env env, String target) {
        Behaviors.forward().behave(env, target);
    }
    protected void forward(Env env, String target) {
        Behaviors.forward().behave(env, target);
    }
    protected void jsp(Env env, String path) {
        Behaviors.jsp().behave(env, path);
    }
    protected void movedPermanently(Env env, String newLocation) {
        Behaviors.movedPermanently().behave(env, newLocation);
    }
    protected void notFound(Env env) {
        Behaviors.notFound().behave(env, null);
    }
    protected void redirect(Env env, String newLocation) {
        Behaviors.redirect().behave(env, newLocation);
    }
    protected void print(Env env, String string) {
        Behaviors.string().behave(env, string);
    }
    
    /**
     * Fill this method with your controller implementation
     * 
     * @see #alias(Env, String)
     * @see #forward(Env, String)
     * @see #jsp(Env, String)
     * @see #notFound(Env)
     * @see #redirect(Env, String)
     * @see #print(Env, String)
     * 
     * @param env
     */
    protected abstract void doPerform(Env env);
    
}
