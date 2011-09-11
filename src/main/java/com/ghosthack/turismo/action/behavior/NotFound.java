package com.ghosthack.turismo.action.behavior;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import com.ghosthack.turismo.action.ActionException;
import com.ghosthack.turismo.servlet.Env;

public class NotFound {

    public void send404() {
        try {
            Env.res().sendError(HttpServletResponse.SC_NOT_FOUND);
        } catch (IOException e) {
            throw new ActionException(e);
        }        
    }
}
