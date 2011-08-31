package com.ghosthack.turismo.servlet;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;


public class Alias implements Executable {

    @Override
    public void execute(Env env) throws ActionException {
        final String target = getTarget();
        forward(env, target);
    }

    private void forward(Env env, final String target) {
        final RequestDispatcher dispatcher = env.ctx
                .getRequestDispatcher(target);
        try {
            dispatcher.forward(env.req, env.res);
        } catch (ServletException e) {
            throw new ActionException(e);
        } catch (IOException e) {
            throw new ActionException(e);
        }
    }

    public Alias(String target) {
        this.target = target;
    }

    public String getTarget() {
        return target;
    }

    private String target;

}
