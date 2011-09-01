package com.ghosthack.turismo.example;

import java.io.IOException;

import com.ghosthack.turismo.Action;
import com.ghosthack.turismo.RoutesMap;
import com.ghosthack.turismo.servlet.Env;

public class TestAppRoutes extends RoutesMap {

    @Override
    protected void map() {
        get("/", new Action() {
            @Override
            public Object perform(Env env) {
                return "Hello World!";
            }
        });
        get("/redir", new Action() {
            @Override
            public Object perform(Env env) {
                env.res.setStatus(302);
                env.res.setHeader("Location", "/dest");
                return "Redirect";
            }
        });
        get("/redir2", new Action() {
            @Override
            public Object perform(Env env) {
                try {
                    env.res.sendRedirect("/dest");
                } catch (IOException e) {
                }
                return "Redirect";
            }
        });
        get("/dest", new Action() {
            @Override
            public Object perform(Env env) {
                return "Hello Redirect";
            }
        });
        notFound(new Action() {
            @Override
            public Object perform(Env env) {
                return "Not found";
            }
        });
    }

    public static void main(String[] args) throws Exception{
        JettyHelper.server(8080, "/*", TestAppRoutes.class.getName());
    }

}