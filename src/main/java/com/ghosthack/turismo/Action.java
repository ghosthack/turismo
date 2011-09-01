package com.ghosthack.turismo;

import com.ghosthack.turismo.servlet.ActionException;
import com.ghosthack.turismo.servlet.Alias;
import com.ghosthack.turismo.servlet.Env;
import com.ghosthack.turismo.servlet.Executable;

public abstract class Action implements Executable {

    public abstract Object perform(Env env);

    @Override
    public void execute(Env env) throws ActionException {
        Object result = perform(env);
        behavior.behavior(env, result);
    }

    public Action(String target) {
        jsp(target);
    }
    
    public Object jsp(String target) {
        this.alias = new Alias(target);
        this.behavior = new Behavior() {
            public void behavior(Env env, Object result) {
                alias.execute(env);
            }
        };
        return null;
    }

    public Action() {
        this.behavior = new StringWriterBehavior();
    }

    public interface Behavior {
        void behavior(Env env, Object result);
    }

    private Alias alias;
    private Behavior behavior;

}
