package com.ghosthack.turismo.example;

import com.ghosthack.turismo.Action;
import com.ghosthack.turismo.RoutesMap;
import com.ghosthack.turismo.servlet.Env;

public class AppRoutes extends RoutesMap {

    @Override
    protected void map() {
        get("/", new Action() {
            @Override
            public Object perform(Env env) {
                return "Hello World!";
            }
        });
    }

    public static void main(String[] args) throws Exception{
        JettyHelper.server(8080, "/*", AppRoutes.class.getName());
    }

}