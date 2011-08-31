package com.ghosthack.turismo.example;

import org.eclipse.jetty.webapp.WebAppContext;

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
     * Minics an application server
     */
    public static void main(String[] args) throws Exception{
        JettyHelper.server(8080, webapp());
    }

    public static WebAppContext webapp() {
        WebAppContext root = new WebAppContext();
        root.setContextPath("/");
        root.setDescriptor("src/test/webapp/WEB-INF/web.xml");
        root.setResourceBase("src/test/webapp/");
        root.setParentLoaderPriority(true);
        return root;
    }

}