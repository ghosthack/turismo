package com.ghosthack.turismo;

import java.io.IOException;
import java.util.HashMap;

import javax.servlet.http.HttpServletResponse;

import com.ghosthack.turismo.servlet.ActionException;
import com.ghosthack.turismo.servlet.Alias;
import com.ghosthack.turismo.servlet.Env;
import com.ghosthack.turismo.servlet.Executable;
import com.ghosthack.turismo.servlet.Resolver;
import com.ghosthack.turismo.servlet.Route;
import com.ghosthack.turismo.servlet.Routes;

public abstract class RoutesMap implements Routes {

    @Override
    public Resolver getResolver() {
        return resolver;
    }

    public Route getNotFoundRoute() {
        return notFoundRoute;
    }

    public RoutesMap() {
        pathMap = new HashMap<String, Route>();
        methodPathMap = new HashMap<String, HashMap<String, Route>>();
        resolver = new MethodPathResolver() {
            @Override
            public Route resolve(String method, String path) {
                Route route = findRoute(method, path);
                if(route != null) {
                    return route;
                }
                return getNotFoundRoute();
            }

            private Route findRoute(String method, String path) {
                HashMap<String, Route> methodMap = methodPathMap.get(method);
                if(methodMap != null) {
                    Route route = methodMap.get(path);
                    if(route != null) {
                        return route;
                    }
                }
                return pathMap.get(path);
            }
        };
        map();
        notFound(new Executable() {
            @Override
            public void execute(Env env) throws ActionException {
                try {
                    env.res.sendError(HttpServletResponse.SC_NOT_FOUND);
                } catch (IOException e) {
                    throw new ActionException(e);
                }
            }
        });
    }

    protected abstract void map();

    protected void route(final String path, Executable dispatch) {
        pathMap.put(path, new ActionRoute(dispatch));
    }

    protected void route(final String path, String target) {
        pathMap.put(path, new ActionRoute(new Alias(target)));
    }

    private void r(final String method, final String path, ActionRoute actionRoute) {
        HashMap<String, Route> methodMap = methodPathMap.get(method);
        if(methodMap == null) {
            methodMap = new HashMap<String, Route>();
            methodPathMap.put(method, methodMap);
        }
        methodMap.put(path, actionRoute);
    }

    protected void route(final String method, final String path, Executable dispatch) {
        r(method, path, new ActionRoute(dispatch));
    }

    protected void route(final String method, final String path, String target) {
        r(method, path, new ActionRoute(new Alias(target)));
    }

    protected void get(final String path, String target) {
        route(GET, path, target);
    }

    protected void get(final String path, Executable dispatch) {
        route(GET, path, dispatch);
    }

    protected void post(final String path, Executable dispatch) {
        route(POST, path, dispatch);
    }

    protected void put(final String path, Executable dispatch) {
        route(PUT, path, dispatch);
    }

    protected void head(final String path, Executable dispatch) {
        route(HEAD, path, dispatch);
    }

    protected void options(final String path, Executable dispatch) {
        route(OPTIONS, path, dispatch);
    }

    protected void delete(final String path, Executable dispatch) {
        route(DELETE, path, dispatch);
    }

    protected void trace(final String path, Executable dispatch) {
        route(TRACE, path, dispatch);
    }

    protected void notFound(Executable dispatch) {
        notFoundRoute = new ActionRoute(dispatch);
    }

    private static final String POST = "POST";
    private static final String GET = "GET";
    private static final String HEAD = "HEAD";
    private static final String OPTIONS = "OPTIONS";
    private static final String PUT = "PUT";
    private static final String DELETE = "DELETE";
    private static final String TRACE = "TRACE";

    private final Resolver resolver;

    /** 
     * { method => { path => action-route } }
     */
    private final HashMap<String, HashMap<String, Route>> methodPathMap;
    /** 
     * { path => action-route }
     */
    private final HashMap<String, Route> pathMap;
    private Route notFoundRoute;

}
