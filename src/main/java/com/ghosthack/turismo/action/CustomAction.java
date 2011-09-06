package com.ghosthack.turismo.action;

import com.ghosthack.turismo.servlet.Env;

public abstract class CustomAction extends Action {

    private final Behavior behavior;

    public CustomAction(Behavior behavior) {
        this.behavior = behavior;
    }

    @Override
    public void perform(Env env) {
        Object result = customPerform(env);
        this.behavior.behave(env, result);
    }

    protected abstract Object customPerform(Env env);
    
    @Override
    protected final void doPerform(Env env) {
        throw new UnsupportedOperationException("Do not use this method. Use #customPerform instead");
    }

}
