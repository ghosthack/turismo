package com.ghosthack.turismo.servlet;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

public class NotFoundAction implements Executable {
    @Override
    public void execute(Env env) throws ActionException {
        try {
            env.res.sendError(HttpServletResponse.SC_NOT_FOUND);
        } catch (IOException e) {
            throw new ActionException(e);
        }
    }
}
