package com.ghosthack.turismo.action.behavior;

import javax.servlet.http.HttpServletResponse;

import com.ghosthack.turismo.servlet.Env;

public class MovedPermanently {

    public void send301(Object location) {
        Env.res().setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
        Env.res().setHeader("Location", String.valueOf(location));
    }

}
