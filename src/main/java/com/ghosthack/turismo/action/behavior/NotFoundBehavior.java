package com.ghosthack.turismo.action.behavior;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import com.ghosthack.turismo.action.ActionException;
import com.ghosthack.turismo.action.Behavior;
import com.ghosthack.turismo.servlet.Env;

public class NotFoundBehavior implements Behavior {

    @Override
    public void behave(Env env, Object result) {
        try {
            env.res.sendError(HttpServletResponse.SC_NOT_FOUND);
        } catch (IOException e) {
            throw new ActionException(e);
        }        
    }
}
