package com.ghosthack.turismo.resolver;

import com.ghosthack.turismo.Resolver;
import com.ghosthack.turismo.IAction;
import com.ghosthack.turismo.action.ActionException;
import com.ghosthack.turismo.servlet.Env;

public abstract class MethodPathResolver implements Resolver {

    private static final String UNDEFINED_PATH = "Undefined path";

    @Override
    public IAction resolve(Env env) throws ActionException {
        String path = extractPath(env);
        String method = env.req.getMethod();
        IAction route = resolve(method, path);
        return route;
    }

    protected abstract IAction resolve(String method, String path);

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

}
