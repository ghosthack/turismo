package io.github.ghosthack.turismo.example;


import io.github.ghosthack.turismo.action.Action;
import io.github.ghosthack.turismo.routes.RoutesMap;

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