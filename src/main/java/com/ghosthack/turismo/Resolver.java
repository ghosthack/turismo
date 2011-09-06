package com.ghosthack.turismo;


public interface Resolver {

    Runnable resolve();

    void route(String method, String path, Runnable runnable);

    void route(Runnable runnable);

}