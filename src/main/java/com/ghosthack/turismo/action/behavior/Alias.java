package com.ghosthack.turismo.action.behavior;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;

import com.ghosthack.turismo.action.ActionException;
import com.ghosthack.turismo.servlet.Env;


public class Alias {

    public void forward(final String target) {
        final RequestDispatcher dispatcher = Env.ctx()
                .getRequestDispatcher(target);
        try {
            dispatcher.forward(Env.req(), Env.res());
        } catch (ServletException e) {
            throw new ActionException(e);
        } catch (IOException e) {
            throw new ActionException(e);
        }
    }

}
