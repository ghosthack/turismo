package com.ghosthack.turismo.example;


import com.ghosthack.turismo.action.Action;
import com.ghosthack.turismo.routes.RoutesMap;

public class WebAppRoutes extends RoutesMap {

    @Override
    protected void map() {
        get("/", new Action() {
            public void run() {
                print("Hello World!");
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