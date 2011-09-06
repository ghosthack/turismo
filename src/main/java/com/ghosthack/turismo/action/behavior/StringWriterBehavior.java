package com.ghosthack.turismo.action.behavior;

import java.io.IOException;

import com.ghosthack.turismo.action.ActionException;
import com.ghosthack.turismo.action.Behavior;
import com.ghosthack.turismo.servlet.Env;

public final class StringWriterBehavior implements Behavior {

    public void on(Object result) {
        try {
            Env.res().getWriter().write(String.valueOf(result));
        } catch (IOException e) {
            throw new ActionException(e);
        }
    }

}