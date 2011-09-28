package com.ghosthack.turismo.example;

import com.ghosthack.turismo.action.Action;
import com.ghosthack.turismo.routes.RoutesMap;
import com.ghosthack.turismo.servlet.Env;

public class ExampleWebAppRoutes extends RoutesMap {

    @Override
    protected void map() {
        get("/", new Action() {
            @Override
            public void run() {
                print("Hello World!");
            }
        });
        get("/redir1", new Action() {
            @Override
            public void run() {
                //301 moved permanently
                movedPermanently("/dest");
            }
        });
        get("/redir2", new Action() {
            @Override
            public void run() {
                //302 redirect
                redirect("/dest");
            }
        });
        get("/dest", new Action() {
            @Override
            public void run() {
                print("Hello Redirect");
            }
        });
        get("/render", new Action() {
            public void run() {
                Env.req().setAttribute("message", "Hello Word!");
                jsp("/jsp/render.jsp");
            }
        });
        post("/search", new Action() {
            public void run() {
                String query = Env.req().getParameter("q");
                print("Your search query was: " + query);
            }
        });
        route(new Action() {
            @Override
            public void run() {
                notFound();
            }
        });
    }

    public static void main(String[] args) throws Exception{
        JettyHelper.server(8080, JettyHelper.webapp());
    }

}