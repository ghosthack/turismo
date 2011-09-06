package com.ghosthack.turismo.action.behavior;

import java.io.IOException;

import com.ghosthack.turismo.action.ActionException;
import com.ghosthack.turismo.action.Behavior;
import com.ghosthack.turismo.servlet.Env;

public final class StringWriterBehavior implements Behavior {

    public void behave(Env env, Object result) {
        try {
            env.res.setStatus(200);
            env.res.getWriter().write(String.valueOf(result));
            env.res.flushBuffer();
        } catch (IOException e) {
            throw new ActionException(e);
        }
    }

}