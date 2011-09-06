package com.ghosthack.turismo;

import com.ghosthack.turismo.servlet.Env;

public interface Resolver {

    IAction resolve(Env env);

    void route(String method, String path, IAction dispatch);

    void notFoundRoute(IAction dispatch);

}