package io.github.ghosthack.turismo.example;

import io.github.ghosthack.turismo.action.Action;
import io.github.ghosthack.turismo.routes.RoutesMap;

public class AppRoutes extends RoutesMap {

    @Override
    protected void map() {
        get("/", new Action() {
            @Override
            public void run() {
                print("Hello World!");
            }
        });
    }

    public static void main(String[] args) throws Exception{
        JettyHelper.server(8080, "/app/*", AppRoutes.class.getName());
    }

}