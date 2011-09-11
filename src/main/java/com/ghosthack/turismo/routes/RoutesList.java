package com.ghosthack.turismo.routes;

import com.ghosthack.turismo.Resolver;
import com.ghosthack.turismo.Routes;
import com.ghosthack.turismo.action.NotFoundAction;
import com.ghosthack.turismo.resolver.ListResolver;

public abstract class RoutesList implements Routes {
    
    private final Resolver resolver;
    
    public RoutesList() {
        resolver = new ListResolver();
        resolver.route(new NotFoundAction());
        map();
    }

    @Override
    public Resolver getResolver() {
        return resolver;
    }
    
    protected abstract void map();

    // Shortcuts methods

    protected void get(final String path, Runnable runnable) {
        resolver.route(GET, path, runnable);
    }

    protected void post(final String path, Runnable runnable) {
        resolver.route(POST, path, runnable);
    }

    protected void put(final String path, Runnable runnable) {
        resolver.route(PUT, path, runnable);
    }

    protected void head(final String path, Runnable runnable) {
        resolver.route(HEAD, path, runnable);
    }

    protected void options(final String path, Runnable runnable) {
        resolver.route(OPTIONS, path, runnable);
    }

    protected void delete(final String path, Runnable runnable) {
        resolver.route(DELETE, path, runnable);
    }

    protected void trace(final String path, Runnable runnable) {
        resolver.route(TRACE, path, runnable);
    }

    protected void route(Runnable runnable) {
        resolver.route(runnable);
    }

    String POST = "POST";
    String GET = "GET";
    String HEAD = "HEAD";
    String OPTIONS = "OPTIONS";
    String PUT = "PUT";
    String DELETE = "DELETE";
    String TRACE = "TRACE";

}
