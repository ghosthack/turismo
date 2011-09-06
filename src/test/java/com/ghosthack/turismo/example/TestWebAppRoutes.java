package com.ghosthack.turismo.example;

import com.ghosthack.turismo.action.Action;
import com.ghosthack.turismo.routes.RoutesMap;
import com.ghosthack.turismo.servlet.Env;

public class TestWebAppRoutes extends RoutesMap {

    @Override
    protected void map() {
        get("/", new Action() {
            @Override
            protected void doPerform(Env env) {
                print(env, "Hello World!");
            }
        });
        get("/redir1", new Action() {
            @Override
            protected void doPerform(Env env) {
                //301 moved permanently
                movedPermanently(env, "/dest");
            }
        });
        get("/redir2", new Action() {
            @Override
            public void doPerform(Env env) {
                //302 redirect
                redirect(env, "/dest");
            }
        });
        get("/dest", new Action() {
            @Override
            public void doPerform(Env env) {
                print(env,"Hello Redirect");
            }
        });
        get("/render", new Action() {
            public void doPerform(Env env) {
                env.req.setAttribute("message", "Hello Word!");
                jsp(env, "/jsp/render.jsp");
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
        JettyHelper.server(8080, JettyHelper.webapp());
    }

}