package com.ghosthack.turismo.action.behavior;

import java.io.IOException;

import com.ghosthack.turismo.action.ActionException;
import com.ghosthack.turismo.servlet.Env;

public class Redirect {

    public void redirect(String location) {
        try {
            Env.res().sendRedirect(String.valueOf(location));
        } catch (IOException e) {
            throw new ActionException(e);
        }
    }

}
