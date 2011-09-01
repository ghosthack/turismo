package com.ghosthack.turismo;

import com.ghosthack.turismo.servlet.ActionException;
import com.ghosthack.turismo.servlet.Env;
import com.ghosthack.turismo.servlet.Resolver;
import com.ghosthack.turismo.servlet.Route;

public abstract class MethodPathResolver implements Resolver {

    public abstract Route resolve(String method, String path);

    @Override
    public Route resolve(Env env) throws ActionException {
        String path = extractPath(env);
        String method = env.req.getMethod();
        Route route = resolve(method, path);
        return route;
    }

    private String extractPath(Env env) throws ActionException {
        String path = env.req.getPathInfo();
        if (path == null) {
            path = env.req.getServletPath();
            if (path == null) {
                throw new ActionException(UNDEFINED_PATH);
            }
        }
        return path;
    }

    private static final String UNDEFINED_PATH = "Undefined path";

}
