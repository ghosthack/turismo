package com.ghosthack.turismo.resolver;

import com.ghosthack.turismo.Resolver;
import com.ghosthack.turismo.action.ActionException;
import com.ghosthack.turismo.servlet.Env;

public abstract class MethodPathResolver implements Resolver {

    private static final String UNDEFINED_PATH = "Undefined path";

    @Override
    public Runnable resolve() throws ActionException {
        String path = extractPath();
        String method = Env.req().getMethod();
        Runnable route = resolve(method, path);
        return route;
    }

    protected abstract Runnable resolve(String method, String path);

    private String extractPath() throws ActionException {
        String path = Env.req().getPathInfo();
        if (path == null) {
            path = Env.req().getServletPath();
            if (path == null) {
                throw new ActionException(UNDEFINED_PATH);
            }
        }
        return path;
    }

}
