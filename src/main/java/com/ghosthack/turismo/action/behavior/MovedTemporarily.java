package com.ghosthack.turismo.action.behavior;

import javax.servlet.http.HttpServletResponse;

import com.ghosthack.turismo.servlet.Env;

public class MovedTemporarily {

    public void send302(String location) {
        Env.res().setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
        Env.res().setHeader("Location", location);
    }

}
