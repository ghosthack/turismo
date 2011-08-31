package com.ghosthack.turismo;

import java.io.IOException;

import com.ghosthack.turismo.servlet.ActionException;
import com.ghosthack.turismo.servlet.Env;

final class StringWriterBehavior implements Action.Behavior {

    public void behavior(Env env, Object result) {
        try {
            env.res.getWriter().write(String.valueOf(result));
        } catch (IOException e) {
            throw new ActionException(e);
        }
    }

}