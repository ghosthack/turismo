package com.ghosthack.turismo.example;

import com.ghosthack.turismo.action.Action;
import com.ghosthack.turismo.routes.RoutesMap;
import com.ghosthack.turismo.servlet.Env;

public class AppRoutes extends RoutesMap {

    @Override
    protected void map() {
        get("/", new Action() {
            @Override
            public void doPerform(Env env) {
                print(env, "Hello World!");
            }
        });
    }

    public static void main(String[] args) throws Exception{
        JettyHelper.server(8080, "/app/*", AppRoutes.class.getName());
    }

}