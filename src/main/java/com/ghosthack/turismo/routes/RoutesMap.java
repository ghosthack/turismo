package com.ghosthack.turismo.routes;

import com.ghosthack.turismo.Resolver;
import com.ghosthack.turismo.Routes;
import com.ghosthack.turismo.resolver.MapResolver;

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
    
    public void execute() {
        this.getResolver().resolve().run();
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

    private static final String POST = "POST";
    private static final String GET = "GET";
    private static final String HEAD = "HEAD";
    private static final String OPTIONS = "OPTIONS";
    private static final String PUT = "PUT";
    private static final String DELETE = "DELETE";
    private static final String TRACE = "TRACE";

}
