package com.ghosthack.turismo.routes;

import com.ghosthack.turismo.IAction;
import com.ghosthack.turismo.Resolver;
import com.ghosthack.turismo.Routes;
import com.ghosthack.turismo.resolver.MapResolver;
import com.ghosthack.turismo.servlet.Env;

public abstract class RoutesMap implements Routes {
    
    private final Resolver resolver;
    
    public RoutesMap() {
        resolver = new MapResolver();
        map();
    }

    @Override
    public Resolver getResolver() {
        return resolver;
    }
    
    public void execute(Env env) {
        this.getResolver().resolve(env).perform(env);
    }

    protected abstract void map();

    // Shortcuts methods

    protected void get(final String path, IAction dispatch) {
        resolver.route(GET, path, dispatch);
    }

    protected void post(final String path, IAction dispatch) {
        resolver.route(POST, path, dispatch);
    }

    protected void put(final String path, IAction dispatch) {
        resolver.route(PUT, path, dispatch);
    }

    protected void head(final String path, IAction dispatch) {
        resolver.route(HEAD, path, dispatch);
    }

    protected void options(final String path, IAction dispatch) {
        resolver.route(OPTIONS, path, dispatch);
    }

    protected void delete(final String path, IAction dispatch) {
        resolver.route(DELETE, path, dispatch);
    }

    protected void trace(final String path, IAction dispatch) {
        resolver.route(TRACE, path, dispatch);
    }

    protected void notFound(IAction dispatch) {
        resolver.notFoundRoute(dispatch);
    }

    private static final String POST = "POST";
    private static final String GET = "GET";
    private static final String HEAD = "HEAD";
    private static final String OPTIONS = "OPTIONS";
    private static final String PUT = "PUT";
    private static final String DELETE = "DELETE";
    private static final String TRACE = "TRACE";

}
