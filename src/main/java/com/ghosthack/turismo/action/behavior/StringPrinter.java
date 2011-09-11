package com.ghosthack.turismo.action.behavior;

import java.io.IOException;

import com.ghosthack.turismo.action.ActionException;
import com.ghosthack.turismo.servlet.Env;

public final class StringPrinter {

    public void print(String string) {
        try {
            Env.res().getWriter().write(string);
        } catch (IOException e) {
            throw new ActionException(e);
        }
    }

}