package com.ghosthack.turismo.action.behavior;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;

import com.ghosthack.turismo.action.ActionException;
import com.ghosthack.turismo.action.Behavior;
import com.ghosthack.turismo.servlet.Env;


public class AliasBehavior implements Behavior {

    @Override
    public void behave(Env env, Object result) {
        forward(env, String.valueOf(result));
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

}
