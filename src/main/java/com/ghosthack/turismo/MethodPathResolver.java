package com.ghosthack.turismo;

import com.ghosthack.turismo.servlet.ActionException;
import com.ghosthack.turismo.servlet.Env;
import com.ghosthack.turismo.servlet.Resolver;
import com.ghosthack.turismo.servlet.Route;

public abstract class MethodPathResolver implements Resolver {

    public abstract Route resolve(String method, String path);

    @Override
    public Route resolve(Env env) throws ActionException {
        final String path = extractPath(env);
        final String method = env.req.getMethod();
        Route route = resolvePath(method, path);
        return route;
    }

    private Route resolvePath(String method, String path) throws ActionException {
        Route route = resolve(method, path);
        if (route == null) {
            throw new ActionException(UNDEFINED_ACTION + path);
        }
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

    private static final String UNDEFINED_ACTION = "Undefined action: ";
    private static final String UNDEFINED_PATH = "Undefined path";

}
