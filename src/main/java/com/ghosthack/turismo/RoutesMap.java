package com.ghosthack.turismo;

import java.util.HashMap;

import com.ghosthack.turismo.servlet.Alias;
import com.ghosthack.turismo.servlet.Resolver;
import com.ghosthack.turismo.servlet.Route;
import com.ghosthack.turismo.servlet.Routes;

public abstract class RoutesMap implements Routes {

    @Override
    public Resolver getResolver() {
        return resolver;
    }

    public RoutesMap() {
        pathMap = new HashMap<String, Route>();
        methodPathMap = new HashMap<String, HashMap<String, Route>>();
        resolver = new MethodPathResolver() {
            @Override
            public Route resolve(String method, String path) {
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
    }

    protected abstract void map();

    protected void route(final String path, Action dispatch) {
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

    protected void route(final String method, final String path, Action dispatch) {
        r(method, path, new ActionRoute(dispatch));
    }

    protected void route(final String method, final String path, String target) {
        r(method, path, new ActionRoute(new Alias(target)));
    }

    protected void post(final String path, Action dispatch) {
        route(POST, path, dispatch);
    }

    protected void get(final String path, Action dispatch) {
        route(GET, path, dispatch);
    }

    protected void get(final String path, String target) {
        route(GET, path, target);
    }

    private static final String POST = "POST";
    private static final String GET = "GET";

    private final Resolver resolver;

    /** 
     * { method => { path => action-route } }
     */
    private final HashMap<String, HashMap<String, Route>> methodPathMap;
    /** 
     * { path => action-route }
     */
    private final HashMap<String, Route> pathMap;

}
