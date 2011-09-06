package com.ghosthack.turismo.resolver;

import java.util.HashMap;
import java.util.Map;

import com.ghosthack.turismo.IAction;

public class MapResolver extends MethodPathResolver {

    /** 
     * { method => { path => action-route } }
     */
    private final Map<String, Map<String, IAction>> methodPathMap = new HashMap<String, Map<String, IAction>>();

    private IAction notFoundRoute;

    @Override
    public IAction resolve(String method, String path) {
        IAction route = findRoute(method, path);
        if(route != null) {
            return route;
        }
        return getNotFoundRoute();
    }

    public IAction getNotFoundRoute() {
        return notFoundRoute;
    }

    public void route(final String path, IAction dispatch) {
        route(null, path, dispatch);
    }

    public void route(final String method, final String path, IAction dispatch) {
        r(method, path, dispatch);
    }

    private IAction findRoute(String method, String path) {
        Map<String, IAction> methodMap = methodPathMap.get(method);
        
        if (methodMap == null) {
            methodMap = methodPathMap.get(null);
        }

        return methodMap != null? methodMap.get(path) : null;
    }

    private void r(final String method, final String path, IAction action) {
        Map<String, IAction> methodMap = methodPathMap.get(method);
        if(methodMap == null) {
            methodMap = new HashMap<String, IAction>();
            methodPathMap.put(method, methodMap);
        }
        methodMap.put(path, action);
    }

    @Override
    public void notFoundRoute(IAction dispatch) {
        notFoundRoute = dispatch;
    }

}
