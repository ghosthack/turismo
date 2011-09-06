package com.ghosthack.turismo.example;

import com.ghosthack.turismo.action.Action;
import com.ghosthack.turismo.routes.RoutesMap;
import com.ghosthack.turismo.servlet.Env;

public class TestAppRoutes extends RoutesMap {

    @Override
    protected void map() {
        get("/", new Action() {
            @Override
            public void doPerform(Env env) {
                print(env, "Hello World!");
            }
        });
        get("", new Action() {
            @Override
            public void doPerform(Env env) {
                print(env, "Hello World!");
            }
        });
        get("/redir1", new Action() {
            @Override
            public void doPerform(Env env) {
                //301 moved permanently
                movedPermanently(env, "/dest");
            }
        });
        get("/redir2", new Action() {
            @Override
            public void doPerform(Env env) {
                redirect(env, "/dest");
            }
        });
        get("/dest", new Action() {
            @Override
            public void doPerform(Env env) {
                print(env, "Hello Redirect");
            }
        });
        post("/search", new Action() {
            public void doPerform(Env env) {
                String query = env.req.getParameter("q");
                print(env, "Your search query was: " + query);
            }
        });
        notFound(new Action() {
            @Override
            public void doPerform(Env env) {
                notFound(env);
            }
        });
    }

    public static void main(String[] args) throws Exception{
        JettyHelper.server(8080, "/*", TestAppRoutes.class.getName());
    }

}