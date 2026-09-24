package io.github.ghosthack.turismo.servlet;

import io.github.ghosthack.turismo.action.Action;
import io.github.ghosthack.turismo.routes.RoutesMap;

/** Routes class loaded by name in {@link ServletTest}. */
public class TestRoutes extends RoutesMap {

    @Override
    protected void map() {
        get("/", new Action() {
            @Override
            public void run() {
                print("Hello World!");
            }
        });
    }

}
