package com.ghosthack.turismo.example;


import com.ghosthack.turismo.Action;
import com.ghosthack.turismo.RoutesMap;
import com.ghosthack.turismo.servlet.Env;

public class WebAppRoutes extends RoutesMap {

    @Override
    protected void map() {
        get("/", new Action() {
            @Override
            public Object perform(Env env) {
                return "Hello World!";
            }
        });
    }

    /**
     * Minics an application server environment
     */
    public static void main(String[] args) throws Exception{
        JettyHelper.server(8080, JettyHelper.webapp());
    }

}